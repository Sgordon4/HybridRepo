package aaa.sgordon.hybridrepo.hybrid;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import aaa.sgordon.hybridrepo.hybrid.types.HFile;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.local.types.LContent;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;

public class HybridAPI {
	private static final String TAG = "Hyb";

	private final LocalRepo localRepo;
	private final RemoteRepo remoteRepo;

	private final Map<UUID, ReentrantLock> AAAAlocalLocks;



	public static HybridAPI getInstance() {
		return HybridAPI.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final HybridAPI INSTANCE = new HybridAPI();
	}
	private HybridAPI() {
		AAAAlocalLocks = new HashMap<>();

		localRepo = LocalRepo.getInstance();
		remoteRepo = RemoteRepo.getInstance();
	}



	public void lock(@NonNull UUID fileUID) {
		if(!AAAAlocalLocks.containsKey(fileUID))
			AAAAlocalLocks.put(fileUID, new ReentrantLock());

		AAAAlocalLocks.get(fileUID).lock();
	}
	public void unlock(@NonNull UUID fileUID) {
		if(!AAAAlocalLocks.containsKey(fileUID))
			return;

		AAAAlocalLocks.get(fileUID).unlock();
	}

	private void ensureLockHeld(@NonNull UUID fileUID) {
		ReentrantLock lock = AAAAlocalLocks.get(fileUID);
		if(lock == null || !lock.isHeldByCurrentThread()) throw new IllegalStateException("Cannot write, lock not held!");
	}



	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------

	public HFile getFileProps(@NonNull UUID fileUID) throws FileNotFoundException {
		LFile local = localRepo.getFileProps(fileUID);
		return HFile.fromLocalFile(local);
	}

	//TODO In HAPI, check if the local file is server-only to decide if we want to hide the content
	// Wouldn't do this for syncHandler
	public Uri getFileContent(@NonNull UUID fileUID) throws FileNotFoundException, ContentsNotFoundException, ConnectException {
		//Grab the file properties, making sure the file exists at the same time. Throws FileNotFoundException if it does not.
		HFile props = getFileProps(fileUID);

		//Try to get the file contents from local. If they exist, return that.
		try { return localRepo.getContentUri(fileUID.toString()); }
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
		HFile currProps = getFileProps(fileUID);
		String currChecksum = currProps.checksum;
		String currAttrHash = currProps.attrhash;
		if(!Objects.equals(currChecksum, prevChecksum))
			throw new IllegalStateException(String.format("Cannot write, checksums don't match! FileUID='%s'", fileUID));

		//Actually write the data
		LContent contentProps = localRepo.writeContents(fileUID.toString(), content);

		currProps.checksum = contentProps.checksum;
		currProps.filesize = contentProps.size;
		currProps.changetime = Instant.now().getEpochSecond();

		//And update the properties with the new info received
		localRepo.putFileProps(currProps.toLocalFile(), currChecksum, currAttrHash);
	}
	public void writeFile(@NonNull UUID fileUID, @NonNull Uri content, @NonNull String prevChecksum) throws FileNotFoundException, IOException {
		ensureLockHeld(fileUID);

		//Check that the current file checksum matches what we were given to ensure we won't be overwriting any data
		HFile currProps = getFileProps(fileUID);
		String currChecksum = currProps.checksum;
		String currAttrHash = currProps.attrhash;
		if(!Objects.equals(currChecksum, prevChecksum))
			throw new IllegalStateException(String.format("Cannot write, checksums don't match! FileUID='%s'", fileUID));

		//Actually write the data
		LContent contentProps = localRepo.writeContents(fileUID.toString(), content);

		currProps.checksum = contentProps.checksum;
		currProps.filesize = contentProps.size;
		currProps.changetime = Instant.now().getEpochSecond();

		//And update the properties with the new info received
		localRepo.putFileProps(currProps.toLocalFile(), currChecksum, currAttrHash);
	}


	public void setAttributes(@NonNull UUID fileUID, @NonNull Map<String, String> attributes) {
		ensureLockHeld(fileUID);
		throw new RuntimeException("Stub!");
	}


	public void deleteFile(@NonNull UUID fileUID) {
		ensureLockHeld(fileUID);
		throw new RuntimeException("Stub!");
	}



	//Should this be import instead?
	public LFile createFile(@NonNull LFile newFile) {
		throw new RuntimeException("Stub!");
	}
}
