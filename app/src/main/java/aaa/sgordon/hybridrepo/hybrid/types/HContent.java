package aaa.sgordon.hybridrepo.hybrid.types;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;

public class HContent {
	@NonNull
	public String name;
	public int size;
	public Long createtime;


	public HContent(@NonNull String name, int size) {
		this.name = name;
		this.size = size;
		this.createtime = Instant.now().getEpochSecond();
	}


	public JsonObject toJson() {
		Gson gson = new GsonBuilder().create();
		return gson.toJsonTree(this).getAsJsonObject();
	}

	@NonNull
	@Override
	public String toString() {
		JsonObject json = toJson();
		return json.toString();
	}
}
