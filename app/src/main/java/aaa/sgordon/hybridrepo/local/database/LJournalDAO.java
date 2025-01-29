package aaa.sgordon.hybridrepo.local.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;
import java.util.UUID;

import aaa.sgordon.hybridrepo.local.types.LJournal;

@Dao
public interface LJournalDAO {

	@Query("SELECT * FROM journal WHERE journalid > :journalID AND accountuid = :accountUID")
	List<LJournal> getAllChangesFor(UUID accountUID, int journalID);
	@Query("SELECT * FROM journal WHERE journalid > :journalID AND accountuid = :accountUID AND fileuid IN (:fileUIDs)")
	List<LJournal> getAllChangesFor(UUID accountUID, int journalID, UUID... fileUIDs);
	@Query("SELECT * FROM journal WHERE journalid > :journalID AND fileuid IN (:fileUIDs)")
	List<LJournal> getAllChangesFor(int journalID, UUID... fileUIDs);


	@Query("SELECT * FROM journal WHERE journalid > :journalID AND accountuid = :accountUID AND fileuid IN (:fileUIDs) "+
			"GROUP BY fileuid ORDER BY MAX(journalid)")
	List<LJournal> getLatestChangeFor(UUID accountUID, int journalID, UUID... fileUIDs);
	@Query("SELECT * FROM journal WHERE journalid > :journalID AND accountuid = :accountUID "+
			"GROUP BY fileuid ORDER BY MAX(journalid)")
	List<LJournal> getLatestChangeFor(UUID accountUID, int journalID);
	@Query("SELECT * FROM journal WHERE journalid > :journalID AND fileuid IN (:fileUIDs) "+
			"GROUP BY fileuid ORDER BY MAX(journalid)")
	List<LJournal> getLatestChangeFor(int journalID, UUID... fileUIDs);


	@Insert
	void insert(LJournal... entries);


	//Might remove since this is append-only, but it's here to mulligan
	@Delete
	Integer delete(LJournal... entries);
}