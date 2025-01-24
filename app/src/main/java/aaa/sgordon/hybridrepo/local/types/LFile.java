package aaa.sgordon.hybridrepo.local.types;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity(tableName = "file")
public class LFile {
	public static final String defaultChecksum = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
	public static final String defaultAttrHash = "44136FA355B3678A1146AD16F7E8649E94FB4FC21FE77E8310C060F61CAAFF8A";

	@PrimaryKey
	@NonNull
	public UUID fileuid;
	@NonNull
	public UUID accountuid;

	@ColumnInfo(defaultValue = "false")
	public boolean isdir;
	@ColumnInfo(defaultValue = "false")
	public boolean islink;

	@NonNull
	@ColumnInfo(defaultValue = defaultChecksum)
	public String checksum;
	@ColumnInfo(defaultValue = "0")
	public int filesize;

	@NonNull
	@ColumnInfo(defaultValue = "{}")
	public JsonObject userattr;
	@NonNull
	@ColumnInfo(defaultValue = defaultAttrHash)
	public String attrhash;

	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Long changetime;	//Last time the file properties (database row) were changed
	public Long modifytime;	//Last time the file contents were modified
	public Long accesstime;	//Last time the file contents were accessed
	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Long createtime;



	public LFile(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;

		this.isdir = false;
		this.islink = false;
		this.checksum = defaultChecksum;
		this.filesize = 0;
		this.userattr = new JsonObject();
		this.attrhash = defaultAttrHash;
		this.changetime = Instant.now().getEpochSecond();
		this.modifytime = null;
		this.accesstime = null;
		this.createtime = Instant.now().getEpochSecond();

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
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LFile lFile = (LFile) o;
		return isdir == lFile.isdir && islink == lFile.islink && filesize == lFile.filesize &&
				Objects.equals(fileuid, lFile.fileuid) && Objects.equals(accountuid, lFile.accountuid) &&
				Objects.equals(checksum, lFile.checksum) && Objects.equals(userattr, lFile.userattr) &&
				Objects.equals(attrhash, lFile.attrhash) && Objects.equals(changetime, lFile.changetime) &&
				Objects.equals(modifytime, lFile.modifytime) && Objects.equals(accesstime, lFile.accesstime) &&
				Objects.equals(createtime, lFile.createtime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, isdir, islink, filesize, checksum,
				userattr, attrhash, changetime, modifytime, accesstime, createtime);
	}
}