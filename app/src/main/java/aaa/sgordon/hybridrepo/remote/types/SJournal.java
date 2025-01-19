package aaa.sgordon.hybridrepo.remote.types;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SJournal {
	public int journalid;

	@NonNull
	public UUID fileuid;
	@NonNull
	public UUID accountuid;

	@Nullable
	public String filehash;
	@Nullable
	public String attrhash;

	public Long changetime;


	public SJournal(@NonNull UUID fileuid, @NonNull UUID accountuid) {
		this.fileuid = fileuid;
		this.accountuid = accountuid;
		this.changetime = Instant.now().getEpochSecond();
	}
	public SJournal(@NonNull SFile file) {
		this.fileuid = file.fileuid;
		this.accountuid = file.accountuid;
		this.filehash = file.checksum;
		this.attrhash = file.attrhash;
		this.changetime = file.changetime;
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


	@Override
	public boolean equals(Object object) {
		if (this == object) return true;
		if (object == null || getClass() != object.getClass()) return false;
		SJournal that = (SJournal) object;
		return Objects.equals(fileuid, that.fileuid) && Objects.equals(accountuid, that.accountuid) &&
				Objects.equals(filehash, that.filehash) && Objects.equals(attrhash, that.attrhash) &&
				Objects.equals(changetime, that.changetime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, filehash, attrhash, changetime);
	}
}
