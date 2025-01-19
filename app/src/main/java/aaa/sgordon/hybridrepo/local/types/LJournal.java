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

	@Nullable
	public String filehash;
	@Nullable
	public String attrhash;

	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Long changetime;



	public LJournal(@NonNull LFile file) {
		this.fileuid = file.fileuid;
		this.accountuid = file.accountuid;
		this.filehash = file.checksum;
		this.attrhash = file.attrhash;
		this.changetime = file.changetime;
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
				Objects.equals(filehash, that.filehash) && Objects.equals(attrhash, that.attrhash) &&
				Objects.equals(changetime, that.changetime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, filehash, attrhash, changetime);
	}
}