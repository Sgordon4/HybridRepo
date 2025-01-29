package aaa.sgordon.hybridrepo;

import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
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
import aaa.sgordon.hybridrepo.remote.types.RFile;

public class ZoningTest {

	private static File smallFile;
	private static String smallChecksum;


	private static final UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	private static RemoteRepo rRepo;
	private static LocalRepo lRepo;
	private static HZoningDAO zoningDAO;

	private static Executor executor;


	@BeforeAll
	public static void beforeAll(@TempDir File tempDir) throws IOException {
		Context context = InstrumentationRegistry.getInstrumentation().getContext();
		executor = Executors.newSingleThreadExecutor();


		LocalDatabase db = Room.inMemoryDatabaseBuilder(context, LocalDatabase.class).allowMainThreadQueries().build();
		LocalRepo.initialize(db, tempDir.toString());
		lRepo = LocalRepo.getInstance();

		rRepo = RemoteRepo.getInstance();
		rRepo.setAccount(accountUID);

		HybridHelpDatabase hybDB = Room.inMemoryDatabaseBuilder(context, HybridHelpDatabase.class).allowMainThreadQueries().build();
		Sync.initialize(hybDB, context);
		zoningDAO = Sync.getInstance().zoningDAO;


		smallFile = createTempFile(tempDir, "Very lengthy input data.".getBytes());
		smallChecksum = smallFile.getName();
	}


	private UUID fileUID;
	@BeforeEach
	public void beforeEach() throws InterruptedException, ConnectException {
		fileUID = UUID.randomUUID();

		deleteEverything();

		//Make sure both the file and the contents are in neither repo
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> lRepo.getFileProps(fileUID));
		Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> lRepo.getContentProps(smallChecksum));
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> rRepo.getFileProps(fileUID));
		Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> rRepo.getContentProps(smallChecksum));

		//Thread.sleep(500);

		System.out.println("=====================================================================");
		System.out.println("STARTING TEST!");
		System.out.println("=====================================================================");
	}

	@AfterEach
	public void afterEach() throws ConnectException {
		deleteEverything();
	}

	private void deleteEverything() throws ConnectException {
		try {
			lRepo.lock(fileUID);
			lRepo.deleteFileProps(fileUID);
		} catch (FileNotFoundException ignored) {}
		finally {
			lRepo.unlock(fileUID);
		}
		lRepo.deleteContents(smallChecksum);

		try {
			rRepo.deleteFileProps(fileUID);
		} catch (FileNotFoundException ignored) {}
		try {
			rRepo.deleteContentProps(smallChecksum);
		} catch (ContentsNotFoundException ignored) {}
	}


	//---------------------------------------------------------------------------------------------

	@Test
	public void zone_L_L() {
		assertNoZoningData();
		startFromLocal();
		assertIsLocalOnly();

		//Attempt to move zone from Local to Local
		changeZones(true, false);
		assertIsLocalOnly();

		//Do it again
		changeZones(true, false);
		assertIsLocalOnly();
	}


	@Test
	public void zone_R_R() {
		assertNoZoningData();
		startFromRemote();
		assertIsRemoteOnly();

		//Attempt to move zone from Remote to Remote
		changeZones(false, true);
		assertIsRemoteOnly();

		//Do it again
		changeZones(false, true);
		assertIsRemoteOnly();
	}

	//---------------------------------------------------------------------------------------------

	@Test
	public void zone_L_R_L_R() {
		assertNoZoningData();
		startFromLocal();
		assertIsLocalOnly();

		//Attempt to move zone from Local to Remote
		changeZones(false, true);
		assertIsRemoteOnly();

		//Attempt to move zone from Remote to Local
		changeZones(true, false);
		assertIsLocalOnly();

		//Attempt to move zone from Local to Remote
		changeZones(false, true);
		assertIsRemoteOnly();
	}


	@Test
	public void zone_R_L_R_L() {
		assertNoZoningData();
		startFromRemote();
		assertIsRemoteOnly();

		//Attempt to move zone from Remote to Local
		changeZones(true, false);
		assertIsLocalOnly();

		//Attempt to move zone from Local to Remote
		changeZones(false, true);
		assertIsRemoteOnly();

		//Attempt to move zone from Remote to Local
		changeZones(true, false);
		assertIsLocalOnly();
	}

	//---------------------------------------------------------------------------------------------

	@Test
	public void zone_L_LR_R_LR_L_LR() {
		assertNoZoningData();
		startFromLocal();
		assertIsLocalOnly();

		//Attempt to move zone from Local to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Remote
		changeZones(false, true);
		assertIsRemoteOnly();

		//Attempt to move zone from Remote to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Local
		changeZones(true, false);
		assertIsLocalOnly();

		//Attempt to move zone from Local to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();
	}

	@Test
	public void zone_R_LR_L_LR_R_LR() {
		assertNoZoningData();
		startFromRemote();
		assertIsRemoteOnly();

		//Attempt to move zone from Remote to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Local
		changeZones(true, false);
		assertIsLocalOnly();

		//Attempt to move zone from Local to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Remote
		changeZones(false, true);
		assertIsRemoteOnly();

		//Attempt to move zone from Remote to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();
	}

	//---------------------------------------------------------------------------------------------

	@Test
	public void zone_L_LR_LR_L() {
		assertNoZoningData();
		startFromLocal();
		assertIsLocalOnly();

		//Attempt to move zone from Local to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Local
		changeZones(true, false);
		assertIsLocalOnly();
	}

	@Test
	public void zone_L_LR_LR_R() {
		assertNoZoningData();
		startFromLocal();
		assertIsLocalOnly();

		//Attempt to move zone from Local to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Remote
		changeZones(false, true);
		assertIsRemoteOnly();
	}



	@Test
	public void zone_R_LR_LR_R() {
		assertNoZoningData();
		startFromRemote();
		assertIsRemoteOnly();

		//Attempt to move zone from Remote to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Remote
		changeZones(false, true);
		assertIsRemoteOnly();
	}

	@Test
	public void zone_R_LR_LR_L() {
		assertNoZoningData();
		startFromRemote();
		assertIsRemoteOnly();

		//Attempt to move zone from Remote to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Local&Remote
		changeZones(true, true);
		assertIsLocalAndRemote();

		//Attempt to move zone from Local&Remote to Local
		changeZones(true, false);
		assertIsLocalOnly();
	}







	//---------------------------------------------------------------------------------------------

	private void assertIsLocalOnly() {
		System.out.println("Asserting isLocalOnly");
		assertZoningCorrect(true, false);
		Assertions.assertDoesNotThrow(() -> {
			LFile props = lRepo.getFileProps(fileUID);
			lRepo.getContentProps(props.checksum);
		});
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> {
			RFile props = rRepo.getFileProps(fileUID);
			rRepo.getContentProps(props.checksum);	//Shouldn't reach this line
		});
	}
	private void assertIsRemoteOnly() {
		System.out.println("Asserting isRemoteOnly");
		assertZoningCorrect(false, true);
		System.out.println("Zoning is correct");

		//For Local:
		//A file's properties will always be on local if the file hasn't been deleted, so we shouldn't check for FileNotFound
		//A file's contents, even if the file is zoned to non-local, won't be deleted until cleanup in case another file
		// is using them. Therefore we shouldn't check for ContentsNotFound

		Assertions.assertDoesNotThrow(() -> {
			RFile props = rRepo.getFileProps(fileUID);
			rRepo.getContentProps(props.checksum);
		});
		System.out.println("Does not throw");
	}
	private void assertIsLocalAndRemote() {
		System.out.println("Asserting isLocalAndRemote");
		assertZoningCorrect(true, true);
		Assertions.assertDoesNotThrow(() -> {
			LFile props = lRepo.getFileProps(fileUID);
			lRepo.getContentProps(props.checksum);
		});
		Assertions.assertDoesNotThrow(() -> {
			RFile props = rRepo.getFileProps(fileUID);
			rRepo.getContentProps(props.checksum);
		});
	}
	//TODO This should fail, I don't actually remember what zoning does here (or if it allows it)
	private void assertIsNone() {
		assertZoningCorrect(false, false);
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> lRepo.getFileProps(fileUID));
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> rRepo.getFileProps(fileUID));
	}


	private void assertZoningCorrect(boolean isLocal, boolean isRemote) {
		HZone zoningInfo = zoningDAO.get(fileUID);
		Assertions.assertNotNull(zoningInfo);
		Assertions.assertEquals(zoningInfo.isLocal, isLocal);
		Assertions.assertEquals(zoningInfo.isRemote, isRemote);
	}
	private void assertNoZoningData() {
		HZone zoningInfo = zoningDAO.get(fileUID);
		Assertions.assertNull(zoningInfo);
	}


	private void changeZones(boolean isLocal, boolean isRemote) {
		ZoningWorker worker = launchWorker(InstrumentationRegistry.getInstrumentation().getContext(),
				fileUID, isLocal, isRemote);
		ListenableWorker.Result result = worker.doWork();
		Assertions.assertEquals(result, ListenableWorker.Result.success());
	}

	private ZoningWorker launchWorker(Context context, UUID fileUID, boolean isLocal, boolean isRemote) {
		Data inputData = new Data.Builder()
				.put("FILEUID", fileUID.toString())
				.put("LOCAL", String.valueOf(isLocal))
				.put("REMOTE", String.valueOf(isRemote))
				.build();

		return TestWorkerBuilder.from(context, ZoningWorker.class, executor).setInputData(inputData).build();
	}


	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------

	private LFile startFromLocal() {
		//Put contents
		lRepo.writeContents(smallChecksum, Uri.fromFile(smallFile));
		Assertions.assertDoesNotThrow(() -> lRepo.getContentProps(smallChecksum));


		try {
			lRepo.lock(fileUID);
			//Create the file props on local
			LFile newProps = new LFile(fileUID, accountUID);
			newProps.checksum = smallChecksum;
			newProps.filesize = (int) smallFile.length();
			newProps = lRepo.putFileProps(newProps, "", "");
			Assertions.assertDoesNotThrow(() -> lRepo.getFileProps(fileUID));

			//Set zoning info exactly how HybridAPI does in createFile()
			HZone newZoningInfo = new HZone(fileUID, true, false);
			zoningDAO.put(newZoningInfo);


			return newProps;
		} finally {
			lRepo.unlock(fileUID);
		}
	}


	private RFile startFromRemote() {
		try {
			//If they don't already exist
			rRepo.getContentProps(smallChecksum);
		} catch (ContentsNotFoundException e) {
			try {
				//Put contents
				rRepo.uploadData(smallChecksum, smallFile);
			} catch (FileNotFoundException | ConnectException ex) {
				throw new RuntimeException(ex);
			}
		} catch (ConnectException e) {
			throw new RuntimeException(e);
		}

		//Create the file props on remote
		RFile newProps = new RFile(fileUID, accountUID);
		newProps.checksum = smallChecksum;
		newProps.filesize = (int) smallFile.length();
		try {
			newProps = rRepo.createFile(newProps);
		}
		catch (ContentsNotFoundException | FileAlreadyExistsException e) {
			throw new RuntimeException(e);
		} catch (ConnectException e) {
			throw new RuntimeException();
		}

		//After creating a file on remote, the next sync would then create the file on local, minus the contents
		try {
			lRepo.lock(fileUID);
			LFile newLocal = HFile.toLocalFile(newProps);
			lRepo.putFileProps(newLocal, "", "");

			//Set zoning info exactly how Sync would
			HZone newZoningInfo = new HZone(fileUID, false, true);
			zoningDAO.put(newZoningInfo);
		} finally {
			lRepo.unlock(fileUID);
		}

		return newProps;
	}


	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------

	private static File createTempFile(File tempDir, byte[] data) throws IOException {
		String checksum;
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(data);
			checksum = Utilities.bytesToHex(md.digest());
		}
		catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }


		File newTempFile = new File(tempDir, checksum);
		if(newTempFile.exists())
			return newTempFile;

		Files.createDirectories(tempDir.toPath());
		Files.createFile(newTempFile.toPath());


		try(FileOutputStream out = new FileOutputStream(newTempFile)) {
			out.write(data);
		}
		return newTempFile;
	}
}
