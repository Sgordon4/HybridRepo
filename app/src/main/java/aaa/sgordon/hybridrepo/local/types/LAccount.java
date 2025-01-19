package aaa.sgordon.hybridrepo.local.types;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity(tableName = "account")
public class LAccount {
	@PrimaryKey
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

	@ColumnInfo(defaultValue = "false")
	public boolean isdeleted;

	@ColumnInfo(defaultValue = "-1")
	public Long logintime;
	@ColumnInfo(defaultValue = "-1")
	public Long changetime;
	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	public Long createtime;



	@Ignore
	public LAccount(){
		this(UUID.randomUUID(), UUID.randomUUID());
	}

	public LAccount(@NonNull UUID accountuid, @NonNull UUID rootfileuid) {
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
		LAccount lAccount = (LAccount) o;
		return isdeleted == lAccount.isdeleted && Objects.equals(logintime, lAccount.logintime) &&
				Objects.equals(changetime, lAccount.changetime) && Objects.equals(createtime, lAccount.createtime) &&
				Objects.equals(accountuid, lAccount.accountuid) && Objects.equals(rootfileuid, lAccount.rootfileuid) &&
				Objects.equals(email, lAccount.email) && Objects.equals(displayname, lAccount.displayname) &&
				Objects.equals(password, lAccount.password);
	}

	@Override
	public int hashCode() {
		return Objects.hash(accountuid, rootfileuid, email, displayname, password, isdeleted, logintime, changetime, createtime);
	}
}