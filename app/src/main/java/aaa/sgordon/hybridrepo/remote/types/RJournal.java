package aaa.sgordon.hybridrepo.remote.types;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.UUID;

public class RJournal {
	public int journalid;

	@NonNull
	public UUID fileuid;
	@NonNull
	public UUID accountuid;

	@NonNull
	public JsonObject properties;

	public Long changetime;



	public RJournal(@NonNull RFile file, @NonNull JsonObject changes) {
		this.fileuid = file.fileuid;
		this.accountuid = file.accountuid;
		this.properties = changes;
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
		RJournal that = (RJournal) object;
		return Objects.equals(fileuid, that.fileuid) && Objects.equals(accountuid, that.accountuid) &&
				Objects.equals(properties, that.properties) && Objects.equals(changetime, that.changetime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileuid, accountuid, properties, changetime);
	}
}
