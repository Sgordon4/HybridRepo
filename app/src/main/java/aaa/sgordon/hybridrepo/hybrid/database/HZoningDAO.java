package aaa.sgordon.hybridrepo.hybrid.database;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.UUID;

@Dao
public interface HZoningDAO {

	@Nullable
	@Query("SELECT * FROM zone where fileuid = :fileUID")
	HZone get(UUID fileUID);

	@Upsert
	void put(HZone zone);

	@Delete
	void delete(HZone zone);
	@Query("DELETE FROM zone WHERE fileuid = :fileUID")
	void delete(UUID fileUID);
}
