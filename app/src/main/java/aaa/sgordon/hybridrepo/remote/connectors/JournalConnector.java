package aaa.sgordon.hybridrepo.remote.connectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import aaa.sgordon.hybridrepo.remote.types.RJournal;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class JournalConnector {
	private final String baseServerUrl;
	private final OkHttpClient client;
	private final UUID deviceUID;
	private static final String TAG = "Hyb.Rem.Journal";


	public JournalConnector(String baseServerUrl, OkHttpClient client, UUID deviceUID) {
		this.baseServerUrl = baseServerUrl;
		this.client = client;
		this.deviceUID = deviceUID;
	}


	//TODO Include device ID
	@Nullable
	public List<RJournal> getAllChanges(long journalID, @NonNull UUID accountUID, UUID... fileUIDs) throws IOException {
		String url = Paths.get(baseServerUrl, "journal", ""+journalID).toString();

		FormBody.Builder builder = new FormBody.Builder();
		builder.add("accountuid", accountUID.toString());
		builder.add("fileuids", Arrays.toString(fileUIDs));
		builder.add("deviceuid", deviceUID.toString());
		RequestBody body = builder.build();

		Request request = new Request.Builder().url(url).put(body).build();
		try (Response response = client.newCall(request).execute()) {
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
			return new Gson().fromJson(responseData, new TypeToken< List<RJournal> >(){}.getType());
		}
	}


	//TODO Include device ID
	@Nullable
	public List<RJournal> getOnlyLatestChanges(long journalID, @NonNull UUID accountUID, UUID... fileUIDs) throws IOException {
		String url = Paths.get(baseServerUrl, "journal", "fileuids", ""+journalID).toString();

		FormBody.Builder builder = new FormBody.Builder();
		builder.add("accountUID", accountUID.toString());
		//builder.add("fileUID", fileUID.toString());
		RequestBody body = builder.build();

		Request request = new Request.Builder().url(url).put(body).build();
		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, new TypeToken< Set<RJournal> >(){}.getType());
		}
	}







	//LONGPOLL all journal entries after a given journalID
	public List<RJournal> longpollJournalEntriesAfter(int journalID) throws IOException, TimeoutException {
		//Log.i(TAG, String.format("\nLONGPOLL JOURNAL called with journalID='%s'", journalID));
		String url = Paths.get(baseServerUrl, "journal", "longpoll", ""+journalID).toString();

		Request request = new Request.Builder().url(url).build();
		try (Response response = client.newCall(request).execute()) {
			if(response.code() == 408)
				throw new TimeoutException();
			if (!response.isSuccessful())
				throw new IOException("Unexpected code " + response.code());
			if(response.body() == null)
				throw new IOException("Response body is null");

			String responseData = response.body().string();
			return new Gson().fromJson(responseData, new TypeToken< List<RJournal> >(){}.getType());
		}
	}
}
