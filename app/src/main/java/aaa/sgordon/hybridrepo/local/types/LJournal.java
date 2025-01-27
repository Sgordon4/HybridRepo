package aaa.sgordon.hybridrepo.local.types;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.UUID;

@Entity(tableName = "journal")
public class LJournal {
	@PrimaryKey(autoGenerate = true)
	public int journalid;

	@NonNull
	public UUID fileuid;
	@NonNull
	public UUID accountuid;

	@NonNull
	@ColumnInfo(defaultValue = "{}")
	public JsonObject changes;

	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Long changetime;

	@ColumnInfo(defaultValue = "false")
	public boolean fromSync;


	public LJournal(@NonNull UUID fileuid, @NonNull UUID accountuid, @NonNull JsonObject changes) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;
		this.changes = changes;
		this.fromSync = false;
	}
	public LJournal(@Nullable LFile oldProps, @NonNull LFile newProps) {
		this.fileuid = newProps.fileuid;
		this.accountuid = newProps.accountuid;
		this.changetime = newProps.changetime;
		this.fromSync = false;

		this.changes = computeChanges(oldProps, newProps);
	}


	private JsonObject computeChanges(@Nullable LFile oldProps, @NonNull LFile newProps) {
		JsonObject changes = new JsonObject();

		if(oldProps == null || !Objects.equals(oldProps.checksum, newProps.checksum))
			changes.addProperty("checksum", newProps.checksum);
		if(oldProps == null || !Objects.equals(oldProps.attrhash, newProps.attrhash))
			changes.addProperty("attrhash", newProps.attrhash);
		if(oldProps == null || !Objects.equals(oldProps.changetime, newProps.changetime))
			changes.addProperty("changetime", newProps.changetime);
		if(oldProps != null && !Objects.equals(oldProps.modifytime, newProps.modifytime))
			changes.addProperty("modifytime", newProps.modifytime);
		if(oldProps != null && !Objects.equals(oldProps.accesstime, newProps.accesstime))
			changes.addProperty("accesstime", newProps.accesstime);
		if(oldProps == null || !Objects.equals(oldProps.createtime, newProps.createtime))
			changes.addProperty("createtime", newProps.createtime);

		return changes;
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


	@Override
	public boolean equals(Object object) {
		if (this == object) return true;
		if (object == null || getClass() != object.getClass()) return false;
		LJournal that = (LJournal) object;
		return Objects.equals(fileuid, that.fileuid) && Objects.equals(accountuid, that.accountuid) &&
				Objects.equals(changes, that.changes) && Objects.equals(changetime, that.changetime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, changes, changetime);
	}
}