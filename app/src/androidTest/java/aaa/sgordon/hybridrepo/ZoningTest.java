package aaa.sgordon.hybridrepo;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.hybrid.database.HZoningDAO;
import aaa.sgordon.hybridrepo.hybrid.database.HybridHelpDatabase;
import aaa.sgordon.hybridrepo.hybrid.jobs.zoning.ZoningWorker;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;
import aaa.sgordon.hybridrepo.remote.types.RFile;


public class ZoningTest {
	private static final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

	private static final Path emptyFile = Paths.get(context.getDataDir().toString(), "temp", "empty.txt");
	private static final String emptyChecksum = RFile.defaultChecksum;

	private static final Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	private static final Path smallFile = Paths.get(context.getDataDir().toString(), "temp", "smallFile.txt");
	private static final String smallChecksum = "35C461DEE98AAD4739707C6CCA5D251A1617BFD928E154995CA6F4CE8156CFFC";

	private static final UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	private static final LocalRepo lRepo = LocalRepo.getInstance();
	private static final RemoteRepo rRepo = RemoteRepo.getInstance();
	HZoningDAO zoningDAO = HybridHelpDatabase.getInstance().getZoningDao();


	@BeforeAll
	public static void setUp() throws IOException {
		//Put empty content to Local and Remote if it does not already exist
		lRepo.writeContents(emptyChecksum, Uri.fromFile(emptyFile.toFile()));
		try {
			rRepo.getContentProps(emptyChecksum);
		} catch (ContentsNotFoundException e) {
			createEmptyFile();
			rRepo.uploadData(emptyChecksum, emptyFile.toFile());
		}

		//Put the test content to Local and Remote if it does not already exist
		lRepo.writeContents(smallChecksum, Uri.fromFile(smallFile.toFile()));
		try {
			rRepo.getContentProps(smallChecksum);
		} catch (ContentsNotFoundException e) {
			create1MBFile();
			rRepo.uploadData(smallChecksum, smallFile.toFile());
		}


		Configuration config = new Configuration.Builder()
				.setMinimumLoggingLevel(Log.DEBUG)
				.setExecutor(new SynchronousExecutor())
				.build();

		// Initialize WorkManager for instrumentation tests.
		WorkManagerTestInitHelper.initializeTestWorkManager(
				context, config);
	}


	private UUID fileUID;
	@BeforeEach
	public void setup() {
		fileUID = UUID.randomUUID();
	}



	private LFile makeLocalFile() {
		LFile newFile = new LFile(fileUID, accountUID);
		newFile.checksum = smallChecksum;
		newFile.filesize = (int) smallFile.toFile().length();
		return lRepo.putFileProps(newFile, "", "");
	}
	private RFile makeRemoteFile() throws ConnectException {
		RFile newFile = new RFile(fileUID, accountUID);
		newFile.checksum = smallChecksum;
		newFile.filesize = (int) smallFile.toFile().length();
		try {
			return rRepo.createFile(newFile);
		} catch (ContentsNotFoundException | FileAlreadyExistsException e) {
			throw new RuntimeException(e);
		}
	}


	@Disabled
	@Test
	public void testWorkerInput() {

	}



	@Test
	public void testStartLocal_ZoneLocal() throws ExecutionException, InterruptedException, ConnectException {
		System.out.println("Starting testing");
		LFile start = makeLocalFile();

		System.out.println("Making thing");

		Data input = new Data.Builder()
				.put("FILEUID", fileUID)
				.put("LOCAL", true)
				.put("REMOTE", false)
				.build();

		OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ZoningWorker.class)
						.setInputData(input).build();
		WorkManager workManager = WorkManager.getInstance(context);
		workManager.enqueue(request).getResult().get();
		WorkInfo workInfo = workManager.getWorkInfoById(request.getId()).get();

		System.out.println("Asserting");

		Assertions.assertEquals(workInfo.getState(), WorkInfo.State.SUCCEEDED);
		Assertions.assertTrue(lRepo.doesFileExist(fileUID));
		Assertions.assertFalse(rRepo.doesFileExist(fileUID));
	}






	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------


	private static void createEmptyFile() throws IOException {
		if(emptyFile.toFile().exists())
			return;

		Files.createDirectories(emptyFile.getParent());
		Files.createFile(emptyFile);
	}

	private static void create1MBFile() throws IOException {
		if(smallFile.toFile().exists())
			return;

		Files.createDirectories(smallFile.getParent());
		Files.createFile(smallFile);

		URL largeUrl = new URL(externalUri_1MB.toString());
		try (BufferedInputStream in = new BufferedInputStream(largeUrl.openStream());
			 DigestInputStream dis = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"));
			 FileOutputStream fileOutputStream = new FileOutputStream(smallFile.toFile())) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = dis.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}

			String checksum = Utilities.bytesToHex(dis.getMessageDigest().digest());
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}