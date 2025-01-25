package aaa.sgordon.hybridrepo.local.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;
import java.util.UUID;

import aaa.sgordon.hybridrepo.local.types.LJournal;

@Dao
public interface LJournalDAO {

	@Query("SELECT * FROM journal WHERE accountuid = :accountUID AND journalid > :journalID")
	List<LJournal> getAllChangesFor(UUID accountUID, int journalID);
	@Query("SELECT * FROM journal WHERE accountuid = :accountUID AND journalid > :journalID AND fileuid IN (:fileUIDs)")
	List<LJournal> getAllChangesFor(UUID accountUID, int journalID, UUID... fileUIDs);


	@Query("SELECT * FROM journal WHERE accountuid = :accountUID AND journalid > :journalID AND fileuid IN (:fileUIDs) "+
			"GROUP BY fileuid ORDER BY MAX(journalid)")
	List<LJournal> getLatestChangeFor(UUID accountUID, int journalID, UUID... fileUIDs);
	@Query("SELECT * FROM journal WHERE accountuid = :accountUID AND journalid > :journalID "+
			"GROUP BY fileuid ORDER BY MAX(journalid)")
	List<LJournal> getLatestChangeFor(UUID accountUID, int journalID);


	@Insert
	void insert(LJournal... entries);


	//Might remove since this is append-only, but it's here to mulligan
	@Delete
	Integer delete(LJournal... entries);
}