package aaa.sgordon.hybridrepo.local.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;
import java.util.UUID;

import aaa.sgordon.hybridrepo.local.types.LAccount;


@Dao
public interface LAccountDAO {
	@Query("SELECT * FROM account LIMIT 500")
	List<LAccount> loadAll();
	@Query("SELECT * FROM account LIMIT 500 OFFSET :offset")
	List<LAccount> loadAll(int offset);

	@Query("SELECT * FROM account WHERE accountuid = :accountUID")
	LAccount loadByUID(UUID accountUID);

	@Query("SELECT * FROM account WHERE accountuid IN (:accountUIDs)")
	List<LAccount> loadByUID(UUID... accountUIDs);


	@Upsert
	List<Long> put(LAccount... accounts);

	@Delete
	Integer delete(LAccount account);
}