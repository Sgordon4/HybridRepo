package aaa.sgordon.hybridrepo.remote.connectors;

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.remote.types.RContent;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ContentConnector {
	private final String baseServerUrl;
	private final OkHttpClient client;
	private static final String TAG = "Hyb.Rem.Cont";


	public ContentConnector(String baseServerUrl, OkHttpClient client) {
		this.baseServerUrl = baseServerUrl;
		this.client = client;
	}



	//---------------------------------------------------------------------------------------------
	// Props
	//---------------------------------------------------------------------------------------------

	public RContent getProps(@NonNull String name) throws IOException {
		String url = Paths.get(baseServerUrl, "content", name).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 404)
				throw new ContentsNotFoundException(name);
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, RContent.class);
		}
	}


	//TODO Have this triggered serverside by an IBM rule, not from manually calling it here
	//Make an entry in the server's database table for this content
	public RContent putProps(@NonNull String name, int size) throws IOException {
		Log.i(TAG, "PUTTING CONTENT PROPS...");
		String url = Paths.get(baseServerUrl, "content").toString();

		RequestBody body = new FormBody.Builder()
				.add("name", name)
				.add("size", String.valueOf(size))
				.build();
		Request request = new Request.Builder()
				.url(url).put(body).build();


		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, RContent.class);
		}
	}


	//---------------------------------------------------------------------------------------------
	// Contents
	//---------------------------------------------------------------------------------------------


	//Get a presigned URL for reading content
	//WARNING: The file at the end of this uri may not exist
	public String getDownloadUrl(@NonNull String name) throws IOException {
		//Log.i(TAG, String.format("\nGET CONTENT DOWNLOAD URL called with name='"+name+"'"));
		String url = Paths.get(baseServerUrl, "content", "data", "downloadurl", name).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			return response.body().string();
		}
	}


	//Get a presigned url for uploading data
	public String getUploadUrl(@NonNull String name) throws IOException {
		Log.i(TAG, "GETTING CONTENT UPLOAD URL...");
		String url = Paths.get(baseServerUrl, "content", "data", "uploadurl", name).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			return response.body().string();
		}
	}


	//Helper method
	public String uploadToUrl(@NonNull byte[] bytes, @NonNull String url) throws IOException {
		Log.i(TAG, "UPLOADING TO URL...");

		Request upload = new Request.Builder()
				.url(url)
				.put(RequestBody.create(bytes, MediaType.parse("application/octet-stream")))
				.build();

		try (Response response = client.newCall(upload).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());

			return response.headers().get("ETag");
		}
	}


	//---------------------------------------------------------------------------------------------
	// Multipart Upload
	//---------------------------------------------------------------------------------------------

	public static final int MIN_PART_SIZE = 1024 * 1024 * 5;	//5MB

	public Pair<UUID, List<Uri>> initializeMultipart(@NonNull String name, int fileSize) throws IOException {
		if(fileSize <= MIN_PART_SIZE) throw new IllegalArgumentException("File size must be > 5MB!");

		int numParts = (int) Math.ceil((double) fileSize / MIN_PART_SIZE);
		Log.i(TAG, "Initializing multipart upload with "+numParts+" parts for "+name);


		String url = Paths.get(baseServerUrl, "content", "data", "multipart", name, numParts+"").toString();
		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			JsonObject responseObj = new Gson().fromJson(responseData, JsonObject.class);


			UUID uploadID = UUID.fromString( responseObj.get("uploadID").getAsString() );

			JsonArray uploadURLs = responseObj.getAsJsonArray("partURLs");
			List<Uri> urls = uploadURLs.asList().stream()
					.map(JsonElement::getAsString).map(Uri::parse)
					.collect(Collectors.toList());


			return new Pair<>(uploadID, urls);
		}
	}



	public static class ETag {
		public final int PartNumber;
		public final String ETag;
		public ETag(int PartNumber, String ETag) {
			this.PartNumber = PartNumber;
			this.ETag = ETag;
		}
	}
	//All ETags must be sent at once, or missing ones will be left out when the file is compiled on the IBM server
	public void completeMultipart(@NonNull String name, @NonNull UUID uploadID, @NonNull List<ETag> etags) throws IOException {
		Log.i(TAG, "Completing multipart upload for "+name);

		String url = Paths.get(baseServerUrl, "content", "data", "multipart", name, uploadID.toString()).toString();


		FormBody.Builder builder = new FormBody.Builder();
		builder.add("ETags", new Gson().toJson(etags));
		RequestBody body = builder.build();



		Request request = new Request.Builder().url(url).put(body).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			System.out.println("Complete multipart complete");
			String responseData = response.body().string();
			System.out.println(responseData);
		}
	}


	public void cancelMultipart(@NonNull String name, @NonNull UUID uploadID) throws IOException {
		Log.i(TAG, "Cancelling multipart upload with uploadID="+uploadID+" for "+name);
		String url = Paths.get(baseServerUrl, "content", "data", "multipart", name, uploadID.toString()).toString();

		Request request = new Request.Builder().url(url).delete().build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 404)
				throw new FileNotFoundException("Multipart not found! ID: '"+uploadID+"'");
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");
		}
	}
}
