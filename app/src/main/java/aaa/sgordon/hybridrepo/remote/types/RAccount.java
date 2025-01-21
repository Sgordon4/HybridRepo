package aaa.sgordon.hybridrepo.remote.types;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class RAccount {
	@NonNull
	public UUID accountuid;
	@NonNull
	public UUID rootfileuid;

	@Nullable
	public String email;
	@Nullable
	public String displayname;
	@Nullable
	public String password;

	public boolean isdeleted;

	public Long logintime;
	public Long changetime;
	public Long createtime;


	public RAccount(){
		this(UUID.randomUUID(), UUID.randomUUID());
	}
	public RAccount(@NonNull UUID accountuid, @NonNull UUID rootfileuid) {
		this.accountuid = accountuid;
		this.rootfileuid = rootfileuid;

		this.email = null;
		this.displayname = null;
		this.password = null;

		this.isdeleted = false;

		this.logintime = null;
		this.changetime = null;
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


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		RAccount rAccount = (RAccount) o;
		return isdeleted == rAccount.isdeleted && Objects.equals(logintime, rAccount.logintime) &&
				Objects.equals(changetime, rAccount.changetime) && Objects.equals(createtime, rAccount.createtime) &&
				Objects.equals(accountuid, rAccount.accountuid) && Objects.equals(rootfileuid, rAccount.rootfileuid) &&
				Objects.equals(email, rAccount.email) && Objects.equals(displayname, rAccount.displayname) && Objects.equals(password, rAccount.password);
	}

	@Override
	public int hashCode() {
		return Objects.hash(accountuid, rootfileuid, email, displayname, password, isdeleted, logintime, changetime, createtime);
	}
}
