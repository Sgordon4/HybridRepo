package aaa.sgordon.hybridrepo;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.room.Room;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestDriver;
import androidx.work.testing.TestWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.hybrid.HybridAPI;
import aaa.sgordon.hybridrepo.hybrid.database.HZone;
import aaa.sgordon.hybridrepo.hybrid.database.HZoningDAO;
import aaa.sgordon.hybridrepo.hybrid.database.HybridHelpDatabase;
import aaa.sgordon.hybridrepo.hybrid.jobs.sync.Sync;
import aaa.sgordon.hybridrepo.hybrid.jobs.sync.ZoningWorker;
import aaa.sgordon.hybridrepo.hybrid.types.HFile;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.local.database.LocalDatabase;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;
import aaa.sgordon.hybridrepo.remote.connectors.JournalConnector;
import aaa.sgordon.hybridrepo.remote.types.RFile;

public class ZoningTest {

	private static Path emptyFile;
	private static final String emptyChecksum = RFile.defaultChecksum;

	private static final Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	private static Path smallFile;
	private static final String smallChecksum = "35C461DEE98AAD4739707C6CCA5D251A1617BFD928E154995CA6F4CE8156CFFC";

	private static final UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	private static RemoteRepo rRepo;
	private static LocalRepo lRepo;
	private static HybridAPI hAPI;
	private static HZoningDAO zoningDAO;

	private static WorkManager workManager;
	private static TestDriver testDriver;

	private static Executor executor;


	@BeforeAll
	public static void beforeAll(@TempDir File tempDir) throws NoSuchFieldException, IllegalAccessException {
		emptyFile = Paths.get(tempDir.toString(), "temp", "empty.txt");
		smallFile = Paths.get(tempDir.toString(), "temp", "smallFile.txt");


		Context context = InstrumentationRegistry.getInstrumentation().getContext();

		executor = Executors.newSingleThreadExecutor();

		System.out.println("Arg");
		LocalDatabase db = Room.inMemoryDatabaseBuilder(context, LocalDatabase.class).allowMainThreadQueries().build();
		LocalRepo.initialize(db, tempDir.toString());
		lRepo = LocalRepo.getInstance();


		rRepo = RemoteRepo.getInstance();
		rRepo.setAccount(accountUID);


		System.out.println("Syncing");
		HybridHelpDatabase hybDB = Room.inMemoryDatabaseBuilder(context, HybridHelpDatabase.class).allowMainThreadQueries().build();
		Sync.initialize(hybDB, context);
		zoningDAO = Sync.getInstance().zoningDAO;

		System.out.println("Working");
	}


	private UUID fileUID;
	@BeforeEach
	public void beforeEach() {
		fileUID = UUID.randomUUID();
	}




	@Test
	public void zoneToLocal_StartingLocal() {
		startFromLocal();

		HZone zoningInfo = zoningDAO.get(fileUID);
		Assertions.assertNotNull(zoningInfo);
		Assertions.assertTrue(zoningInfo.isLocal);
		Assertions.assertFalse(zoningInfo.isRemote);


		//Attempt to move zone from Local to Local
		ZoningWorker worker = launchWorker(InstrumentationRegistry.getInstrumentation().getContext(), fileUID, true, false);
		ListenableWorker.Result result = worker.doWork();
		Assertions.assertEquals(result, ListenableWorker.Result.success());

		zoningInfo = zoningDAO.get(fileUID);
		Assertions.assertNotNull(zoningInfo);
		Assertions.assertTrue(zoningInfo.isLocal);
		Assertions.assertFalse(zoningInfo.isRemote);


		//Do it again
		worker = launchWorker(InstrumentationRegistry.getInstrumentation().getContext(), fileUID, true, false);
		result = worker.doWork();
		Assertions.assertEquals(result, ListenableWorker.Result.success());

		zoningInfo = zoningDAO.get(fileUID);
		Assertions.assertNotNull(zoningInfo);
		Assertions.assertTrue(zoningInfo.isLocal);
		Assertions.assertFalse(zoningInfo.isRemote);
	}


	@Test
	public void zoneToLocal_StartingRemote() throws ConnectException {
		startFromRemote();

		HZone zoningInfo = zoningDAO.get(fileUID);
		Assertions.assertNotNull(zoningInfo);
		Assertions.assertFalse(zoningInfo.isLocal);
		Assertions.assertTrue(zoningInfo.isRemote);


		//Attempt to move zone from Remote to Local
		ZoningWorker worker = launchWorker(InstrumentationRegistry.getInstrumentation().getContext(), fileUID, true, false);
		ListenableWorker.Result result = worker.doWork();
		Assertions.assertEquals(ListenableWorker.Result.success(), result);

		zoningInfo = zoningDAO.get(fileUID);
		Assertions.assertNotNull(zoningInfo);
		Assertions.assertTrue(zoningInfo.isLocal);
		Assertions.assertFalse(zoningInfo.isRemote);


		//Do it again
		worker = launchWorker(InstrumentationRegistry.getInstrumentation().getContext(), fileUID, true, false);
		result = worker.doWork();
		Assertions.assertEquals(ListenableWorker.Result.success(), result);

		zoningInfo = zoningDAO.get(fileUID);
		Assertions.assertNotNull(zoningInfo);
		Assertions.assertTrue(zoningInfo.isLocal);
		Assertions.assertFalse(zoningInfo.isRemote);
	}





	//---------------------------------------------------------------------------------------------


	private ZoningWorker launchWorker(Context context, UUID fileUID, boolean isLocal, boolean isRemote) {
		Data inputData = new Data.Builder()
				.put("FILEUID", fileUID.toString())
				.put("LOCAL", String.valueOf(isLocal))
				.put("REMOTE", String.valueOf(isRemote))
				.build();

		return TestWorkerBuilder.from(context, ZoningWorker.class, executor).setInputData(inputData).build();
	}


	private LFile startFromLocal() {
		//Create a file on local
		LFile newFile = new LFile(fileUID, accountUID);
		newFile.checksum = smallChecksum;
		newFile.filesize = (int) smallFile.toFile().length();

		try {
			lRepo.lock(fileUID);
			newFile = lRepo.putFileProps(newFile, "", "");
		} finally {
			lRepo.unlock(fileUID);
		}

		//Do exactly what HAPI does in createFile() and make zoning info
		HZone newZoningInfo = new HZone(fileUID, true, false);
		zoningDAO.put(newZoningInfo);

		return newFile;
	}


	private RFile startFromRemote() throws ConnectException {
		//Create a file on remote
		RFile newFile = new RFile(fileUID, accountUID);
		newFile.checksum = smallChecksum;
		newFile.filesize = (int) smallFile.toFile().length();
		try {
			newFile = rRepo.createFile(newFile);
		}
		catch (ContentsNotFoundException | FileAlreadyExistsException e) {
			throw new RuntimeException(e);
		}

		//After creating a file on remote, sync would then create the file on local, minus the contents
		try {
			lRepo.lock(fileUID);
			LFile newLocal = HFile.toLocalFile(newFile);
			lRepo.putFileProps(newLocal, "", "");
		} finally {
			lRepo.unlock(fileUID);
		}

		//Do exactly what Sync does and make zoning info
		HZone newZoningInfo = new HZone(fileUID, false, true);
		zoningDAO.put(newZoningInfo);

		return newFile;
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
