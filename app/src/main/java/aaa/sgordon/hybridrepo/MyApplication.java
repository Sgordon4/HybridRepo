package aaa.sgordon.hybridrepo;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

//This class is only being used to get context in static/singleton locations.
//This process feels like cheating every time lmao.
public class MyApplication extends Application {

	@SuppressLint("StaticFieldLeak")
	private static Context context;

	public void onCreate() {
		super.onCreate();
		MyApplication.context = getApplicationContext();
	}

	public static Context getAppContext() {
		return MyApplication.context;
	}


	public static boolean doesDeviceHaveInternet() {
		ConnectivityManager connectivityManager = (ConnectivityManager)
				context.getSystemService(Context.CONNECTIVITY_SERVICE);

		Network nw = connectivityManager.getActiveNetwork();
		if(nw == null) return false;

		NetworkCapabilities cap = connectivityManager.getNetworkCapabilities(nw);
		return cap != null && (
				cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
				//|| cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
				//|| cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
				//|| cap.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
		);
	}
}