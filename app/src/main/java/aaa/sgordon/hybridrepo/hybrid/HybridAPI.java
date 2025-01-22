package aaa.sgordon.hybridrepo.hybrid;

import android.content.Context;
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
import aaa.sgordon.hybridrepo.hybrid.jobs.sync.SyncWorkers;
import aaa.sgordon.hybridrepo.hybrid.types.HFile;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.local.types.LContent;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;

public class HybridAPI {
	private static final String TAG = "Hyb";

	private final LocalRepo localRepo;
	private final RemoteRepo remoteRepo;





	public static HybridAPI getInstance() {
		return HybridAPI.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final HybridAPI INSTANCE = new HybridAPI();
	}
	private HybridAPI() {
		localRepo = LocalRepo.getInstance();
		remoteRepo = RemoteRepo.getInstance();
	}


	public void startListeningForChanges(@NonNull UUID accountUID) {
		SyncWorkers.JournalWatcher.enqueue(accountUID);
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


	public HFile createFile(@NonNull UUID accountUID, boolean isDir, boolean isLink) {
		UUID fileUID = UUID.randomUUID();
		LFile newFile = new LFile(fileUID, accountUID);

		newFile.isdir = isDir;
		newFile.islink = isLink;

		//Create blank file contents
		localRepo.writeContents(HFile.defaultChecksum, "".getBytes());

		newFile = localRepo.putFileProps(newFile, "", "");
		return HFile.fromLocalFile(newFile);
	}


	public void deleteFile(@NonNull UUID fileUID) {
		localRepo.deleteFileProps(fileUID);
	}


	//---------------------------------------------------------------------------------------------


	public HFile setAttributes(@NonNull UUID fileUID, @NonNull JsonObject attributes, @NonNull String prevAttrHash) throws FileNotFoundException {
		localRepo.ensureLockHeld(fileUID);

		LFile oldProps = localRepo.getFileProps(fileUID);
		LFile newProps = localRepo.getFileProps(fileUID);

		//Check that the current attribute checksum matches what we were given to ensure we won't be overwriting any data
		String currAttrHash = oldProps.attrhash;
		if(!Objects.equals(currAttrHash, prevAttrHash))
			throw new IllegalStateException(String.format("Cannot set attributes, checksums don't match! FileUID='%s'", fileUID));


		//Get the checksum of the attributes
		String newAttrHash;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(attributes.toString().getBytes());
			newAttrHash = Utilities.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }


		//And update the properties with the new info received
		newProps.userattr = attributes;
		newProps.attrhash = newAttrHash;
		newProps.changetime = Instant.now().getEpochSecond();



		return HFile.fromLocalFile( localRepo.putFileProps(newProps, newProps.checksum, oldProps.attrhash) );
	}



	public HFile writeFile(@NonNull UUID fileUID, @NonNull byte[] content, @NonNull String prevChecksum) throws FileNotFoundException {
		localRepo.ensureLockHeld(fileUID);

		//Check that the current file checksum matches what we were given to ensure we won't be overwriting any data
		HFile props = getFileProps(fileUID);
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

		//And update the properties with the new info received
		props.checksum = contentProps.checksum;
		props.filesize = contentProps.size;
		props.changetime = Instant.now().getEpochSecond();
		props.modifytime = Instant.now().getEpochSecond();

		return HFile.fromLocalFile(localRepo.putFileProps(props.toLocalFile(), currChecksum, props.attrhash));
	}


	public HFile writeFile(@NonNull UUID fileUID, @NonNull Uri content, @NonNull String checksum, @NonNull String prevChecksum) throws FileNotFoundException {
		localRepo.ensureLockHeld(fileUID);
		HFile props = getFileProps(fileUID);

		//Check that the current file checksum matches what we were given to ensure we won't be overwriting any data
		String currChecksum = props.checksum;
		if(!Objects.equals(currChecksum, prevChecksum))
			throw new IllegalStateException(String.format("Cannot write, checksums don't match! FileUID='%s'", fileUID));


		//Actually write the contents
		LContent contentProps = localRepo.writeContents(checksum, content);

		//And update the properties with the new info received
		props.checksum = contentProps.checksum;
		props.filesize = contentProps.size;
		props.changetime = Instant.now().getEpochSecond();
		props.modifytime = Instant.now().getEpochSecond();

		return HFile.fromLocalFile(localRepo.putFileProps(props.toLocalFile(), currChecksum, props.attrhash));
	}



	public HFile importFile(@NonNull Uri content, @NonNull String prevChecksum) throws IOException {
		UUID fileUID = UUID.randomUUID();

		//Write the source to a temp file so we can get the checksum
		Context context = MyApplication.getAppContext();
		File appCacheDir = context.getCacheDir();
		File tempFile = Files.createTempFile(appCacheDir.toPath(), fileUID.toString(), null).toFile();

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
		HFile fileProps = createFile(fileUID, false, false);

		//Update the file props with the updated content properties
		fileProps.checksum = contentProps.checksum;
		fileProps.filesize = contentProps.size;
		fileProps.changetime = Instant.now().getEpochSecond();
		fileProps.modifytime = Instant.now().getEpochSecond();

		return HFile.fromLocalFile( localRepo.putFileProps(fileProps.toLocalFile(), prevChecksum, fileProps.attrhash) );
	}
}
