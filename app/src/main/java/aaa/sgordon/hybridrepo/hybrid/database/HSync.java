package aaa.sgordon.hybridrepo.hybrid.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.UUID;

@Entity(tableName = "sync")
public class HSync {
	@PrimaryKey
	@NonNull
	public UUID fileuid;

	@NonNull
	public String lastSyncChecksum;

	@Nullable
	public JsonObject attrBeforeMod;


	public HSync(@NonNull UUID fileuid, @NonNull String lastSyncChecksum, @Nullable JsonObject attrBeforeMod) {
		this.fileuid = fileuid;
		this.lastSyncChecksum = lastSyncChecksum;
		this.attrBeforeMod = attrBeforeMod;
	}


	public JsonObject toJson() {
		return new Gson().toJsonTree(this).getAsJsonObject();
	}

	@NonNull
	@Override
	public String toString() {
		JsonObject json = toJson();
		return json.toString();
	}
}
