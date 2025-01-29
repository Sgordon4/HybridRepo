package aaa.sgordon.hybridrepo.hybrid.jobs.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.hybrid.database.HZone;
import aaa.sgordon.hybridrepo.hybrid.database.HZoningDAO;
import aaa.sgordon.hybridrepo.hybrid.database.HybridHelpDatabase;
import aaa.sgordon.hybridrepo.hybrid.types.HFile;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.local.database.LocalDatabase;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.local.types.LJournal;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;
import aaa.sgordon.hybridrepo.remote.types.RFile;
import aaa.sgordon.hybridrepo.remote.types.RJournal;

public class Sync {
	private final String TAG = "Hyb.Sync";
	private final boolean debug = true;

	private final SharedPreferences sharedPrefs;
	private int lastSyncLocalID;
	private int lastSyncRemoteID;

	private final LocalRepo localRepo;
	private final RemoteRepo remoteRepo;


	private static Sync instance;
	public final HZoningDAO zoningDAO;
	public static Sync getInstance() {
		if (instance == null)
			throw new IllegalStateException("LocalRepo is not initialized. Call initialize() first.");
		return instance;
	}

	public static synchronized void initialize(Context context) {
		if (instance == null) {
			HybridHelpDatabase hdb = new HybridHelpDatabase.DBBuilder().newInstance(context);
			instance = new Sync(hdb, context);
		}
	}
	public static synchronized void initialize(HybridHelpDatabase database, Context context) {
		if (instance == null) instance = new Sync(database, context);
	}
	private Sync(HybridHelpDatabase database, Context context) {
		localRepo = LocalRepo.getInstance();
		remoteRepo = RemoteRepo.getInstance();
		zoningDAO = database.getZoningDao();

		sharedPrefs = context.getSharedPreferences("gallery.syncPointers", Context.MODE_PRIVATE);
		lastSyncLocalID = sharedPrefs.getInt("lastSyncLocal", 0);
		lastSyncRemoteID = sharedPrefs.getInt("lastSyncRemote", 0);
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

	/* Pseudocode:

	If local latest is fromSync, ignore.
	If local latest is delete, delete from remote and remove zoning.
	If remote latest is delete, delete from local and remove zoning.

	If Local has changes & Remote has changes, merge :(.
	If Local has NO changes & Remote has changes, attempt to push to local.
	If Local has changes & Remote has NO changes, attempt to push to remote.

	Merge sucks.

	If push to local and local doesn't exist, create local and create zoning.
	If push to local and local does exist, update whatever changed and update zoning.

	If push to remote and remote doesn't exist, DO NOTHING. Remote is added through zoning.
	If push to remote and remote exists, update whatever changed and update zoning.
	*/


	//Returns the highest journalIDs it found for Local::Remote
	public Pair<Integer, Integer> sync(@NonNull UUID fileUID, int localSyncID, int remoteSyncID) throws IllegalStateException, ConnectException {
		try {
			localRepo.lock(fileUID);
			localRepo.getFileProps(fileUID);
		} catch (FileNotFoundException e) {
			//If local doesn't exist, remove any somehow un-deleted zoning data. Just in case.
			zoningDAO.delete(fileUID);
		} finally {
			localRepo.unlock(fileUID);
		}

		//Note: When grabbing journals from remote, changes originating from this device are excluded!
		List<RJournal> remoteLatestChange = remoteRepo.getLatestChangesFor(remoteSyncID, null, new UUID[]{fileUID});
		boolean remoteHasChanges = !remoteLatestChange.isEmpty();
		List<LJournal> localLatestChange = localRepo.getLatestChangesFor(localSyncID, null, new UUID[]{fileUID});
		boolean localHasChanges = !localLatestChange.isEmpty() && !localLatestChange.get(0).fromSync;

		//If neither repo has changes, we're done
		if(!localHasChanges && !remoteHasChanges) {
			return new Pair<>(localSyncID, remoteSyncID);
		}

		if(remoteHasChanges)
			remoteSyncID = remoteLatestChange.get(0).journalid;
		if(localHasChanges)
			localSyncID = localLatestChange.get(0).journalid;


		//If the latest remote journal has "isdeleted=true"...
		//Note: Check remote before local to ensure deletion if errors occur.
		if(remoteHasChanges && remoteLatestChange.get(0).changes.has("isdeleted") &&
				remoteLatestChange.get(0).changes.get("isdeleted").getAsBoolean()) {
			try {
				localRepo.lock(fileUID);

				//Remove the file from local completely
				localRepo.deleteFileProps(fileUID);

				//Add a journal entry
				JsonObject changes = new JsonObject();
				changes.addProperty("isdeleted", true);
				LJournal journal = new LJournal(fileUID, remoteLatestChange.get(0).accountuid, changes);
				journal.fromSync = true;
				localRepo.putJournalEntry(journal);
			}
			catch (FileNotFoundException e) {
				//Do nothing
			} finally {
				//Remove zoning information for the file
				zoningDAO.delete(fileUID);

				localRepo.unlock(fileUID);
			}
			return new Pair<>(localSyncID, remoteSyncID);
		}

		//If the latest local journal has "isdeleted=true"...
		if(localHasChanges && localLatestChange.get(0).changes.has("isdeleted") &&
				localLatestChange.get(0).changes.get("isdeleted").getAsBoolean()) {
			try {
				//Remove the file from Remote
				remoteRepo.deleteFileProps(fileUID);
			} catch (FileNotFoundException e) {
				//Do nothing
			}
			return new Pair<>(localSyncID, remoteSyncID);
		}

		//----------------------------------------------------------------


		//If both repos have changes, we need to merge
		else if(localHasChanges && remoteHasChanges) {
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
			// but this MUST be rectified for this to be respectable

			Log.w(TAG, "Repos were supposed to be merged, but merge is not implemented yet! FileUID='"+fileUID+"'");
			remoteHasChanges = false;
		}




		//If only local has changes, attempt to push to remote. Essentially just copy everything over.
		//We already checked deletes, so these must be actual changes.
		if(localHasChanges) {
			try {
				//If remote doesn't exist, we don't want to add it. Remote is added through zoning.
				RFile remoteProps = remoteRepo.getFileProps(fileUID);
				//If local doesn't exist, it's highly likely it was *just* deleted. Wait for next sync.
				LFile localProps = localRepo.getFileProps(fileUID);


				//If the file contents don't match, copy them over
				if(!localProps.checksum.equals(remoteProps.checksum)) {
					Uri localContent = localRepo.getContentUri(localProps.checksum);
					remoteRepo.uploadData(localProps.checksum, new File(localContent.getPath()));
					remoteRepo.putContentProps(HFile.toRemoteFile(localProps), remoteProps.checksum);
				}
				//If the user attributes don't match, copy them over
				if(!localProps.attrhash.equals(remoteProps.attrhash))
					remoteRepo.putAttributeProps(HFile.toRemoteFile(localProps), remoteProps.attrhash);


				//DO NOT update zoning info. A local update could be content stored for upload to remote,
				// or it could even just be an attribute change. Only zoning should update isLocal.
			}
			catch (ContentsNotFoundException e) {
				//The content should have been either temp written content or permanent.
				//Log this, and now is a good time to update the zoning data in case it was corrupted.
				//This shouldn't happen, but we can smooth it out.
				Log.e(TAG, "Local contents not found when syncing to remote, something went wrong!");
				HZone zoningInfo = new HZone(fileUID, false, true);
				zoningDAO.put(zoningInfo);
			}
			catch (FileNotFoundException e) {
				//This is fine. Nothing to sync here if a file is missing, so we're done. DO NOT update zoning.
			}

			return new Pair<>(localSyncID, remoteSyncID);
		}


		//If only remote has changes, attempt to push to local. Essentially just copy everything over.
		//We already checked deletes, so these must be actual changes.
		else {
			RFile remoteProps;
			try { remoteProps = remoteRepo.getFileProps(fileUID); }
			catch (FileNotFoundException e) {
				//If remote doesn't exist but the latest remote journal wasn't a delete record,
				// it's highly likely it was *just* deleted. Wait for next sync. DO NOT update zoning.
				return new Pair<>(localSyncID, remoteSyncID);
			}

			try {
				localRepo.lock(fileUID);

				LFile localProps;
				try { localProps = localRepo.getFileProps(fileUID); }
				catch (FileNotFoundException e) {
					//If local doesn't exist, we want to add it, as this is effectively a new file coming from remote.
					localProps = HFile.toLocalFile(remoteProps);	//Just use the remote props as a stand-in.
					zoningDAO.delete(fileUID);						//Reset zoning in case of bad data
				}
				HZone zoningInfo = zoningDAO.get(fileUID);
				if(zoningInfo == null) zoningInfo = new HZone(fileUID, false, true);


				//If the file contents should be on local and they don't match, copy them over
				if(zoningInfo.isLocal && !remoteProps.checksum.equals(localProps.checksum)) {
					Uri remoteContent = remoteRepo.getContentDownloadUri(remoteProps.checksum);
					localRepo.writeContents(remoteProps.checksum, remoteContent);
				}

				//Copy over all file properties, which happen to include the attributes we may need to copy
				localRepo.putFileProps(HFile.toLocalFile(remoteProps), localProps.checksum, localProps.attrhash);

				//Create/update the zoning info
				zoningInfo.isRemote = true;
				zoningDAO.put(zoningInfo);

			} catch (ContentsNotFoundException e) {
				//Not really sure what to do here... This really shouldn't happen...
				Log.wtf(TAG, "Remote contents not found, system is hosed! Joever! Donezo! FileUID='"+fileUID+"'");
				throw new RuntimeException(e);
			} finally {
				localRepo.unlock(fileUID);
			}

			return new Pair<>(localSyncID, remoteSyncID);
		}
	}
}
