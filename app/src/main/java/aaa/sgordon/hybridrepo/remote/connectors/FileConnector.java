package aaa.sgordon.hybridrepo.remote.connectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Paths;
import java.util.UUID;

import aaa.sgordon.hybridrepo.remote.types.RFile;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FileConnector {
	private final String baseServerUrl;
	private final OkHttpClient client;
	private final UUID deviceUID;
	private static final String TAG = "Hyb.Rem.File";


	public FileConnector(String baseServerUrl, OkHttpClient client, UUID deviceUID) {
		this.baseServerUrl = baseServerUrl;
		this.client = client;
		this.deviceUID = deviceUID;
	}


	//---------------------------------------------------------------------------------------------
	// Get
	//---------------------------------------------------------------------------------------------

	//Instead of making an endpoint for this one, we're just checking if getProps() throws a FileNotFound exception
	public Boolean exists(@NonNull UUID fileUID) {
		throw new RuntimeException("Stub");
	}

	@NonNull
	public RFile getProps(@NonNull UUID fileUID) throws FileNotFoundException, IOException {
		//Log.i(TAG, String.format("\nGET FILE called with fileUID='"+fileUID+"'"));
		String url = Paths.get(baseServerUrl, "files", fileUID.toString()).toString();


		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 404)
				throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();

			return new Gson().fromJson(responseData.trim(), RFile.class);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Put
	//---------------------------------------------------------------------------------------------


	//Create a file entry on Remote
	public RFile create(@NonNull RFile fileProps) throws IOException {
		String base = Paths.get(baseServerUrl, "files", "create").toString();

		//Compile all passed properties into a form body. Doesn't matter what they are, send them all.
		JsonObject props = fileProps.toJson();
		FormBody.Builder builder = new FormBody.Builder();
		for(String key : props.keySet()) {
			if (key.equals("userattr"))
				builder.add(key, props.get(key).getAsJsonObject().toString());
			else
				builder.add(key, props.get(key).getAsString());
		}
		builder.add("deviceuid", deviceUID.toString());
		RequestBody body = builder.build();


		Request request = new Request.Builder().url(base).put(body).build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 409)
				throw new FileAlreadyExistsException("File already exists! FileUID='"+fileProps.fileuid+"'");
			if(response.body() == null)
				throw new IOException("Response body is null");
			if(response.code() == 422) {
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				String prettyJson = gson.toJson(JsonParser.parseString(response.body().string()));
				throw new RuntimeException("We're not sending the right stuff. \n"+prettyJson);
			}
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, RFile.class);
		}
	}



	//Update file content information on Remote
	public RFile putContentProps(@NonNull RFile fileProps, @NonNull String prevChecksum) throws IOException {
		String base = Paths.get(baseServerUrl, "files", "content").toString();

		//Compile all passed properties into a form body. Doesn't matter what they are, send them all.
		JsonObject props = fileProps.toJson();
		FormBody.Builder builder = new FormBody.Builder();
		for(String key : props.keySet()) {
			if (key.equals("userattr"))
				builder.add(key, props.get(key).getAsJsonObject().toString());
			else
				builder.add(key, props.get(key).getAsString());
		}
		builder.add("deviceuid", deviceUID.toString());
		RequestBody body = builder.build();


		Request request = new Request.Builder().url(base).put(body)
				.addHeader("If-Match", prevChecksum)
				.build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 404)
				throw new FileNotFoundException("File not found! FileUID='"+fileProps.fileuid+"'");
			if(response.code() == 412)
				throw new IllegalStateException("Checksums don't match! FileUID='"+fileProps.fileuid+"'");
			if(response.body() == null)
				throw new IOException("Response body is null");
			if(response.code() == 422) {
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				String prettyJson = gson.toJson(JsonParser.parseString(response.body().string()));
				throw new RuntimeException("We're not sending the right stuff. \n"+prettyJson);
			}
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, RFile.class);
		}
	}


	//Update file attribute information on Remote
	public RFile putAttributeProps(@NonNull RFile fileProps, @NonNull String prevAttrHash) throws IOException {
		String base = Paths.get(baseServerUrl, "files", "attributes").toString();

		//Compile all passed properties into a form body. Doesn't matter what they are, send them all.
		JsonObject props = fileProps.toJson();
		FormBody.Builder builder = new FormBody.Builder();
		for(String key : props.keySet()) {
			if (key.equals("userattr"))
				builder.add(key, props.get(key).getAsJsonObject().toString());
			else
				builder.add(key, props.get(key).getAsString());
		}
		builder.add("deviceuid", deviceUID.toString());
		RequestBody body = builder.build();


		Request request = new Request.Builder().url(base).put(body)
				.addHeader("If-Match", prevAttrHash)
				.build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 404)
				throw new FileNotFoundException("File not found! FileUID='"+fileProps.fileuid+"'");
			if(response.code() == 412)
				throw new IllegalStateException("Checksums don't match! FileUID='"+fileProps.fileuid+"'");
			if(response.body() == null)
				throw new IOException("Response body is null");
			if(response.code() == 422) {
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				String prettyJson = gson.toJson(JsonParser.parseString(response.body().string()));
				throw new RuntimeException("We're not sending the right stuff. \n"+prettyJson);
			}
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, RFile.class);
		}
	}



	//Update file timestamp information on Remote
	public RFile putTimestamps(@NonNull RFile fileProps) throws IOException {
		String base = Paths.get(baseServerUrl, "files", "timestamps").toString();

		//Compile all passed properties into a form body. Doesn't matter what they are, send them all.
		JsonObject props = fileProps.toJson();
		FormBody.Builder builder = new FormBody.Builder();
		for(String key : props.keySet()) {
			if (key.equals("userattr"))
				builder.add(key, props.get(key).getAsJsonObject().toString());
			else
				builder.add(key, props.get(key).getAsString());
		}
		builder.add("deviceuid", deviceUID.toString());
		RequestBody body = builder.build();


		Request request = new Request.Builder().url(base).put(body).build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 404)
				throw new FileNotFoundException("File not found! FileUID='"+fileProps.fileuid+"'");
			if(response.body() == null)
				throw new IOException("Response body is null");
			if(response.code() == 422) {
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				String prettyJson = gson.toJson(JsonParser.parseString(response.body().string()));
				throw new RuntimeException("We're not sending the right stuff. \n"+prettyJson);
			}
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, RFile.class);
		}
	}
	

	//---------------------------------------------------------------------------------------------
	// Delete
	//---------------------------------------------------------------------------------------------

	public void delete(@NonNull UUID fileUID, @NonNull UUID accountUID) throws FileNotFoundException, IOException {
		//Log.i(TAG, String.format("\nDELETE FILE called with fileUID='"+fileUID+"'"));
		String url = Paths.get(baseServerUrl, "files").toString();

		//Compile all necessary properties into a form body.
		FormBody.Builder builder = new FormBody.Builder();
		builder.add("fileuid", fileUID.toString());
		builder.add("accountuid", accountUID.toString());
		builder.add("deviceuid", deviceUID.toString());
		RequestBody body = builder.build();

		Request request = new Request.Builder().url(url).delete(body).build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 404)
				throw new FileNotFoundException("File not found! ID: '"+fileUID+"'");
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			//String responseData = response.body().string();
			//return new Gson().fromJson(responseData, SFile.class);
		}
	}
}
