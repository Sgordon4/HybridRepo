package aaa.sgordon.hybridrepo.hybrid;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.Utilities;
import aaa.sgordon.hybridrepo.hybrid.types.HFile;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.local.types.LContent;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;

public class HybridAPI {
	private static final String TAG = "Hyb";

	private final LocalRepo localRepo;
	private final RemoteRepo remoteRepo;

	private final Map<UUID, ReentrantLock> localLocks;



	public static HybridAPI getInstance() {
		return HybridAPI.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final HybridAPI INSTANCE = new HybridAPI();
	}
	private HybridAPI() {
		localLocks = new HashMap<>();

		localRepo = LocalRepo.getInstance();
		remoteRepo = RemoteRepo.getInstance();
	}



	public void lock(@NonNull UUID fileUID) {
		if(!localLocks.containsKey(fileUID))
			localLocks.put(fileUID, new ReentrantLock());

		localLocks.get(fileUID).lock();
	}
	public void unlock(@NonNull UUID fileUID) {
		if(!localLocks.containsKey(fileUID))
			return;

		localLocks.get(fileUID).unlock();
	}

	private void ensureLockHeld(@NonNull UUID fileUID) {
		ReentrantLock lock = localLocks.get(fileUID);
		if(lock == null || !lock.isHeldByCurrentThread()) throw new IllegalStateException("Cannot write, lock not held!");
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


	public void writeFile(@NonNull UUID fileUID, @NonNull byte[] content, @NonNull String prevChecksum) throws FileNotFoundException, IOException {
		ensureLockHeld(fileUID);

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

		//Actually write the data
		LContent contentProps = localRepo.writeContents(newChecksum, content);


		//And update the properties with the new info received
		props.checksum = contentProps.checksum;
		props.filesize = contentProps.size;
		props.changetime = Instant.now().getEpochSecond();
		props.modifytime = Instant.now().getEpochSecond();
		localRepo.putFileProps(props.toLocalFile(), currChecksum, props.attrhash);
	}


	public void setAttributes(@NonNull UUID fileUID, @NonNull Map<String, String> attributes, @NonNull String prevAttrHash) throws FileNotFoundException {
		ensureLockHeld(fileUID);

		//Check that the current attribute checksum matches what we were given to ensure we won't be overwriting any data
		HFile props = getFileProps(fileUID);
		String currAttrHash = props.attrhash;
		if(!Objects.equals(currAttrHash, prevAttrHash))
			throw new IllegalStateException(String.format("Cannot set attributes, checksums don't match! FileUID='%s'", fileUID));


		//Get the checksum of the attributes
		String newAttrHash;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(attributes.toString().getBytes());
			newAttrHash = Utilities.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }


		//And update the properties with the new info received
		props.userattr = new Gson().toJsonTree(attributes).getAsJsonObject();
		props.attrhash = newAttrHash;
		props.changetime = Instant.now().getEpochSecond();
		localRepo.putFileProps(props.toLocalFile(), props.checksum, prevAttrHash);
	}


	public void deleteFile(@NonNull UUID fileUID) {
		ensureLockHeld(fileUID);

		localRepo.deleteFileProps(fileUID);
	}



	public LFile createSpecialFile(boolean isDir, boolean isLink) {
		throw new RuntimeException("Stub!");
	}
	public LFile importFile(@NonNull UUID accountUID, @NonNull Uri source) throws IOException {
		UUID newFileUID = UUID.randomUUID();

		//In case the source is a web-url, save the content to a temp file for efficiency
		Context context = MyApplication.getAppContext();
		File appCacheDir = context.getCacheDir();
		File tempFile = new File(appCacheDir, newFileUID.toString());

		if(!tempFile.exists()) {
			Files.createDirectories(tempFile.toPath().getParent());
			Files.createFile(tempFile.toPath());
		}

		//Write the source to a temp file, getting the checksum while we do so
		String checksum;
		try (InputStream in = new URL(source.toString()).openStream();
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

		//Make new file props with the updated content properties
		LFile newFile = new LFile(newFileUID, accountUID);
		newFile.checksum = contentProps.checksum;
		newFile.filesize = contentProps.size;
		newFile.changetime = Instant.now().getEpochSecond();
		newFile.modifytime = Instant.now().getEpochSecond();


		//Write the new file props to local
		newFile = localRepo.putFileProps(newFile, "", "");
		return newFile;
	}
}
