package aaa.sgordon.hybridrepo.local.database;

import android.content.Context;
import android.util.Log;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import java.util.Arrays;

import aaa.sgordon.hybridrepo.local.types.LAccount;
import aaa.sgordon.hybridrepo.local.types.LContent;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.local.types.LJournal;


@Database(entities = {LAccount.class, LFile.class, LJournal.class, LContent.class}, version = 1)
@TypeConverters({LocalConverters.class})
public abstract class LocalDatabase extends RoomDatabase {


	public abstract LAccountDAO getAccountDao();
	public abstract LFileDAO getFileDao();
	public abstract LJournalDAO getJournalDao();
	public abstract LContentDAO getContentDao();



	public static class DBBuilder {
		private static final String DB_NAME = "hlocal.db";

		public LocalDatabase newInstance(Context context) {

			Builder<LocalDatabase> dbBuilder = Room.databaseBuilder(context, LocalDatabase.class, DB_NAME);

			//SQL Logging:
			QueryCallback callback = (s, list) -> {
				Log.v("Gal.SQLite", "---------------------------------------------------------");
				Log.v("Gal.SQLite", s);
				Log.v("Gal.SQLite", Arrays.toString(list.toArray()));
			};
			//dbBuilder.setQueryCallback(callback, Executors.newSingleThreadExecutor());

			return dbBuilder.build();
		}
	}
}