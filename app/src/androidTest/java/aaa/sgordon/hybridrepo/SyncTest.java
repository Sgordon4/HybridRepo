package aaa.sgordon.hybridrepo;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.room.Room;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;

import com.google.gson.JsonObject;

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
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.hybrid.HybridAPI;
import aaa.sgordon.hybridrepo.hybrid.database.HZone;
import aaa.sgordon.hybridrepo.hybrid.database.HZoningDAO;
import aaa.sgordon.hybridrepo.hybrid.database.HybridHelpDatabase;
import aaa.sgordon.hybridrepo.hybrid.jobs.sync.Sync;
import aaa.sgordon.hybridrepo.hybrid.jobs.sync.SyncWorkers;
import aaa.sgordon.hybridrepo.hybrid.jobs.sync.ZoningWorker;
import aaa.sgordon.hybridrepo.hybrid.types.HFile;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.local.database.LocalDatabase;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;
import aaa.sgordon.hybridrepo.remote.connectors.FileConnector;
import aaa.sgordon.hybridrepo.remote.connectors.JournalConnector;
import aaa.sgordon.hybridrepo.remote.types.RFile;

public class SyncTest {

	private static File emptyFile;
	private static File smallFile;
	private static File largerFile;


	private static RemoteRepo rRepo;
	private static LocalRepo lRepo;
	private static HybridAPI hAPI;
	private static HZoningDAO zoningDAO;

	private static Executor executor;

	private static Integer localSyncID;
	private static Integer remoteSyncID;

	private static UUID originalDeviceUID;
	private static Field reflectDeviceuid;


	@BeforeAll
	public static void beforeAll(@TempDir File tempDir) throws IOException, NoSuchFieldException, IllegalAccessException {
		Context context = InstrumentationRegistry.getInstrumentation().getContext();
		executor = Executors.newSingleThreadExecutor();


		LocalDatabase db = Room.inMemoryDatabaseBuilder(context, LocalDatabase.class).allowMainThreadQueries().build();
		LocalRepo.initialize(db, tempDir.toString());
		lRepo = LocalRepo.getInstance();

		rRepo = RemoteRepo.getInstance();

		HybridHelpDatabase hybDB = Room.inMemoryDatabaseBuilder(context, HybridHelpDatabase.class).allowMainThreadQueries().build();
		Sync.initialize(hybDB, context);
		zoningDAO = Sync.getInstance().zoningDAO;


		hAPI = HybridAPI.getInstance();


		emptyFile = createTempFile(tempDir, "".getBytes());
		smallFile = createTempFile(tempDir, "Very lengthy input data.".getBytes());
		largerFile = createTempFile(tempDir, "The fitness gram pacer test is a multistage aerobic capacity...".getBytes());


		localSyncID = 0;
		remoteSyncID = 0;

		//The queries server-side exclude all journals created by our deviceuid.
		//Therefore we have to change the deviceuid we send over or we will receive no data for the tests.
		reflectDeviceuid = JournalConnector.class.getDeclaredField("deviceUID");
		reflectDeviceuid.setAccessible(true);
		originalDeviceUID = (UUID) reflectDeviceuid.get(rRepo.journalConn);
	}


	private UUID fileUID;
	@BeforeEach
	public void beforeEach() throws ConnectException, IllegalAccessException {
		reflectDeviceuid.set(rRepo.journalConn, UUID.randomUUID());

		fileUID = UUID.randomUUID();
		System.out.println("=====================================================================");
		System.out.println("STARTING TEST!");

		deleteEverything();

		//Make sure both the file and the contents are in neither repo
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> lRepo.getFileProps(fileUID));
		Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> lRepo.getContentProps(emptyFile.getName()));
		Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> lRepo.getContentProps(smallFile.getName()));
		Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> lRepo.getContentProps(largerFile.getName()));
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> rRepo.getFileProps(fileUID));
		Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> rRepo.getContentProps(emptyFile.getName()));
		Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> rRepo.getContentProps(smallFile.getName()));
		Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> rRepo.getContentProps(largerFile.getName()));

		System.out.println("=====================================================================");
	}

	@AfterEach
	public void afterEach() throws ConnectException {
		System.out.println("=====================================================================");
		System.out.println("ENDING TEST!");
		deleteEverything();
		System.out.println("=====================================================================");
	}

	private void deleteEverything() throws ConnectException {
		try {
			lRepo.lock(fileUID);
			lRepo.deleteFileProps(fileUID);
		} catch (FileNotFoundException ignored) {}
		finally {
			lRepo.unlock(fileUID);
		}
		lRepo.deleteContents(emptyFile.getName());
		lRepo.deleteContents(smallFile.getName());
		lRepo.deleteContents(largerFile.getName());


		try {
			rRepo.deleteFileProps(fileUID);
		} catch (FileNotFoundException ignored) {}
		try { rRepo.deleteContentProps(emptyFile.getName()); }
		catch (ContentsNotFoundException ignored) {}
		try { rRepo.deleteContentProps(smallFile.getName()); }
		catch (ContentsNotFoundException ignored) {}
		try { rRepo.deleteContentProps(largerFile.getName()); }
		catch (ContentsNotFoundException ignored) {}
	}




	//---------------------------------------------------------------------------------------------


	@Test
	public void sync_R_wCR_wCR_wAR_wCAR() throws ConnectException, FileNotFoundException, ContentsNotFoundException {
		RFile newFile = createRemoteFile(smallFile);
		JsonObject userAttr = newFile.userattr;
		assertNoZoningData();
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> lRepo.getFileProps(fileUID));
		Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> lRepo.getContentProps(smallFile.getName()));
		Assertions.assertDoesNotThrow(() -> rRepo.getFileProps(fileUID));
		Assertions.assertDoesNotThrow(() -> rRepo.getContentProps(smallFile.getName()));

		System.out.println("=====================================================================");
		System.out.println("Sync1");
		runSync();
		assertZoningCorrect(false, true);
		assertSyncedWithZoning(smallFile, userAttr);

		System.out.println("=====================================================================");
		System.out.println("Sync2");
		//Change remote contents
		writeRemote(largerFile);
		runSync();
		assertZoningCorrect(false, true);
		assertSyncedWithZoning(largerFile, userAttr);

		System.out.println("=====================================================================");
		System.out.println("Sync3");
		//Change remote contents
		writeRemote(smallFile);
		runSync();
		assertZoningCorrect(false, true);
		assertSyncedWithZoning(smallFile, userAttr);

		System.out.println("=====================================================================");
		System.out.println("Sync4");
		//Change remote attributes
		userAttr = new JsonObject();
		userAttr.addProperty("Test1", "Something");
		setAttrLocal(userAttr);
		assertZoningCorrect(false, true);
		runSync();
		assertZoningCorrect(false, true);
		assertSyncedWithZoning(smallFile, userAttr);

		System.out.println("=====================================================================");
		System.out.println("Sync5");
		//Change remote contents and attributes
		writeRemote(largerFile);
		userAttr = new JsonObject();
		userAttr.addProperty("Test1", "Something");
		userAttr.addProperty("Prop2", "Whatever");
		userAttr.addProperty("AAAAA", "BBBBB");
		setAttrRemote(userAttr);
		runSync();
		assertZoningCorrect(false, true);
		assertSyncedWithZoning(largerFile, userAttr);
	}



	@Test
	public void sync_R_wCR_LR_wCR_wAR_wCAR() throws ConnectException, FileNotFoundException, ContentsNotFoundException {
		RFile newFile = createRemoteFile(smallFile);
		JsonObject userAttr = newFile.userattr;
		assertNoZoningData();
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> lRepo.getFileProps(fileUID));
		Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> lRepo.getContentProps(smallFile.getName()));
		Assertions.assertDoesNotThrow(() -> rRepo.getFileProps(fileUID));
		Assertions.assertDoesNotThrow(() -> rRepo.getContentProps(smallFile.getName()));

		runSync();
		assertZoningCorrect(false, true);
		assertSyncedWithZoning(smallFile, userAttr);

		//Change remote contents
		writeRemote(largerFile);
		runSync();
		assertZoningCorrect(false, true);
		assertSyncedWithZoning(largerFile, userAttr);

		changeZones(true, true);

		//Change remote contents
		writeRemote(smallFile);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(smallFile, userAttr);

		//Change remote attributes
		userAttr = new JsonObject();
		userAttr.addProperty("Test1", "Something");
		setAttrLocal(userAttr);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(smallFile, userAttr);

		//Change remote contents and attributes
		writeRemote(largerFile);
		userAttr = new JsonObject();
		userAttr.addProperty("Test1", "Something");
		userAttr.addProperty("Prop2", "Whatever");
		userAttr.addProperty("AAAAA", "BBBBB");
		setAttrRemote(userAttr);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(largerFile, userAttr);
	}



	//---------------------------------------------------------------------------------------------


	@Test
	public void sync_L_wCL_LR_wCL_wAL_wCAL() throws ConnectException, FileNotFoundException, ContentsNotFoundException {
		assertNoZoningData();
		LFile newFile = createLocalFile(smallFile);
		JsonObject userAttr = newFile.userattr;
		assertZoningCorrect(true, false);
		assertSyncedWithZoning(smallFile, userAttr);

		runSync();
		assertZoningCorrect(true, false);
		assertSyncedWithZoning(smallFile, userAttr);

		//Change local contents
		writeLocal(largerFile);
		runSync();
		assertZoningCorrect(true, false);
		assertSyncedWithZoning(largerFile, userAttr);

		changeZones(true, true);

		//Change remote contents
		writeLocal(smallFile);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(smallFile, userAttr);

		//Change local attributes
		userAttr = new JsonObject();
		userAttr.addProperty("Test1", "Something");
		setAttrLocal(userAttr);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(smallFile, userAttr);

		//Change local contents and attributes
		writeLocal(largerFile);
		userAttr = new JsonObject();
		userAttr.addProperty("Test1", "Something");
		userAttr.addProperty("Prop2", "Whatever");
		userAttr.addProperty("AAAAA", "BBBBB");
		setAttrLocal(userAttr);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(largerFile, userAttr);
	}



	@Test
	public void sync_L_wCL_LR_wCL_wAR_wCAR() throws ConnectException, FileNotFoundException, ContentsNotFoundException {
		assertNoZoningData();
		LFile newFile = createLocalFile(smallFile);
		JsonObject userAttr = newFile.userattr;
		assertZoningCorrect(true, false);
		assertSyncedWithZoning(smallFile, userAttr);

		runSync();
		assertZoningCorrect(true, false);
		assertSyncedWithZoning(smallFile, userAttr);

		//Change local contents
		writeLocal(largerFile);
		runSync();
		assertZoningCorrect(true, false);
		assertSyncedWithZoning(largerFile, userAttr);

		changeZones(true, true);
		assertSyncedWithZoning(largerFile, userAttr);

		//Change local contents
		writeLocal(smallFile);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(smallFile, userAttr);

		//Change remote attributes
		userAttr.addProperty("Test1", "Something");
		setAttrRemote(userAttr);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(smallFile, userAttr);

		//Change remote contents and attributes
		writeRemote(largerFile);
		userAttr = new JsonObject();
		userAttr.addProperty("Test1", "Something");
		userAttr.addProperty("Prop2", "Whatever");
		userAttr.addProperty("AAAAA", "BBBBB");
		setAttrRemote(userAttr);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(largerFile, userAttr);
	}


	//---------------------------------------------------------------------------------------------


	@Test
	public void sync_L_LR_wCL_wAR_L_wCL() throws ConnectException, FileNotFoundException {
		assertNoZoningData();
		LFile newFile = createLocalFile(smallFile);
		JsonObject userAttr = newFile.userattr;
		assertZoningCorrect(true, false);
		assertSyncedWithZoning(smallFile, userAttr);

		changeZones(true, true);

		//Change local contents
		writeLocal(largerFile);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(largerFile, userAttr);

		//Change remote attributes
		userAttr = new JsonObject();
		userAttr.addProperty("Test1", "Something");
		setAttrRemote(userAttr);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(largerFile, userAttr);

		try {
			//Because we changed deviceUID in order to get journals from our changes, this means the delete
			// journal incoming from remote because of this next changeZones will hit us and cause local to be deleted.
			//We don't want that, put the deviceUID back.
			reflectDeviceuid.set(rRepo.journalConn, originalDeviceUID);
		} catch (IllegalAccessException e) { throw new RuntimeException(e); }
		changeZones(true, false);


		//Change local contents and attributes
		writeLocal(smallFile);
		runSync();
		assertZoningCorrect(true, false);
		//Can't use the usual assertSyncedWithZoning() because contentProps exist on Remote now.
		//This is just that but trimmed down.
		Assertions.assertDoesNotThrow(() -> lRepo.getFileProps(fileUID));
		Assertions.assertDoesNotThrow(() -> lRepo.getContentProps(smallFile.getName()));
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> rRepo.getFileProps(fileUID));
	}



	@Test
	public void sync_L_LR_wCL_DEL_R_wCR_LR() throws ConnectException, FileNotFoundException,
			IllegalAccessException, FileAlreadyExistsException, ContentsNotFoundException, NoSuchFieldException {
		assertNoZoningData();
		LFile newFile = createLocalFile(smallFile);
		JsonObject userAttr = newFile.userattr;
		assertZoningCorrect(true, false);
		assertSyncedWithZoning(smallFile, userAttr);

		changeZones(true, true);

		//Change local contents
		writeLocal(largerFile);
		runSync();
		assertZoningCorrect(true, true);
		assertSyncedWithZoning(largerFile, userAttr);

		//Delete from local, which at next sync will delete from remote
		LFile fileProps = lRepo.getFileProps(fileUID);
		try {
			hAPI.lockLocal(fileUID);
			hAPI.deleteFile(fileUID);
		} finally {
			hAPI.unlockLocal(fileUID);
		}
		assertNoZoningData();
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> lRepo.getFileProps(fileUID));
		Assertions.assertDoesNotThrow(() -> rRepo.getFileProps(fileUID));

		//Sync to delete from remote
		runSync();
		assertNoZoningData();
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> lRepo.getFileProps(fileUID));
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> rRepo.getFileProps(fileUID));

		//In prod, nobody else will be able to update this file, so this chain is now complete. No further testing needed.
		//However, if we were to admin un-delete it, I want to make sure things go well.

		//Outside of our testing environment, remote won't send us the new delete journal because it has our deviceUID.
		//However, here we've changed our deviceUID, so I'm just going to increment our remoteSyncID to avoid it.
		//This isn't a guaranteed method to do so, but this is a test so idc.
		remoteSyncID++;

		//Remake the file on server, but with some different properties
		userAttr.addProperty("Undeleted", "Yessir");
		fileProps.userattr = userAttr;
		//Because in this test we're fetching journals using a different deviceUID than the one we're setting data with,
		// we'll get the journal for this create where we otherwise wouldn't on production.
		rRepo.createFile(HFile.toRemoteFile(fileProps));
		assertNoZoningData();
		Assertions.assertThrowsExactly(FileNotFoundException.class, () -> lRepo.getFileProps(fileUID));
		Assertions.assertDoesNotThrow(() -> rRepo.getFileProps(fileUID));

		runSync();
		assertZoningCorrect(false, true);
		assertSyncedWithZoning(largerFile, userAttr);
	}






































	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------


	private void runSync() {
		SyncWorkers.SyncWorker worker = launchSyncWorker(InstrumentationRegistry.getInstrumentation().getContext(), fileUID);
		ListenableWorker.Result result = worker.doWork();
		Assertions.assertInstanceOf(ListenableWorker.Result.Success.class, result);

		Data outputData = result.getOutputData();
		localSyncID = Integer.parseInt(outputData.getString("LOCAL_JID"));
		remoteSyncID = Integer.parseInt(outputData.getString("REMOTE_JID"));
	}


	private SyncWorkers.SyncWorker launchSyncWorker(Context context, UUID fileUID) {
		Data inputData = new Data.Builder()
				.put("FILEUID", fileUID.toString())
				.put("LOCAL_JID", String.valueOf(localSyncID))
				.put("REMOTE_JID", String.valueOf(remoteSyncID))
				.build();

		return TestWorkerBuilder.from(context, SyncWorkers.SyncWorker.class, executor).setInputData(inputData).build();
	}


	private void assertSyncedWithZoning(File correctFile, JsonObject correctAttr) throws ConnectException {
		HZone zoningInfo = zoningDAO.get(fileUID);
		assert zoningInfo != null;

		//Assert local is as it should be
		if(zoningInfo.isLocal) {
			Assertions.assertDoesNotThrow(() -> lRepo.getFileProps(fileUID));
			Assertions.assertDoesNotThrow(() -> lRepo.getContentProps(correctFile.getName()));
		}
		else {
			Assertions.assertDoesNotThrow(() -> lRepo.getFileProps(fileUID));
			//Note: Local contents can exist when the file isn't zoned to local. In this test it's because of leftover contents.
			//This works MOST of the time in this test, but not always.
			//Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> lRepo.getContentProps(correctFile.getName()));
		}

		//Assert remote is as it should be
		if(zoningInfo.isRemote) {
			Assertions.assertDoesNotThrow(() -> rRepo.getFileProps(fileUID));
			Assertions.assertDoesNotThrow(() -> rRepo.getContentProps(correctFile.getName()));
		}
		else {
			Assertions.assertThrowsExactly(FileNotFoundException.class, () -> rRepo.getFileProps(fileUID));
			Assertions.assertThrowsExactly(ContentsNotFoundException.class, () -> rRepo.getContentProps(correctFile.getName()));
		}



		if(zoningInfo.isRemote) {
			LFile localProps;
			try { localProps = lRepo.getFileProps(fileUID); }
			catch (FileNotFoundException e) { throw new RuntimeException(e); }

			RFile remoteProps;
			try { remoteProps = rRepo.getFileProps(fileUID); }
			catch (FileNotFoundException e) { throw new RuntimeException(e); }

			Log.w("Hyb.SYNCTEST", "Asserting synced files are the same: ");
			Log.w("Hyb.SYNCTEST", localProps.toString());
			Log.w("Hyb.SYNCTEST", remoteProps.toString());

			Assertions.assertEquals(correctFile.getName(), localProps.checksum);
			Assertions.assertEquals(correctFile.getName(), remoteProps.checksum);

			Assertions.assertEquals(correctAttr, localProps.userattr);
			Assertions.assertEquals(Utilities.computeChecksum(correctAttr.toString().getBytes()), localProps.attrhash);
			Assertions.assertEquals(correctAttr, remoteProps.userattr);
			Assertions.assertEquals(Utilities.computeChecksum(correctAttr.toString().getBytes()), remoteProps.attrhash);

			Assertions.assertEquals(localProps.checksum, remoteProps.checksum);
			Assertions.assertEquals(localProps.filesize, remoteProps.filesize);
			Assertions.assertEquals(localProps.userattr, remoteProps.userattr);
			Assertions.assertEquals(localProps.attrhash, remoteProps.attrhash);
			Assertions.assertEquals(localProps.changetime, remoteProps.changetime);
			Assertions.assertEquals(localProps.modifytime, remoteProps.modifytime);
			Assertions.assertEquals(localProps.createtime, remoteProps.createtime);
		}
	}




	private void changeZones(boolean isLocal, boolean isRemote) {
		ZoningWorker worker = launchZoningWorker(InstrumentationRegistry.getInstrumentation().getContext(),
				fileUID, isLocal, isRemote);
		ListenableWorker.Result result = worker.doWork();
		Assertions.assertInstanceOf(ListenableWorker.Result.Success.class, result);
	}

	private ZoningWorker launchZoningWorker(Context context, UUID fileUID, boolean isLocal, boolean isRemote) {
		Data inputData = new Data.Builder()
				.put("FILEUID", fileUID.toString())
				.put("LOCAL", String.valueOf(isLocal))
				.put("REMOTE", String.valueOf(isRemote))
				.build();

		return TestWorkerBuilder.from(context, ZoningWorker.class, executor).setInputData(inputData).build();
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
		assertZoningCorrect(false, true);

		//For Local:
		//A file's properties will always be on local if the file hasn't been deleted, so we shouldn't check for FileNotFound
		//A file's contents, even if the file is zoned to non-local, won't be deleted until cleanup in case another file
		// is using them. Therefore we shouldn't check for ContentsNotFound

		Assertions.assertDoesNotThrow(() -> {
			RFile props = rRepo.getFileProps(fileUID);
			rRepo.getContentProps(props.checksum);
		});
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


	private void assertZoningCorrect(boolean isLocal, boolean isRemote) {
		HZone zoningInfo = zoningDAO.get(fileUID);
		Assertions.assertNotNull(zoningInfo);
		Assertions.assertEquals(isLocal, zoningInfo.isLocal);
		Assertions.assertEquals(isRemote, zoningInfo.isRemote);
	}
	private void assertNoZoningData() {
		HZone zoningInfo = zoningDAO.get(fileUID);
		Assertions.assertNull(zoningInfo);
	}


	//---------------------------------------------------------------------------------------------


	private void setAttrLocal(JsonObject attr) throws FileNotFoundException {
		try {
			hAPI.lockLocal(fileUID);
			HFile localProps = hAPI.getFileProps(fileUID);
			hAPI.setAttributes(fileUID, attr, localProps.attrhash);
		}
		finally {
			hAPI.unlockLocal(fileUID);
		}
	}
	private void writeLocal(File file) throws FileNotFoundException {
		try {
			hAPI.lockLocal(fileUID);
			HFile localProps = hAPI.getFileProps(fileUID);
			hAPI.writeFile(fileUID, Uri.fromFile(file), file.getName(), localProps.checksum);
		} finally {
			hAPI.unlockLocal(fileUID);
		}
	}
	private LFile createLocalFile(File file) {
		fileUID = hAPI.createFile(lRepo.getCurrentAccount(), false, false);

		try {
			hAPI.lockLocal(fileUID);
			HFile localProps = hAPI.getFileProps(fileUID);
			hAPI.writeFile(fileUID, Uri.fromFile(file), file.getName(), localProps.checksum);
			Assertions.assertDoesNotThrow(() -> lRepo.getFileProps(fileUID));

			return lRepo.getFileProps(fileUID);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} finally {
			hAPI.unlockLocal(fileUID);
		}
	}



	private void setAttrRemote(JsonObject attr) throws FileNotFoundException, ConnectException {
		RFile remoteProps = rRepo.getFileProps(fileUID);
		String prevAttrHash = remoteProps.attrhash;
		remoteProps.userattr = attr;
		rRepo.putAttributeProps(remoteProps, prevAttrHash);
	}
	private void writeRemote(File file) throws FileNotFoundException, ConnectException, ContentsNotFoundException {
		putContentRemote(file);

		RFile remoteProps = rRepo.getFileProps(fileUID);
		String prevChecksum = remoteProps.checksum;
		remoteProps.checksum = file.getName();
		remoteProps.filesize = (int) file.length();
		rRepo.putContentProps(remoteProps, prevChecksum);
	}
	private RFile createRemoteFile(File file) {
		putContentRemote(file);

		//Create the file props on remote
		RFile newProps = new RFile(fileUID, rRepo.getCurrentAccount());
		newProps.checksum = file.getName();
		newProps.filesize = (int) file.length();
		try {
			newProps = rRepo.createFile(newProps);
		}
		catch (ContentsNotFoundException | FileAlreadyExistsException e) {
			throw new RuntimeException(e);
		} catch (ConnectException e) {
			throw new RuntimeException();
		}
		Assertions.assertDoesNotThrow(() -> rRepo.getFileProps(fileUID));

		return newProps;
	}
	private void putContentRemote(File file) {
		try {
			//If they don't already exist
			rRepo.getContentProps(file.getName());
		} catch (ContentsNotFoundException e) {
			try {
				//Put contents
				rRepo.uploadData(file.getName(), file);
			} catch (FileNotFoundException | ConnectException ex) {
				throw new RuntimeException(ex);
			}
		} catch (ConnectException e) {
			throw new RuntimeException(e);
		}
		Assertions.assertDoesNotThrow(() -> rRepo.getContentProps(file.getName()));
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
