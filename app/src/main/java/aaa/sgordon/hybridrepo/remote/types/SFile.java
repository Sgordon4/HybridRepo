package aaa.sgordon.hybridrepo.remote.types;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SFile {
	public static final String defaultChecksum = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
	public static final String defaultAttrHash = "44136FA355B3678A1146AD16F7E8649E94FB4FC21FE77E8310C060F61CAAFF8A";

	@NonNull
	public UUID fileuid;
	@NonNull
	public UUID accountuid;

	public boolean isdir;
	public boolean islink;

	@NonNull
	public String checksum;
	public int filesize;

	@NonNull
	public JsonObject userattr;
	@NonNull
	public String attrhash;

	@NonNull
	public Long changetime;	//Last time the file properties (database row) were changed
	public Long modifytime;	//Last time the file contents were modified
	public Long accesstime;	//Last time the file contents were accessed
	@NonNull
	public Long createtime; //Create time :)



	public SFile(@NonNull UUID fileuid, @NonNull UUID accountuid) {
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
		SFile sFile = (SFile) o;
		return isdir == sFile.isdir && islink == sFile.islink && filesize == sFile.filesize &&
				Objects.equals(fileuid, sFile.fileuid) && Objects.equals(accountuid, sFile.accountuid) &&
				Objects.equals(checksum, sFile.checksum) && Objects.equals(userattr, sFile.userattr) &&
				Objects.equals(attrhash, sFile.attrhash) && Objects.equals(changetime, sFile.changetime) &&
				Objects.equals(modifytime, sFile.modifytime) && Objects.equals(accesstime, sFile.accesstime) &&
				Objects.equals(createtime, sFile.createtime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, isdir, islink, filesize, checksum,
				userattr, attrhash, changetime, modifytime, accesstime, createtime);
	}
}
