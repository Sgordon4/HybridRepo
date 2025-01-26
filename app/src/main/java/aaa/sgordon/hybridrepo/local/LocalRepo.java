package aaa.sgordon.hybridrepo.local;

import android.net.Uri;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.Utilities;
import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.local.database.LocalDatabase;
import aaa.sgordon.hybridrepo.local.types.LAccount;
import aaa.sgordon.hybridrepo.local.types.LContent;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.local.types.LJournal;

public class LocalRepo {
	private static final String TAG = "Hyb.Local";
	public final LocalDatabase database;

	private final Map<UUID, ReentrantLock> locks;


	public static LocalRepo getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final LocalRepo INSTANCE = new LocalRepo();
	}
	private LocalRepo() {
		locks = new HashMap<>();
		database = new LocalDatabase.DBBuilder().newInstance( MyApplication.getAppContext() );
	}

	private boolean isOnMainThread() {
		return Thread.currentThread().equals(Looper.getMainLooper().getThread());
	}


	public void lock(@NonNull UUID fileUID) {
		if(!locks.containsKey(fileUID))
			locks.put(fileUID, new ReentrantLock());

		locks.get(fileUID).lock();
	}
	public void unlock(@NonNull UUID fileUID) {
		if(!locks.containsKey(fileUID))
			return;

		locks.get(fileUID).unlock();
	}
	public void ensureLockHeld(@NonNull UUID fileUID) {
		ReentrantLock lock = locks.get(fileUID);
		if(lock == null || !lock.isHeldByCurrentThread()) throw new IllegalStateException("Cannot write, lock not held!");
	}


	//---------------------------------------------------------------------------------------------
	// Account
	//---------------------------------------------------------------------------------------------

	public LAccount getAccountProps(@NonNull UUID accountUID) throws FileNotFoundException {
		Log.i(TAG, String.format("LOCAL GET ACCOUNT PROPS called with accountUID='%s'", accountUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		LAccount account = database.getAccountDao().loadByUID(accountUID);
		if(account == null) throw new FileNotFoundException("Account not found! ID: '"+accountUID);
		return account;
	}


	public void putAccountProps(@NonNull LAccount accountProps) {
		Log.i(TAG, String.format("LOCAL PUT ACCOUNT PROPS called with accountUID='%s'", accountProps.accountuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		database.getAccountDao().put(accountProps);
	}


	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------

	@NonNull
	public LFile getFileProps(@NonNull UUID fileUID) throws FileNotFoundException {
		Log.v(TAG, String.format("LOCAL GET FILE PROPS called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		LFile file = database.getFileDao().get(fileUID);
		if(file == null) throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
		return file;
	}
	public boolean doesFileExist(@NonNull UUID fileUID) {
		Log.v(TAG, "LOCAL GET FILE PROPS EXIST called.");
		try {
			getFileProps(fileUID);
			return true;
		} catch (FileNotFoundException e) {
			return false;
		}
	}


	public LFile putFileProps(@NonNull LFile fileProps, @NonNull String prevChecksum, @NonNull String prevAttrHash) throws IllegalStateException {
		Log.i(TAG, String.format("LOCAL PUT FILE PROPS called with fileUID='%s'", fileProps.fileuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();
		ensureLockHeld(fileProps.fileuid);

		/*	//We may not have the contents on local if the file is server-only
		//Check if the repo is missing the file contents. If so, we can't commit the file changes
		try {
			getContentProps(fileProps.checksum);
		} catch (ContentsNotFoundException e) {
			throw new ContentsNotFoundException("Cannot put props, system is missing file contents! FileUID='" + fileProps.fileuid + "'");
		}
		 */


		//Make sure the hashes match if any were passed
		LFile oldFile = database.getFileDao().get(fileProps.fileuid);
		if(oldFile != null) {
			if(!Objects.equals(oldFile.checksum, prevChecksum))
				throw new IllegalStateException(String.format("Cannot put props, file contents hash doesn't match for fileUID='%s'", oldFile.fileuid));

			if(!Objects.equals(oldFile.attrhash, prevAttrHash))
				throw new IllegalStateException(String.format("Cannot put props, file attributes hash doesn't match for fileUID='%s'", oldFile.fileuid));
		}


		//Hash the user attributes in the updated props
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(fileProps.userattr.toString().getBytes());
			fileProps.attrhash = Utilities.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }


		//Create/update the file
		database.getFileDao().put(fileProps);

		//And add a journal entry
		LJournal journal = new LJournal(oldFile, fileProps);
		database.getJournalDao().insert(journal);
		return fileProps;
	}


	public void deleteFileProps(@NonNull UUID fileUID) {
		Log.i(TAG, String.format("LOCAL DELETE FILE PROPS called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();
		ensureLockHeld(fileUID);

		database.getFileDao().delete(fileUID);

		//And add a journal entry
		LJournal journal = new LJournal(oldFile, fileProps);
		database.getJournalDao().insert(journal);
	}


	//---------------------------------------------------------------------------------------------
	// Contents
	//---------------------------------------------------------------------------------------------

	//TODO Check with Cleanup to decide if we should show content or if it's delete marked
	public LContent getContentProps(@NonNull String name) throws ContentsNotFoundException {
		Log.i(TAG, String.format("\nLOCAL GET CONTENT PROPS called with name='%s'", name));
		LContent props = database.getContentDao().get(name);
		if(props == null) throw new ContentsNotFoundException(name);
		return props;
	}


	@NonNull
	public Uri getContentUri(@NonNull String name) throws ContentsNotFoundException {
		Log.v(TAG, String.format("\nLOCAL GET CONTENT URI called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		//Throws a ContentsNotFound exception if the content properties don't exist
		getContentProps(name);

		//Now that we know the properties exist, return the content uri
		return LContentHelper.getContentUri(name);
	}


	public LContent writeContents(@NonNull String name, @NonNull byte[] contents) {
		Log.v(TAG, String.format("\nLOCAL WRITE CONTENTS BYTE called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			//Just grab the properties if the content already exists
			return getContentProps(name);
		} catch (ContentsNotFoundException e) {
			//If the content doesn't already exist, write it
			try {
				return LContentHelper.writeContents(name, contents);
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	public LContent writeContents(@NonNull String name, @NonNull Uri source) {
		Log.v(TAG, String.format("\nLOCAL WRITE CONTENTS URI called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		try {
			//Just grab the properties if the content already exists
			return getContentProps(name);
		} catch (ContentsNotFoundException e) {
			//If the content doesn't already exist, write it
			try {
				return LContentHelper.writeContents(name, source);
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}


	public void deleteContents(@NonNull String name) {
		Log.i(TAG, String.format("\nLOCAL DELETE CONTENTS called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		//Remove the database entry first to avoid race conditions
		database.getContentDao().delete(name);

		//Now remove the content itself from disk
		LContentHelper.deleteContents(name);
	}


	//---------------------------------------------------------------------------------------------
	// Journal
	//---------------------------------------------------------------------------------------------

	@NonNull
	public List<LJournal> getLatestChangeFor(@NonNull UUID accountUID, int journalID, UUID... fileUIDs) {
		Log.v(TAG, String.format("REMOTE JOURNAL GET LATEST called with journalID='%s', accountUID='%s'", journalID, accountUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(fileUIDs.length == 0)
			return database.getJournalDao().getLatestChangeFor(accountUID, journalID);
		return database.getJournalDao().getLatestChangeFor(accountUID, journalID, fileUIDs);
	}


	@NonNull
	public List<LJournal> getAllChangesFor(@NonNull UUID accountUID, int journalID, UUID... fileUIDs) {
		Log.v(TAG, String.format("REMOTE JOURNAL GET ALL called with journalID='%s', accountUID='%s'", journalID, accountUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(fileUIDs.length == 0)
			return database.getJournalDao().getAllChangesFor(accountUID, journalID);
		return database.getJournalDao().getAllChangesFor(accountUID, journalID, fileUIDs);
	}
}
