package aaa.sgordon.hybridrepo.hybrid;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.Utilities;
import aaa.sgordon.hybridrepo.hybrid.database.HZone;
import aaa.sgordon.hybridrepo.hybrid.database.HZoningDAO;
import aaa.sgordon.hybridrepo.hybrid.database.HybridHelpDatabase;
import aaa.sgordon.hybridrepo.hybrid.jobs.sync.Sync;
import aaa.sgordon.hybridrepo.hybrid.jobs.sync.SyncWorkers;
import aaa.sgordon.hybridrepo.hybrid.types.HFile;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.local.types.LContent;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.local.types.LJournal;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;


/*
TODO I don't want to expose HFile to the users of this API.
 Go through this and make sure no methods return/require HFile or similar things to operate.
 E.g. setAttributes() should require a JsonObject, or a Map, and return attrHash.
 write() should require the content, and should return checksum and size somehow.
 Maybe even add another function to getSize(), and have write just return checksum.
 '
 With that said, does HFile even need to exist?
 As static converter functions sure, but idk if we should keep the whole class.
*/

public class HybridAPI {
	private static final String TAG = "Hyb";

	private final LocalRepo localRepo;
	private final RemoteRepo remoteRepo;

	private final Sync sync;

	private UUID currentAccount = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");





	public static HybridAPI getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final HybridAPI INSTANCE = new HybridAPI();
	}
	private HybridAPI() {
		localRepo = LocalRepo.getInstance();
		remoteRepo = RemoteRepo.getInstance();

		//Stand-in for the login system
		localRepo.setAccount(currentAccount);
		remoteRepo.setAccount(currentAccount);

		Sync.initialize(MyApplication.getAppContext());
		sync = Sync.getInstance();
	}


	public void startListeningForChanges(@NonNull UUID accountUID) {
		SyncWorkers.SyncWatcher.enqueue(accountUID);
	}




	public void lockLocal(@NonNull UUID fileUID) {
		localRepo.lock(fileUID);
	}
	public void unlockLocal(@NonNull UUID fileUID) {
		localRepo.unlock(fileUID);
	}


	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------

	//TODO Should this exist as public? Or at all?
	public HFile getFileProps(@NonNull UUID fileUID) throws FileNotFoundException {
		LFile local = localRepo.getFileProps(fileUID);
		return HFile.fromLocalFile(local);
	}


	public Uri getFileContent(@NonNull UUID fileUID) throws FileNotFoundException, ContentsNotFoundException, ConnectException {
		//Grab the file properties, which also makes sure the file exists
		HFile props = getFileProps(fileUID);

		//Try to get the file contents from local. If they exist, return that.
		try { return localRepo.getContentUri(props.checksum); }
		catch (ContentsNotFoundException ignored) { }

		//If the contents don't exist locally, try to get it from the server.
		try { return remoteRepo.getContentDownloadUri(props.checksum); }
		catch (ContentsNotFoundException ignored) { }

		//If the contents don't exist in either, throw an exception
		throw new ContentsNotFoundException(String.format("Contents not found for fileUID='%s'", fileUID));
	}


	//Returns the FileUID of the new file
	public UUID createFile(@NonNull UUID accountUID, boolean isDir, boolean isLink) {
		//Note: Locking for this file doesn't really matter, since nothing can know about it yet
		UUID fileUID = UUID.randomUUID();
		LFile newFile = new LFile(fileUID, accountUID);

		newFile.isdir = isDir;
		newFile.islink = isLink;

		//Create blank file contents
		localRepo.writeContents(HFile.defaultChecksum, "".getBytes());

		try {
			lockLocal(fileUID);
			//Put the properties themselves
			newFile = localRepo.putFileProps(newFile, "", "");
		}
		finally {
			unlockLocal(fileUID);
		}


		//Add a journal entry
		JsonObject changes = new JsonObject();
		changes.addProperty("checksum", newFile.checksum);
		changes.addProperty("attrhash", newFile.attrhash);
		changes.addProperty("changetime", newFile.changetime);
		changes.addProperty("createtime", newFile.createtime);
		LJournal journal = new LJournal(fileUID, accountUID, changes);
		localRepo.putJournalEntry(journal);

		//Set zoning information.
		HZone newZoningInfo = new HZone(fileUID, true, false);
		Sync.getInstance().zoningDAO.put(newZoningInfo);

		return newFile.fileuid;
	}


	public void deleteFile(@NonNull UUID fileUID) throws FileNotFoundException {
		localRepo.deleteFileProps(fileUID);

		//Add a journal entry
		JsonObject changes = new JsonObject();
		changes.addProperty("isdeleted", true);
		LJournal journal = new LJournal(fileUID, currentAccount, changes);
		localRepo.putJournalEntry(journal);

		//Remove zoning information
		Sync.getInstance().zoningDAO.delete(fileUID);
		//All we need to do is delete the file properties and zoning information here in local.
		//Cleanup will remove this file's contents if they're not being used by another file.
		//If a remote file exists, Sync will now handle the delete from Remote using the new journal entry.
	}


	//---------------------------------------------------------------------------------------------


	//Returns SHA-256 hash of the given attributes. Referenced as attrHash in file properties
	public String setAttributes(@NonNull UUID fileUID, @NonNull JsonObject attributes, @NonNull String prevAttrHash) throws FileNotFoundException {
		localRepo.ensureLockHeld(fileUID);

		LFile props = localRepo.getFileProps(fileUID);

		//Check that the current attribute checksum matches what we were given to ensure we won't be overwriting any data
		String currAttrHash = props.attrhash;
		if(!Objects.equals(currAttrHash, prevAttrHash))
			throw new IllegalStateException(String.format("Cannot set attributes, attrHashes don't match! FileUID='%s'", fileUID));


		//Get the checksum of the attributes
		String newAttrHash;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(attributes.toString().getBytes());
			newAttrHash = Utilities.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }


		//Update the properties with the new info received
		props.userattr = attributes;
		props.attrhash = newAttrHash;
		props.changetime = Instant.now().getEpochSecond();
		props = localRepo.putFileProps(props, props.checksum, currAttrHash);

		//Add a journal entry
		JsonObject changes = new JsonObject();
		changes.addProperty("attrhash", props.checksum);
		changes.addProperty("changetime", props.changetime);
		LJournal journal = new LJournal(fileUID, currentAccount, changes);
		localRepo.putJournalEntry(journal);

		return props.attrhash;
	}



	//Returns SHA-256 checksum of the given contents. Referenced as checksum in the file properties
	public String writeFile(@NonNull UUID fileUID, @NonNull byte[] content, @NonNull String prevChecksum) throws FileNotFoundException {
		localRepo.ensureLockHeld(fileUID);

		//Check that the current file checksum matches what we were given to ensure we won't be overwriting any data
		LFile props = localRepo.getFileProps(fileUID);
		String currChecksum = props.checksum;
		if(!Objects.equals(currChecksum, prevChecksum))
			throw new IllegalStateException(String.format("Cannot write, checksums don't match! FileUID='%s'", fileUID));


		//Get the checksum of the contents
		String newChecksum;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
			newChecksum = Utilities.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }


		//Actually write the contents
		LContent contentProps = localRepo.writeContents(newChecksum, content);

		//Update the properties with the new info received
		props.checksum = contentProps.checksum;
		props.filesize = contentProps.size;
		props.changetime = Instant.now().getEpochSecond();
		props.modifytime = Instant.now().getEpochSecond();
		props = localRepo.putFileProps(props, currChecksum, props.attrhash);

		//Add a journal entry
		JsonObject changes = new JsonObject();
		changes.addProperty("checksum", props.checksum);
		changes.addProperty("changetime", props.changetime);
		changes.addProperty("modifytime", props.createtime);
		LJournal journal = new LJournal(fileUID, currentAccount, changes);
		localRepo.putJournalEntry(journal);

		return props.checksum;
	}


	//Returns SHA-256 checksum of the given contents. Referenced as checksum in the file properties
	public String writeFile(@NonNull UUID fileUID, @NonNull Uri content, @NonNull String checksum, @NonNull String prevChecksum) throws FileNotFoundException {
		localRepo.ensureLockHeld(fileUID);
		LFile props = localRepo.getFileProps(fileUID);

		//Check that the current file checksum matches what we were given to ensure we won't be overwriting any data
		String currChecksum = props.checksum;
		if(!Objects.equals(currChecksum, prevChecksum))
			throw new IllegalStateException(String.format("Cannot write, checksums don't match! FileUID='%s'", fileUID));


		//Actually write the contents
		LContent contentProps = localRepo.writeContents(checksum, content);

		//Update the properties with the new info received
		props.checksum = contentProps.checksum;
		props.filesize = contentProps.size;
		props.changetime = Instant.now().getEpochSecond();
		props.modifytime = Instant.now().getEpochSecond();
		props = localRepo.putFileProps(props, currChecksum, props.attrhash);

		//Add a journal entry
		JsonObject changes = new JsonObject();
		changes.addProperty("checksum", props.checksum);
		changes.addProperty("changetime", props.changetime);
		changes.addProperty("modifytime", props.createtime);
		LJournal journal = new LJournal(fileUID, currentAccount, changes);
		localRepo.putJournalEntry(journal);

		return props.checksum;
	}



	//Returns new FileUID for imported file
	public UUID importFile(@NonNull Uri content, @NonNull String prevChecksum) throws IOException {
		//Write the source to a temp file so we can get the checksum
		File appCacheDir = MyApplication.getAppContext().getCacheDir();
		File tempFile = Files.createTempFile(appCacheDir.toPath(), UUID.randomUUID().toString(), null).toFile();

		String checksum;
		try (InputStream in = new URL(content.toString()).openStream();
			 FileOutputStream out = new FileOutputStream(tempFile);
			 DigestOutputStream dos = new DigestOutputStream(out, MessageDigest.getInstance("SHA-256"))) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
				dos.write(dataBuffer, 0, bytesRead);
			}

			checksum = Utilities.bytesToHex(dos.getMessageDigest().digest());
		}
		catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }


		//Write the temp file contents to the content repo
		LContent contentProps = localRepo.writeContents(checksum, Uri.fromFile(tempFile));

		//Create a new file
		UUID fileUID = createFile(currentAccount, false, false);

		//Update the file props with the updated content properties
		LFile fileProps = localRepo.getFileProps(fileUID);
		fileProps.checksum = contentProps.checksum;
		fileProps.filesize = contentProps.size;
		fileProps.changetime = Instant.now().getEpochSecond();
		fileProps.modifytime = Instant.now().getEpochSecond();
		LFile newFile = localRepo.putFileProps(fileProps, prevChecksum, fileProps.attrhash);

		//Add another journal entry after the create entry
		JsonObject changes = new JsonObject();
		changes.addProperty("checksum", newFile.checksum);
		changes.addProperty("changetime", newFile.changetime);
		changes.addProperty("modifytime", newFile.createtime);
		LJournal journal = new LJournal(fileUID, currentAccount, changes);
		localRepo.putJournalEntry(journal);

		return newFile.fileuid;
	}

	public void exportFile(@NonNull UUID fileUID, @NonNull Uri destination) {
		throw new RuntimeException("Stub!");
	}
}
