package aaa.sgordon.hybridrepo.hybrid.jobs.zoning;

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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.hybrid.database.HZone;
import aaa.sgordon.hybridrepo.hybrid.database.HZoningDAO;
import aaa.sgordon.hybridrepo.hybrid.database.HybridHelpDatabase;
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
	public static void enqueue(@NonNull UUID fileUID, boolean isLocal, boolean isRemote) throws FileNotFoundException {
		HZoningDAO dao = HybridHelpDatabase.getInstance().getZoningDao();
		HZone currentZones = dao.get(fileUID);
		if(currentZones == null) throw new FileNotFoundException("Zoning data not found for fileUID='"+fileUID+"'");


		Constraints.Builder constraints = new Constraints.Builder();

		//If we are downloading data to Local or uploading data to Remote, require an unmetered connection
		if((!currentZones.isLocal && isLocal) || (!currentZones.isRemote && isRemote))
			constraints.setRequiredNetworkType(NetworkType.UNMETERED);

		//If we're copying  data onto device, require storage not to be low
		if(!currentZones.isLocal && isLocal)
			constraints.setRequiresStorageNotLow(true);


		Data.Builder data = new Data.Builder();
		data.putString("FILEUID", fileUID.toString());
		data.putBoolean("ISLOCAL", isLocal);
		data.putBoolean("ISREMOTE", isRemote);

		OneTimeWorkRequest worker = new OneTimeWorkRequest.Builder(ZoningWorker.class)
				.setConstraints(constraints.build())
				.setInputData(data.build())
				.setInitialDelay(3, TimeUnit.SECONDS)	//User is most likely to requeue requests immediately after enqueuing one
				.addTag(fileUID.toString()).addTag("ZONING")
				.addTag("ISLOCAL_"+isLocal)
				.addTag("ISREMOTE_"+isRemote)
				.build();

		WorkManager workManager = WorkManager.getInstance(MyApplication.getAppContext());
		workManager.enqueueUniqueWork("zoning_"+fileUID, ExistingWorkPolicy.REPLACE, worker);
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

		String isLocalString = getInputData().getString("ISLOCAL");
		assert isLocalString != null;
		boolean isLocal = Boolean.getBoolean(isLocalString);

		String isRemoteString = getInputData().getString("ISREMOTE");
		assert isRemoteString != null;
		boolean isRemote = Boolean.getBoolean(isRemoteString);


		HZoningDAO dao = HybridHelpDatabase.getInstance().getZoningDao();
		HZone currentZones = dao.get(fileUID);
		if(currentZones == null) {
			Log.e(TAG, "Zoning data not found for fileUID='"+fileUID+"'");
			return Result.failure();
		}

		Log.d(TAG, String.format("Zoning Worker changing zones from %s:%s to %s:%s for fileUID='%s'",
				currentZones.isLocal, currentZones.isRemote, isLocal, isRemote, fileUID));

		//If the zones for this file are already set to these values, do nothing
		if((currentZones.isLocal == isLocal) && (currentZones.isRemote == isRemote))
			return Result.success();

		HZone updatedZones = new HZone(fileUID, currentZones.isLocal, currentZones.isRemote);



		LocalRepo localRepo = LocalRepo.getInstance();
		RemoteRepo remoteRepo = RemoteRepo.getInstance();

		//If we're trying to download data to Local (and the file exists on Remote)...
		if(!currentZones.isLocal && isLocal && !currentZones.isRemote)
			Log.d(TAG, "Zoning Worker trying to download data to Local, but zones say file shouldn't exist on remote! FileUID='"+fileUID+"'");
		if(!currentZones.isLocal && isLocal && currentZones.isRemote) {
			Log.d(TAG, "Zoning Worker trying to download data to Local! FileUID='"+fileUID+"'");
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
						localRepo.putFileProps(HFile.fromServerFile(remoteProps).toLocalFile(), localProps.checksum, localProps.attrhash);

						updatedZones.isLocal = true;
						dao.put(updatedZones);
					}
				}
				catch (FileNotFoundException ex) {
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


		//If we're trying to upload data to Remote (and the file exists on Local)...
		if(!currentZones.isRemote && isRemote && !currentZones.isLocal)
			Log.d(TAG, "Zoning Worker trying to upload data to remote, but zones say file shouldn't exist on local! FileUID='"+fileUID+"'");
		if(!currentZones.isRemote && isRemote && currentZones.isLocal) {
			Log.d(TAG, "Zoning Worker trying to upload data to Remote! FileUID='"+fileUID+"'");
			try {
				//We only want to upload the data if the file does not already exist on Remote. Check just in case the zones are out of date.
				if(!remoteRepo.doesFileExist(fileUID)) {
					LFile localProps = localRepo.getFileProps(fileUID);
					Uri localContent = localRepo.getContentUri(localProps.checksum);

					remoteRepo.uploadData(localProps.checksum, new File(localContent.getPath()));
					remoteRepo.putFileProps(HFile.fromLocalFile(localProps).toServerFile(), "", "");

					updatedZones.isRemote = true;
					dao.put(updatedZones);

				}
			} catch (IllegalStateException ex) {
				Log.w(TAG, "Zoning Worker tried to put a file to Remote, but someone beat us to it! This should not happen? FileUID='"+fileUID+"'");
				//Even though this is an issue, the zone update was technically a success
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


		//If we're trying to remove the file from Remote...
		if(currentZones.isRemote && !isRemote) {
			Log.d(TAG, "Zoning Worker trying to remove file from Remote! FileUID='"+fileUID+"'");
			try {
				remoteRepo.deleteFileProps(fileUID);

				updatedZones.isRemote = false;
				dao.put(updatedZones);

			} catch (FileNotFoundException e) {
				Log.e(TAG, "Could not find remote file props when removing from remote! FileUID='"+fileUID+"'");
				return Result.failure();
			} catch (ConnectException e) {
				Log.d(TAG, "Zoning Worker retrying due to connection issues! FileUID='"+fileUID+"'");
				return Result.retry();
			}
		}


		//If we're trying to remove the file from Local...
		if(currentZones.isLocal && !isLocal) {
			Log.d(TAG, "Zoning Worker trying to remove file from Local! FileUID='"+fileUID+"'");
			try {
				localRepo.lock(fileUID);
				localRepo.deleteFileProps(fileUID);

				updatedZones.isLocal = false;
				dao.put(updatedZones);
			}
			finally {
				localRepo.unlock(fileUID);
			}
		}


		return Result.success();
	}
}
