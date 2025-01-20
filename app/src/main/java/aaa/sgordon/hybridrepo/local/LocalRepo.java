package aaa.sgordon.hybridrepo.local;

import android.net.Uri;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.Utilities;
import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.local.types.LAccount;
import aaa.sgordon.hybridrepo.local.types.LContent;
import aaa.sgordon.hybridrepo.local.database.LocalDatabase;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.local.types.LJournal;

public class LocalRepo {
	private static final String TAG = "Hyb.Local";
	public final LocalDatabase database;


	public static LocalRepo getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final LocalRepo INSTANCE = new LocalRepo();
	}
	private LocalRepo() {
		database = new LocalDatabase.DBBuilder().newInstance( MyApplication.getAppContext() );
	}

	private boolean isOnMainThread() {
		return Thread.currentThread().equals(Looper.getMainLooper().getThread());
	}


	//---------------------------------------------------------------------------------------------
	// Account
	//---------------------------------------------------------------------------------------------

	public LAccount getAccountProps(@NonNull UUID accountUID) throws FileNotFoundException {
		Log.i(TAG, String.format("GET LOCAL ACCOUNT PROPS called with accountUID='%s'", accountUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		LAccount account = database.getAccountDao().loadByUID(accountUID);
		if(account == null) throw new FileNotFoundException("Account not found! ID: '"+accountUID);
		return account;
	}


	public void putAccountProps(@NonNull LAccount accountProps) {
		Log.i(TAG, String.format("PUT LOCAL ACCOUNT PROPS called with accountUID='%s'", accountProps.accountuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		database.getAccountDao().put(accountProps);
	}


	//---------------------------------------------------------------------------------------------
	// File
	//---------------------------------------------------------------------------------------------

	@NonNull
	public LFile getFileProps(@NonNull UUID fileUID) throws FileNotFoundException {
		Log.v(TAG, String.format("GET LOCAL FILE PROPS called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		LFile file = database.getFileDao().get(fileUID);
		if(file == null) throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
		return file;
	}


	public LFile putFileProps(@NonNull LFile fileProps, @NonNull String prevChecksum, @NonNull String prevAttrHash) throws IllegalStateException {
		Log.i(TAG, String.format("PUT LOCAL FILE PROPS called with fileUID='%s'", fileProps.fileuid));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();


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


		//Hash the user attributes
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(fileProps.userattr.toString().getBytes());
			fileProps.attrhash = Utilities.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }


		//Create/update the file
		database.getFileDao().put(fileProps);
		return fileProps;
	}


	public void deleteFileProps(@NonNull UUID fileUID) {
		Log.i(TAG, String.format("DELETE LOCAL FILE PROPS called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		database.getFileDao().delete(fileUID);
	}


	//---------------------------------------------------------------------------------------------
	// Contents
	//---------------------------------------------------------------------------------------------

	//TODO Check with Cleanup to decide if we should show content or if it's delete marked
	public LContent getContentProps(@NonNull String name) throws ContentsNotFoundException {
		Log.i(TAG, String.format("\nGET LOCAL CONTENT PROPS called with name='%s'", name));
		LContent props = database.getContentDao().get(name);
		if(props == null) throw new ContentsNotFoundException(name);
		return props;
	}


	@Nullable
	public Uri getContentUri(@NonNull String name) throws ContentsNotFoundException {
		Log.v(TAG, String.format("\nGET LOCAL CONTENT URI called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		//Throws a ContentsNotFound exception if the content properties don't exist
		getContentProps(name);

		//Now that we know the properties exist, return the content uri
		return LContentHelper.getContentUri(name);
	}


	public LContent writeContents(@NonNull String name, @NonNull byte[] contents) throws IOException {
		Log.v(TAG, String.format("\nWRITE LOCAL CONTENTS BYTE called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return LContentHelper.writeContents(name, contents);
	}

	public LContent writeContents(@NonNull String name, @NonNull Uri source) throws IOException {
		Log.v(TAG, String.format("\nWRITE LOCAL CONTENTS URI called with name='"+name+"'"));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();

		return LContentHelper.writeContents(name, source);
	}


	public void deleteContents(@NonNull String name) {
		Log.i(TAG, String.format("\nDELETE LOCAL CONTENTS called with name='"+name+"'"));
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
	public List<LJournal> getJournalEntriesAfter(int journalID) {
		Log.i(TAG, String.format("GET LOCAL JOURNALS AFTER ID called with journalID='%s'", journalID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();


		List<LJournal> journals = database.getJournalDao().loadAllAfterID(journalID);
		return journals != null ? journals : new ArrayList<>();
	}


	@NonNull
	public List<LJournal> getJournalEntriesForFile(@NonNull UUID fileUID) {
		Log.i(TAG, String.format("GET LOCAL JOURNALS FOR FILE called with fileUID='%s'", fileUID));
		if(isOnMainThread()) throw new NetworkOnMainThreadException();


		List<LJournal> journals = database.getJournalDao().loadAllByFileUID(fileUID);
		return journals != null ? journals : new ArrayList<>();
	}
}
