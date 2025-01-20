package aaa.sgordon.hybridrepo.hybrid.jobs;

import android.content.Context;
import android.content.SharedPreferences;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.local.LocalRepo;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;

public class Sync {
	private final SharedPreferences sharedPrefs;
	private int lastSyncLocalID;
	private int lastSyncServerID;

	private final LocalRepo localRepo;
	private final RemoteRepo remoteRepo;


	private Sync() {
		localRepo = LocalRepo.getInstance();
		remoteRepo = RemoteRepo.getInstance();


		sharedPrefs = MyApplication.getAppContext().getSharedPreferences("gallery.syncPointers", Context.MODE_PRIVATE);
		lastSyncLocalID = sharedPrefs.getInt("lastSyncLocal", 0);
		lastSyncServerID = sharedPrefs.getInt("lastSyncServer", 0);
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
	public void updateLastSyncServer(int id) {
		if(id > lastSyncServerID)
			lastSyncServerID = id;

		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putInt("lastSyncServer", lastSyncServerID);
		editor.apply();
	}

	public int getLastSyncLocal() {
		return lastSyncLocalID;
	}
	public int getLastSyncServer() {
		return lastSyncServerID;
	}

	//---------------------------------------------------------------------------------------------





}
