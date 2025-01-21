package aaa.sgordon.hybridrepo.hybrid.types;

import androidx.annotation.NonNull;

import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.remote.types.RFile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class HFile {
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



	public HFile(@NonNull UUID fileuid, @NonNull UUID accountuid) {
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
		HFile hFile = (HFile) o;
		return isdir == hFile.isdir && islink == hFile.islink && filesize == hFile.filesize &&
				Objects.equals(fileuid, hFile.fileuid) && Objects.equals(accountuid, hFile.accountuid) &&
				Objects.equals(checksum, hFile.checksum) && Objects.equals(userattr, hFile.userattr) &&
				Objects.equals(attrhash, hFile.attrhash) && Objects.equals(changetime, hFile.changetime) &&
				Objects.equals(modifytime, hFile.modifytime) && Objects.equals(accesstime, hFile.accesstime) &&
				Objects.equals(createtime, hFile.createtime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, isdir, islink, filesize, checksum,
				userattr, attrhash, changetime, modifytime, accesstime, createtime);
	}


	//---------------------------------------------------------------------------------------------


	public static HFile fromLocalFile(@NonNull LFile local) {
		HFile hFile = new HFile(local.fileuid, local.accountuid);
		hFile.isdir = local.isdir;
		hFile.islink = local.islink;
		hFile.filesize = local.filesize;
		hFile.checksum = local.checksum;
		hFile.userattr = local.userattr;
		hFile.attrhash = local.attrhash;
		hFile.changetime = local.changetime;
		hFile.modifytime = local.modifytime;
		hFile.accesstime = local.accesstime;
		hFile.createtime = local.createtime;

		return hFile;
	}

	public static HFile fromServerFile(@NonNull RFile server) {
		HFile hFile = new HFile(server.fileuid, server.accountuid);
		hFile.isdir = server.isdir;
		hFile.islink = server.islink;
		hFile.filesize = server.filesize;
		hFile.checksum = server.checksum;
		hFile.userattr = server.userattr;
		hFile.attrhash = server.attrhash;
		hFile.changetime = server.changetime;
		hFile.modifytime = server.modifytime;
		hFile.accesstime = server.accesstime;
		hFile.createtime = server.createtime;

		return hFile;
	}


	public LFile toLocalFile() {
		LFile local = new LFile(fileuid, accountuid);
		local.isdir = isdir;
		local.islink = islink;
		local.filesize = filesize;
		local.checksum = checksum;
		local.userattr = userattr;
		local.attrhash = attrhash;
		local.changetime = changetime;
		local.modifytime = modifytime;
		local.accesstime = accesstime;
		local.createtime = createtime;

		return local;
	}

	public RFile toServerFile() {
		RFile server = new RFile(fileuid, accountuid);
		server.isdir = isdir;
		server.islink = islink;
		server.filesize = filesize;
		server.checksum = checksum;
		server.userattr = userattr;
		server.attrhash = attrhash;
		server.changetime = changetime;
		server.modifytime = modifytime;
		server.accesstime = accesstime;
		server.createtime = createtime;

		return server;
	}
}
