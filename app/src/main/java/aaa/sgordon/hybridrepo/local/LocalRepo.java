package aaa.sgordon.hybridrepo.local;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.Utilities;
import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.hybrid.database.HybridHelpDatabase;
import aaa.sgordon.hybridrepo.hybrid.jobs.sync.Sync;
import aaa.sgordon.hybridrepo.local.database.LocalDatabase;
import aaa.sgordon.hybridrepo.local.types.LAccount;
import aaa.sgordon.hybridrepo.local.types.LContent;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.local.types.LJournal;

public class LocalRepo {
	private static final String TAG = "Hyb.Local";

	private static LocalRepo instance;
	private final LocalDatabase database;
	private LContentHelper contentHelper;

	private UUID currentAccount;
	private final Map<UUID, ReentrantLock> locks;

	public static LocalRepo getInstance() {
		if (instance == null)
			throw new IllegalStateException("LocalRepo is not initialized. Call initialize() first.");
		return instance;
	}

	public static synchronized void initialize(Context context) {
		LocalDatabase db = new LocalDatabase.DBBuilder().newInstance(context);
		if (instance == null) instance = new LocalRepo(db, context.getApplicationInfo().dataDir);
	}
	public static synchronized void initialize(LocalDatabase database, String storageDir) {
		if (instance == null) instance = new LocalRepo(database, storageDir);
	}
	private LocalRepo(LocalDatabase database, String storageDir) {
		locks = new HashMap<>();
		this.database = database;
		this.contentHelper = new LContentHelper(storageDir);
	}




	private boolean isOnMainThread() {
		return Thread.currentThread().equals(Looper.getMainLooper().getThread());
	}

	public UUID getCurrentAccount() {
		return currentAccount;
	}
	public void setAccount(@NonNull UUID accountUID) {
		this.currentAccount = accountUID;
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

		//TODO Check zoning (here or in HAPI)
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

		return fileProps;
	}


	//This is supposed to throw FileNotFound. We do NOT want another journal entry added
	// if the file doesn't exist, as that might mess up the next sync. Also it gives more info.
	public void deleteFileProps(@NonNull UUID fileUID) throws FileNotFoundException {
		Log.i(TAG, String.format("LOCAL DELETE FILE PROPS called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();
		ensureLockHeld(fileUID);

		//Ensure the file exists in the first place
		LFile existingFile = database.getFileDao().get(fileUID);
		if(existingFile == null)
			throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");

		//Remove the file
		database.getFileDao().delete(fileUID);
	}


	//---------------------------------------------------------------------------------------------
	// Contents
	//---------------------------------------------------------------------------------------------

	//TODO Check with Cleanup to decide if we should show content or if it's delete marked
	public LContent getContentProps(@NonNull String name) throws ContentsNotFoundException {
		Log.v(TAG, String.format("\nLOCAL GET CONTENT PROPS called with name='%s'", name));
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
		return contentHelper.getContentUri(name);
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
				LContent newContents = contentHelper.writeContents(name, contents);
				database.getContentDao().put(newContents);
				return newContents;
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
				LContent newContents = contentHelper.writeContents(name, source);
				database.getContentDao().put(newContents);
				return newContents;
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
		contentHelper.deleteContents(name);
	}


	//---------------------------------------------------------------------------------------------
	// Journal
	//---------------------------------------------------------------------------------------------

	public void putJournalEntry(@NonNull LJournal journal) {
		database.getJournalDao().insert(journal);
	}

	@NonNull
	public List<LJournal> getLatestChangesFor(int journalID, @Nullable UUID accountUID, @Nullable UUID[] fileUIDs) {
		Log.v(TAG, String.format("REMOTE JOURNAL GET LATEST called with journalID='%s', accountUID='%s'", journalID, accountUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(fileUIDs == null) fileUIDs = new UUID[0];
		if(accountUID == null && fileUIDs.length == 0)
			throw new IllegalArgumentException("AccountUID and/or 1+ FileUIDs are required!");

		if(fileUIDs.length == 0)
			return database.getJournalDao().getLatestChangeFor(accountUID, journalID);
		return database.getJournalDao().getLatestChangeFor(accountUID, journalID, fileUIDs);
	}


	@NonNull
	public List<LJournal> getAllChangesFor(int journalID, @Nullable UUID accountUID, @Nullable UUID[] fileUIDs) {
		Log.v(TAG, String.format("REMOTE JOURNAL GET ALL called with journalID='%s', accountUID='%s'", journalID, accountUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		if(fileUIDs == null) fileUIDs = new UUID[0];
		if(accountUID == null && fileUIDs.length == 0)
			throw new IllegalArgumentException("AccountUID and/or 1+ FileUIDs are required!");

		if(fileUIDs.length == 0)
			return database.getJournalDao().getAllChangesFor(accountUID, journalID);
		return database.getJournalDao().getAllChangesFor(accountUID, journalID, fileUIDs);
	}
}
