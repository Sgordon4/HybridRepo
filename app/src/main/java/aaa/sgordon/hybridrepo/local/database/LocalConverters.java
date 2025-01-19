package aaa.sgordon.hybridrepo.local.database;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.time.Instant;
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

		System.out.println("Value: ");
		System.out.println(value);
		return new Gson().fromJson(value, mapType);
	}

	@TypeConverter
	public static String fromMap(Map<String, Object> list) {
		return new Gson().toJson(list);
	}

	//---------------------------------------------------------------------------------------------

	private static final Gson gson = new GsonBuilder()
			.registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) ->
					Instant.parse(json.getAsString()))
			.registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (instant, type, jsonSerializationContext) ->
					new JsonPrimitive(instant.toString()
					))
			.create();

	@TypeConverter
	public static Instant toInstant(String value) {
		//return (value == null) ? null : Instant.parse(value);

		//return (value == null) ? null : gson.fromJson('"'+value+'"', Instant.class);
		System.out.println("ToInstant");
		System.out.println(value);
		System.out.println("To");
		System.out.println((value == null) ? null : Instant.ofEpochMilli(Long.parseLong(value)));
		System.out.println();
		return (value == null) ? null : Instant.ofEpochMilli(Long.parseLong(value));
	}

	@TypeConverter
	public static String fromInstant(Instant instant) {
		//return (instant == null) ? null : instant.toString();
		//return (instant == null) ? null : gson.toJson(instant);
		System.out.println("Instant");
		System.out.println(instant);
		System.out.println("To");
		System.out.println((instant == null) ? null : String.valueOf(instant.getEpochSecond()));
		System.out.println();
		return (instant == null) ? null :  String.valueOf(instant.getEpochSecond());
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