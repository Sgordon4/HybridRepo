package aaa.sgordon.hybridrepo.remote.connectors;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AccountConnector {
	private final String baseServerUrl;
	private final OkHttpClient client;
	private static final String TAG = "Hyb.Remote.Account";

	//For reference
	private static final String[] accountProps = {
			"accountuid",
			"rootfileuid",

			"email",
			"displayname",
			"password",

			"isdeleted",
			"logintime",
			"changetime",
			"createtime"
	};


	public AccountConnector(String baseServerUrl, OkHttpClient client) {
		this.baseServerUrl = baseServerUrl;
		this.client = client;
	}


	//---------------------------------------------------------------------------------------------
	// Get
	//---------------------------------------------------------------------------------------------

	//Get the account data from the server database
	public JsonObject getProps(@NonNull UUID accountID) throws IOException {
		Log.i(TAG, String.format("\nGET ACCOUNT called with accountID='"+accountID+"'"));
		String url = Paths.get(baseServerUrl, "accounts", accountID.toString()).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Put
	//---------------------------------------------------------------------------------------------


	//Create a new account entry in the database
	public JsonObject createEntry(@NonNull JsonObject props) throws IOException {
		Log.i(TAG, "\nCREATE ACCOUNT called");
		String url = Paths.get(baseServerUrl, "accounts", "insert").toString();

		String[] reqInsert = {"accountuid", "rootfileuid", "email", "displayname", "password"};
		if(!props.keySet().containsAll(Arrays.asList(reqInsert)))
			throw new IllegalArgumentException("Account creation request must contain all of: "+
					Arrays.toString(reqInsert));


		//Compile all passed properties into a form body
		FormBody.Builder builder = new FormBody.Builder();
		for(String prop : props.keySet()) {
			builder.add(prop, props.get(prop).getAsString());
		}
		RequestBody body = builder.build();


		Request request = new Request.Builder().url(url).post(body).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class);
		}
	}


	public JsonObject updateEntry(@NonNull JsonObject props) throws IOException {
		if(!props.has("accountuid"))
			throw new IllegalArgumentException("Account update request must contain accountuid!");

		UUID accountUID = UUID.fromString(props.get("accountuid").getAsString());
		Log.i(TAG, "\nUPDATE ACCOUNT called with accountuid='"+accountUID+"'");
		String url = Paths.get(baseServerUrl, "accounts", "update", accountUID.toString()).toString();

		//Note: This isn't checking that any usable props are sent, maybe we should but server can do that
		if(!(props.keySet().size() > 1))
			throw new IllegalArgumentException("Account update request must contain " +
					"at least one property other than accountuid!");


		//Compile all passed properties into a form body
		FormBody.Builder builder = new FormBody.Builder();
		for(String prop : props.asMap().keySet()) {
			builder.add(prop, props.get(prop).getAsString());
		}
		RequestBody body = builder.build();


		Request request = new Request.Builder().url(url).post(body).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, JsonObject.class);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Delete
	//---------------------------------------------------------------------------------------------

}
