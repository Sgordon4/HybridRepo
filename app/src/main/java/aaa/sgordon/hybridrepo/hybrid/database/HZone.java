package aaa.sgordon.hybridrepo.hybrid.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.UUID;

@Entity(tableName = "zone")
public class HZone {
	@PrimaryKey
	@NonNull
	public UUID fileuid;

	@ColumnInfo(defaultValue = "true")
	public boolean isLocal;
	@ColumnInfo(defaultValue = "false")
	public boolean isRemote;

	
	public HZone(@NonNull UUID fileuid, boolean isLocal, boolean isRemote) {
		this.fileuid = fileuid;
		this.isLocal = isLocal;
		this.isRemote = isRemote;
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
