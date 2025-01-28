package aaa.sgordon.hybridrepo.local.database;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LocalConverters {
	@TypeConverter
	public static List<String> toList(String value) {
		Type listType = new TypeToken<List<String>>() {}.getType();
		//System.out.println("Converting toList: "+value+"  to  "+new Gson().fromJson(value, listType));

		return new Gson().fromJson(value, listType);
	}

	@TypeConverter
	public static String fromList(List<String> list) {
		//System.out.println("Converting fromList: "+list+"  to  "+new Gson().toJsonTree(list).getAsJsonArray().toString());

		return new Gson().toJsonTree(list).getAsJsonArray().toString();
	}

	//---------------------------------------------------------------------------------------------

	@TypeConverter
	public static UUID toUUID(String value) {
		return UUID.fromString(value);
	}

	@TypeConverter
	public static String fromUUID(UUID uuid) {
		return uuid.toString();
	}

	//---------------------------------------------------------------------------------------------

	@TypeConverter
	public static JsonObject toJsonObject(String value) {
		return new Gson().fromJson(value, JsonObject.class);
	}

	@TypeConverter
	public static String fromJsonObject(JsonObject object) {
		return object.toString();
	}

	//---------------------------------------------------------------------------------------------

	@TypeConverter
	public static Map<String, Object> toMap(String value) {
		Type mapType = new TypeToken<Map<String, Object>>() {}.getType();

		return new Gson().fromJson(value, mapType);
	}

	@TypeConverter
	public static String fromMap(Map<String, Object> list) {
		return new Gson().toJson(list);
	}

	//---------------------------------------------------------------------------------------------

	@TypeConverter
	public static Timestamp toTimestamp(String value) {
		return Timestamp.valueOf(value);
	}

	@TypeConverter
	public static String fromTimestamp(Timestamp timestamp) {
		return timestamp.toString();
	}
}