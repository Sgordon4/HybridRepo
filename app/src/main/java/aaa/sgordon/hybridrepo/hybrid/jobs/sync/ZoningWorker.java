package aaa.sgordon.hybridrepo.hybrid.jobs.sync;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.FileAlreadyExistsException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.hybrid.database.HZone;
import aaa.sgordon.hybridrepo.hybrid.database.HZoningDAO;
import aaa.sgordon.hybridrepo.hybrid.types.HFile;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;
import aaa.sgordon.hybridrepo.remote.types.RFile;

public class ZoningWorker extends Worker {
	private static final String TAG = "Hyb.Zone.Worker";

	public ZoningWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
	}


	//Enqueue a worker to change a file's zones
	public static OneTimeWorkRequest enqueue(@NonNull UUID fileUID, boolean shouldBeLocal, boolean shouldBeRemote) throws IllegalArgumentException, IllegalStateException {
		if(!shouldBeLocal && !shouldBeRemote)
			throw new IllegalArgumentException("Cannot set both zones to false! FileUID='"+fileUID+"'");

		HZoningDAO dao = Sync.getInstance().zoningDAO;
		HZone currentZones = dao.get(fileUID);
		if(currentZones == null) throw new IllegalStateException("Zoning data not found for fileUID='"+fileUID+"'");

		if(!currentZones.isLocal && !currentZones.isRemote) {
			//TODO Attempt a repair. Maybe also do that in cleanup.
			Log.wtf(TAG, "Zoning machine broke. FileUID='"+fileUID+"'");
			throw new IllegalStateException("Zoning machine broke. FileUID='"+fileUID+"'");
		}

		//Don't do any other zoning compatibility changes here, do them in doWork for the most up-do-date data


		Constraints.Builder constraints = new Constraints.Builder();

		//If we are downloading data to Local or uploading data to Remote, require an unmetered connection
		if((!currentZones.isLocal && shouldBeLocal) || (!currentZones.isRemote && shouldBeRemote))
			constraints.setRequiredNetworkType(NetworkType.UNMETERED);

		//If we're copying  data onto device, require storage not to be low
		if(!currentZones.isLocal && shouldBeLocal)
			constraints.setRequiresStorageNotLow(true);


		Data.Builder data = new Data.Builder();
		data.putString("FILEUID", fileUID.toString());
		data.putBoolean("LOCAL", shouldBeLocal);
		data.putBoolean("REMOTE", shouldBeRemote);

		OneTimeWorkRequest worker = new OneTimeWorkRequest.Builder(ZoningWorker.class)
				.setConstraints(constraints.build())
				.setInputData(data.build())
				.setInitialDelay(3, TimeUnit.SECONDS)	//User is most likely to requeue requests immediately after enqueuing one
				.addTag(fileUID.toString()).addTag("ZONING")
				.addTag("LOCAL_"+shouldBeLocal)
				.addTag("REMOTE_"+shouldBeRemote)
				.build();

		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
		workManager.enqueueUniqueWork("zoning_"+fileUID, ExistingWorkPolicy.REPLACE, worker);

		return worker;
	}

	public static void dequeue(@NonNull UUID fileuid) {
		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
		workManager.cancelUniqueWork("zoning_"+fileuid);
	}




	@NonNull
	@Override
	public Result doWork() {
		String fileUIDString = getInputData().getString("FILEUID");
		assert fileUIDString != null;
		UUID fileUID = UUID.fromString(fileUIDString);

		String localString = getInputData().getString("LOCAL");
		assert localString != null;
		boolean shouldBeLocal = Boolean.parseBoolean(localString);

		String remoteString = getInputData().getString("REMOTE");
		assert remoteString != null;
		boolean shouldBeRemote = Boolean.parseBoolean(remoteString);


		HZoningDAO dao =  Sync.getInstance().zoningDAO;
		HZone currentZones = dao.get(fileUID);
		if(currentZones == null) {
			Log.e(TAG, "Zoning data not found for fileUID='"+fileUID+"'");
			return Result.failure();
		}

		Log.d(TAG, String.format("Zoning Worker changing zones from %s:%s to %s:%s for fileUID='%s'",
				currentZones.isLocal, currentZones.isRemote, shouldBeLocal, shouldBeRemote, fileUID));

		//If the zones for this file are already set to these values, do nothing
		if((currentZones.isLocal == shouldBeLocal) && (currentZones.isRemote == shouldBeRemote))
			return Result.success();

		if(!currentZones.isLocal && !currentZones.isRemote) {
			//TODO Attempt a repair. Maybe also do that in cleanup.
			Log.wtf(TAG, "Zoning machine broke. FileUID='"+fileUID+"'");
			return Result.failure();
		}


		HZone updatedZones = new HZone(fileUID, currentZones.isLocal, currentZones.isRemote);


		//--------------------------------------------------


		//If we're trying to download data to Local...
		if(!currentZones.isLocal && shouldBeLocal) {
			Log.d(TAG, "Zoning Worker trying to download data to Local! FileUID='"+fileUID+"'");

			if(!currentZones.isRemote)
				Log.d(TAG, "Zones say file shouldn't exist on remote! Skipping download! FileUID='"+fileUID+"'");

			else {
				Result downloadResult = downloadToLocal(fileUID);
				if(!downloadResult.equals(Result.success()))
					return downloadResult;

				updatedZones.isLocal = true;
				dao.put(updatedZones);
			}
		}

		//If we're trying to upload data to Remote (and the file exists on Local)...
		if(!currentZones.isRemote && shouldBeRemote) {
			Log.d(TAG, "Zoning Worker trying to upload data to Remote! FileUID='"+fileUID+"'");

			if(!currentZones.isLocal)
				Log.d(TAG, "Zones say file shouldn't exist on local! Skipping upload! FileUID='"+fileUID+"'");

			else {
				Result uploadResult = uploadToRemote(fileUID);
				if(!uploadResult.equals(Result.success()))
					return uploadResult;

				updatedZones.isRemote = true;
				dao.put(updatedZones);
			}
		}



		//If we're trying to remove the file from Local...
		if(currentZones.isLocal && !shouldBeLocal) {
			Log.d(TAG, "Zoning Worker trying to remove file from Local! FileUID='"+fileUID+"'");

			//We don't actually want to do anything but change the zoning info.
			//We ALWAYS want local props if the file exists on remote, and sync will sort things out if the remote file is deleted.
			//Instead, marking isLocal=false tells the cleanup job that this file's content isn't needed and can be removed.

			updatedZones.isLocal = false;
			dao.put(updatedZones);
		}

		//If we're trying to remove the file from Remote...
		if(currentZones.isRemote && !shouldBeRemote) {
			Log.d(TAG, "Zoning Worker trying to remove file from Remote! FileUID='"+fileUID+"'");

			Result removeRemoteResult = removeFromRemote(fileUID);
			if(!removeRemoteResult.equals(Result.success()))
				return removeRemoteResult;

			updatedZones.isRemote = false;
			dao.put(updatedZones);
		}


		return Result.success();
	}


	//---------------------------------------------------------------------------------------------

	//Note: I really tried to pull these functions out of this Worker class, but each sub-section is really
	// just made up of a ton of documentation, 8 lines of actual code, and like 30 error checks specific to the worker.

	@NonNull
	private Result downloadToLocal(UUID fileUID) {
		LocalRepo localRepo = LocalRepo.getInstance();
		RemoteRepo remoteRepo = RemoteRepo.getInstance();

		try {
			//Grab the props and content uri from Remote before we lock Local
			RFile remoteProps = remoteRepo.getFileProps(fileUID);
			Uri remoteContent = remoteRepo.getContentDownloadUri(remoteProps.checksum);

			try {
				localRepo.lock(fileUID);
				LFile localProps = localRepo.getFileProps(fileUID);

				try {
					//If the Local contents exist, we're good to go.
					//Since zoning says content isn't kept locally for this file, that content could just be used by another local file,
					// OR edited data was just written recently and we want to preserve that.
					//The next sync will rectify things if stuff just hasn't been written to Remote yet.
					localRepo.getContentProps(localProps.checksum);
				}
				catch (ContentsNotFoundException e) {
					//If the contents don't exist in Local, we can guarantee there is no newly written data we need to preserve.
					//We can't guarantee the Local checksum we need exists in Remote however, so we'll need to do an ad-hoc sync.
					//Throw the retrieved props straight into Local. This is acceptable since we know there's no data to-be-written from Local.
					localRepo.writeContents(localProps.checksum, remoteContent);
					localRepo.putFileProps(HFile.fromRemoteFile(remoteProps).toLocalFile(), localProps.checksum, localProps.attrhash);
				}
				return Result.success();
			}
			catch (FileNotFoundException ex) {
				//If file props were never in local, this system shouldn't actually know it exists, and therefore couldn't run this job.
				//Import or Sync is in charge of putting file props into local for the first time (they're special like that, lvl 5 clearance).
				//If we know about a file, but the local props don't exist, it means they were just deleted.
				Log.e(TAG, "Could not find local file props when copying to local! FileUID='"+fileUID+"'");
				return Result.failure();
			}
			finally {
				localRepo.unlock(fileUID);
			}
		} catch (FileNotFoundException ex) {
			Log.e(TAG, "Could not find remote file props when copying to local! FileUID='"+fileUID+"'");
			return Result.failure();
		} catch (ContentsNotFoundException e) {
			Log.e(TAG, "Remote contents not found when copying to local, something went wrong! FileUID='"+fileUID+"'");
			return Result.failure();
		} catch (ConnectException e) {
			Log.d(TAG, "Zoning Worker retrying due to connection issues! FileUID='"+fileUID+"'");
			return Result.retry();
		}
	}

	//---------------------------------------------------------------------------------------------

	private Result uploadToRemote(UUID fileUID) {
		LocalRepo localRepo = LocalRepo.getInstance();
		RemoteRepo remoteRepo = RemoteRepo.getInstance();

		try {
			//We only want to upload the data if the file does not already exist on Remote. Check just in case the zones are out of date.
			if(remoteRepo.doesFileExist(fileUID))
				throw new FileAlreadyExistsException("Remote file already exists!");

			LFile localProps = localRepo.getFileProps(fileUID);
			Uri localContent = localRepo.getContentUri(localProps.checksum);

			remoteRepo.uploadData(localProps.checksum, new File(localContent.getPath()));
			remoteRepo.createFile(HFile.toRemoteFile(localProps));

			return Result.success();
		}
		catch (IllegalStateException ex) {
			Log.w(TAG, "Zoning Worker tried to put a file to Remote, but someone beat us to it! This should not happen? FileUID='"+fileUID+"'");
			//Even though this is an exception, the zone update was technically a success
			return Result.success();
		}
		catch (FileAlreadyExistsException e) {
			Log.e(TAG, "Remote file already exists, no need to copy! FileUID='"+fileUID+"'");
			//Even though this is an exception, the zone update was technically a success
			return Result.success();
		}
		catch (ContentsNotFoundException ex) {
			Log.e(TAG, "Local contents not found when copying to remote, something went wrong! FileUID='"+fileUID+"'");
			return Result.failure();
		} catch (FileNotFoundException ex) {
			Log.e(TAG, "Local file props not found when copying to remote, something went wrong! FileUID='"+fileUID+"'");
			return Result.failure();
		} catch (ConnectException e) {
			Log.d(TAG, "Zoning Worker retrying due to connection issues! FileUID='"+fileUID+"'");
			return Result.retry();
		}
	}

	//---------------------------------------------------------------------------------------------

	private Result removeFromRemote(UUID fileUID) {
		RemoteRepo remoteRepo = RemoteRepo.getInstance();

		try {
			remoteRepo.deleteFileProps(fileUID);
			return Result.success();
		}
		catch (FileNotFoundException e) {
			/*
			Even though the file is technically removed, we want this to 'fail'. Otherwise, a large problem is allowed:
			Other than somehow bad data, if the zoning table says a file exists on remote when it doesn't, it means another device beat us to deleting it.
			If that happens, the next sync job will tell this device to delete this file from local.
			If we 'succeed' here instead, isRemote will be set to false, and a zoning job to upload to remote is then allowed to be scheduled.
			If that job is scheduled before the sync happens (30 second window, easily possible), that upload will overwrite the delete.
			We don't want that to happen. Instead, we'll let sync sort out any bad data once it runs.
			*/
			Log.e(TAG, "Could not find remote file props when removing from remote! FileUID='"+fileUID+"'");
			return Result.failure();
		} catch (ConnectException e) {
			Log.d(TAG, "Zoning Worker retrying due to connection issues! FileUID='"+fileUID+"'");
			return Result.retry();
		}
	}
}

