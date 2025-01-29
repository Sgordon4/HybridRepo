package aaa.sgordon.hybridrepo.hybrid.jobs.sync;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkQuery;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.local.types.LJournal;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;
import aaa.sgordon.hybridrepo.remote.types.RJournal;

public class SyncWorkers {


	public static class SyncWatcher extends Worker {
		private static final String TAG = "Hyb.Sync.Watcher";

		public SyncWatcher(@NonNull Context context, @NonNull WorkerParameters workerParams) {
			super(context, workerParams);
		}


		//Enqueue a Worker to check for changes and launch little workers to sync those changes
		public static void enqueue(@NonNull UUID accountUID) {
			Data.Builder data = new Data.Builder();
			data.putString("ACCOUNTUID", accountUID.toString());

			PeriodicWorkRequest worker = new PeriodicWorkRequest.Builder(SyncWorker.class, 30, TimeUnit.SECONDS)
					.setConstraints(new Constraints.Builder()
							.setRequiredNetworkType(NetworkType.UNMETERED)
							.setRequiresStorageNotLow(true)
							.build())
					.addTag(accountUID.toString()).addTag("swatch")
					.setInputData(data.build()).build();


			//Enqueue the sync job, keeping any currently running job operational
			WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
			workManager.enqueueUniquePeriodicWork("swatch_"+accountUID, ExistingPeriodicWorkPolicy.KEEP, worker);
		}


		public static void dequeue(@NonNull UUID fileuid) {
			WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
			workManager.cancelUniqueWork("swatch_"+fileuid);
		}



		@NonNull
		@Override
		public Result doWork() {
			String accountUIDString = getInputData().getString("ACCOUNTUID");
			assert accountUIDString != null;
			UUID accountUID = UUID.fromString(accountUIDString);

			Sync sync = Sync.getInstance();
			int lastSyncLocal = sync.getLastSyncLocal();
			int lastSyncRemote = sync.getLastSyncRemote();

			Log.i(TAG, "Journal Watcher looking for files to sync after "+lastSyncLocal+":"+lastSyncRemote+"for AccountUID='"+accountUID+"'");


			LocalRepo localRepo = LocalRepo.getInstance();
			RemoteRepo remoteRepo = RemoteRepo.getInstance();



			//Get all the files with changes since the journalIDs specified
			List<LJournal> localFilesChanged = localRepo.getLatestChangesFor(lastSyncLocal, accountUID, null);
			List<RJournal> remoteFilesChanged;
			try {
				remoteFilesChanged = remoteRepo.getLatestChangesFor(lastSyncRemote, accountUID, null);
			} catch (ConnectException e) {
				Log.w(TAG, "Journal Watcher requeueing due to connection issues!");
				return Result.retry();
			}


			//And grab the data we need from them
			Set<UUID> filesChanged = new HashSet<>();
			int newSyncLocal = lastSyncLocal;
			int newSyncRemote = lastSyncRemote;

			for(LJournal lJournal : localFilesChanged) {
				filesChanged.add(lJournal.fileuid);
				newSyncLocal = Math.max(newSyncLocal, lJournal.journalid);
			}
			for(RJournal rJournal : remoteFilesChanged) {
				filesChanged.add(rJournal.fileuid);
				newSyncRemote = Math.max(newSyncRemote, rJournal.journalid);
			}


			//For each file with changes, start a worker to sync it
			for(UUID fileUID : filesChanged) {
				SyncWorker.enqueue(fileUID, lastSyncLocal, lastSyncRemote);
			}

			//And update our journalID trackers
			sync.updateLastSyncLocal(newSyncLocal);
			sync.updateLastSyncRemote(newSyncRemote);

			return Result.success();
		}
	}


	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------


	public static class SyncWorker extends Worker {
		private static final String TAG = "Hyb.Sync.Worker";

		public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
			super(context, workerParams);
		}


		//Enqueue a worker to sync a file
		public static void enqueue(@NonNull UUID fileUID, int localSyncID, int remoteSyncID) {
			Data.Builder data = new Data.Builder();
			data.putString("FILEUID", fileUID.toString());
			data.putString("LOCAL_JID", String.valueOf(localSyncID));
			data.putString("REMOTE_JID", String.valueOf(remoteSyncID));

			OneTimeWorkRequest worker = new OneTimeWorkRequest.Builder(SyncWorker.class)
					.setConstraints(new Constraints.Builder()
							.setRequiredNetworkType(NetworkType.UNMETERED)
							.setRequiresStorageNotLow(true)
							.build())
					.addTag(fileUID.toString()).addTag("SYNC")
					.setInputData(data.build()).build();


			WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());

			//If there is any enqueued (NOT RUNNING) sync job for this fileUID, do nothing
			try {
				WorkQuery workQuery = WorkQuery.Builder
						.fromUniqueWorkNames(Collections.singletonList("sync_"+fileUID))
						.addStates(Collections.singletonList(WorkInfo.State.ENQUEUED))
						.build();
				List<WorkInfo> existingInfo = workManager.getWorkInfos(workQuery).get();

				if(!existingInfo.isEmpty())
					return;
			} catch (ExecutionException | InterruptedException e) { throw new RuntimeException(e); }

			//Enqueue the sync job, keeping any currently running job operational
			workManager.enqueueUniqueWork("sync_"+fileUID, ExistingWorkPolicy.APPEND_OR_REPLACE, worker);
		}


		public static void dequeue(@NonNull UUID fileuid) {
			WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
			workManager.cancelUniqueWork("sync_"+fileuid);
		}



		@NonNull
		@Override
		public Result doWork() {
			String fileUIDString = getInputData().getString("FILEUID");
			assert fileUIDString != null;
			UUID fileUID = UUID.fromString(fileUIDString);

			String localString = getInputData().getString("LOCAL_JID");
			assert localString != null;
			int localSyncID = Integer.parseInt(localString);

			String remoteString = getInputData().getString("REMOTE_JID");
			assert remoteString != null;
			int remoteSyncID = Integer.parseInt(remoteString);

			Log.i(TAG, "SyncWorker syncing for FileUID='"+fileUID+"'");



			try {
				Pair<Integer, Integer> syncIDs = Sync.getInstance().sync(fileUID, localSyncID, remoteSyncID);

				Data.Builder data = new Data.Builder();
				data.putString("LOCAL_JID", String.valueOf(syncIDs.first));
				data.putString("REMOTE_JID", String.valueOf(syncIDs.second));
				return Result.success(data.build());
			}
			//If the sync fails due to another update happening before we could finish the sync, requeue it for later
			catch (IllegalStateException e) {
				Log.w(TAG, "SyncWorker requeueing due to conflicting update!");
				return Result.retry();
			}
			//If the sync fails due to server connection issues, requeue it for later
			catch (ConnectException e) {
				Log.w(TAG, "SyncWorker requeueing due to connection issues!");
				return Result.retry();
			}
		}
	}
}
