package aaa.sgordon.hybridrepo.hybrid.jobs.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.List;
import java.util.UUID;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.hybrid.HybridAPI;
import aaa.sgordon.hybridrepo.hybrid.types.HFile;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.local.types.LJournal;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;
import aaa.sgordon.hybridrepo.remote.types.RFile;
import aaa.sgordon.hybridrepo.remote.types.RJournal;


//TODO If any new files show up on Remote, add their properties to local.

public class Sync {
	private final String TAG = "Hyb.Sync";
	private final boolean debug = true;

	private final SharedPreferences sharedPrefs;
	private int lastSyncLocalID;
	private int lastSyncRemoteID;

	private final HybridAPI hybridAPI;
	private final LocalRepo localRepo;
	private final RemoteRepo remoteRepo;


	private Sync() {
		hybridAPI = HybridAPI.getInstance();
		localRepo = LocalRepo.getInstance();
		remoteRepo = RemoteRepo.getInstance();


		sharedPrefs = MyApplication.getAppContext().getSharedPreferences("gallery.syncPointers", Context.MODE_PRIVATE);
		lastSyncLocalID = sharedPrefs.getInt("lastSyncLocal", 0);
		lastSyncRemoteID = sharedPrefs.getInt("lastSyncRemote", 0);
	}
	public static Sync getInstance() {
		return Sync.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final Sync INSTANCE = new Sync();
	}


	//---------------------------------------------------------------------------------------------


	//TODO This is not set up to work with multiple accounts atm. Need to store it as a Map(UUID, Integer).
	public void updateLastSyncLocal(int id) {
		//Gets rid of race conditions when several file updates come in at once. We just want the largest ID.
		if(id > lastSyncLocalID)
			lastSyncLocalID = id;

		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putInt("lastSyncLocal", lastSyncLocalID);
		editor.apply();
	}
	public void updateLastSyncRemote(int id) {
		if(id > lastSyncRemoteID)
			lastSyncRemoteID = id;

		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putInt("lastSyncRemote", lastSyncRemoteID);
		editor.apply();
	}

	public int getLastSyncLocal() {
		return lastSyncLocalID;
	}
	public int getLastSyncRemote() {
		return lastSyncRemoteID;
	}



	//---------------------------------------------------------------------------------------------


	public void sync(@NonNull UUID fileUID, int localSyncID, int remoteSyncID) throws FileNotFoundException, IllegalStateException, ConnectException {
		List<RJournal> remoteChanges = remoteRepo.getChangesForFileAfter(fileUID, localSyncID);
		RFile remoteProps = remoteRepo.getFileProps(fileUID);

		try {

			localRepo.lock(fileUID);
			List<LJournal> localChanges = localRepo.getChangesForFileAfter(fileUID, remoteSyncID);
			LFile localProps = localRepo.getFileProps(fileUID);

			//If neither repo has changes, we're done
			if(localChanges.isEmpty() && remoteChanges.isEmpty())
				return;
			if(HFile.fromLocalFile(localProps).equals(HFile.fromServerFile(remoteProps)))
				return;


			//If both repos have changes, we need to merge them
			if(!localChanges.isEmpty() && !remoteChanges.isEmpty()) {
				//Get the latest set of props for each repo
				//Walk through the list of changes, checking for deletes, and grab the starting hashes
				//Starting hashes may not match if remote was deleted and then re-uploaded from a different device
				//Make sure to sync attributes too. Also, one repo may have file changes while another has attr changes.
				//Watch out for newly deleted and un-deleted files

				//Loop through each set of changes, compiling them into a JsonObject
				//Compare, if differences then merge those
				//May be worth grabbing all journals for file, not just the recent ones


				//TODO Merging is very hard, and my brain is very smooth.
				// Therefore, I am setting it so the local file is ALWAYS written to remote instead of merging.
				// This IF condition should never occur for a one-device-per-account setup like we'll have initially,
				//  but this MUST be rectified for this to be respectable

				Log.w(TAG, "Repos were supposed to be merged, but merge is not implemented yet! FileUID='"+fileUID+"'");
				remoteChanges.clear();
			}


			//If only local has changes, we can write directly to remote. Essentially just copy everything over.
			if(!localChanges.isEmpty()) {
				if(debug) Log.d(TAG, "Local is the only repo with changes, persisting to Remote.");

				//If the file contents don't match, copy them over
				if(!localProps.checksum.equals(remoteProps.checksum)) {
					try {
						Uri localContent = localRepo.getContentUri(localProps.checksum);
						remoteRepo.uploadData(localProps.checksum, new File(localContent.getPath()));
					} catch (ContentsNotFoundException e) {
						Log.e(TAG, "Remote contents not found, something went wrong!");
					}
				}

				//Copy over all file properties, which happens to include the attributes we may need to copy
				try {
					remoteRepo.putFileProps(HFile.fromLocalFile(localProps).toServerFile(), localProps.checksum, localProps.attrhash);
				} catch (ContentsNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
			//If only remote has changes, we can write directly to local. Essentially just copy everything over.
			else if(!remoteChanges.isEmpty()) {
				if(debug) Log.d(TAG, "Remote is the only repo with changes, persisting to Local.");

				//If the file contents don't match, copy them over
				if(!localProps.checksum.equals(remoteProps.checksum)) {
					try {
						Uri remoteContent = remoteRepo.getContentDownloadUri(remoteProps.checksum);
						localRepo.writeContents(remoteProps.checksum, remoteContent);
					} catch (ContentsNotFoundException e) {
						Log.e(TAG, "Remote contents not found, something went wrong!");
					}
				}

				//Copy over all file properties, which happens to include the attributes we may need to copy
				localRepo.putFileProps(HFile.fromServerFile(remoteProps).toLocalFile(), localProps.checksum, localProps.attrhash);
			}
		} finally {
			localRepo.unlock(fileUID);
		}
	}



	//---------------------------------------------------------------------------------------------







}
