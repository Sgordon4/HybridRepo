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

	@Query("SELECT DISTINCT fileuid FROM journal WHERE accountuid = :accountUID AND journalid > :journalID")
	List<UUID> getFilesChangedForAccount(UUID accountUID, long journalID);

	@Query("SELECT * FROM journal WHERE fileuid = :fileUID AND journalid > :journalID")
	List<LJournal> getChangesForFile(UUID fileUID, long journalID);

	@Query("SELECT * FROM journal WHERE journalid > :journalID")
	LiveData<List<LJournal>> longpollAfterID(long journalID);


	@Insert
	void insert(LJournal... entries);


	//Might remove since this is append-only, but it's here to mulligan
	@Delete
	Integer delete(LJournal... entries);
}