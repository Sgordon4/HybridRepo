package aaa.sgordon.hybridrepo.local.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import aaa.sgordon.hybridrepo.local.types.LContent;

@Dao
public interface LContentDAO {
	@Query("SELECT * FROM content WHERE name = :name")
	LContent get(String name);

	@Upsert
	void put(LContent... contents);

	@Delete
	Integer delete(LContent... contents);
	@Query("DELETE FROM content WHERE name = :fileHash")
	Integer delete(String fileHash);
}