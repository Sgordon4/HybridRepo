package aaa.sgordon.hybridrepo.hybrid.database;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.UUID;

@Dao
public interface HSyncDAO {

	@Nullable
	@Query("SELECT * FROM sync where fileuid = :fileUID")
	HSync get(UUID fileUID);

	@Upsert
	void put(HSync sync);

	@Delete
	void delete(HSync sync);
	@Query("DELETE FROM sync WHERE fileuid = :fileUID")
	void delete(UUID fileUID);
}
