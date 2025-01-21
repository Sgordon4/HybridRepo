package aaa.sgordon.hybridrepo.local.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import aaa.sgordon.hybridrepo.local.types.LAccount;
import aaa.sgordon.hybridrepo.local.types.LContent;
import aaa.sgordon.hybridrepo.local.types.LFile;
import aaa.sgordon.hybridrepo.local.types.LJournal;

import java.util.Arrays;


@Database(entities = {LAccount.class, LFile.class, LJournal.class, LContent.class}, version = 1)
@TypeConverters({LocalConverters.class})
public abstract class LocalDatabase extends RoomDatabase {


	public abstract LAccountDAO getAccountDao();
	public abstract LFileDAO getFileDao();
	public abstract LJournalDao getJournalDao();
	public abstract LContentDao getContentDao();



	public static class DBBuilder {
		private static final String DB_NAME = "glocal.db";

		public LocalDatabase newInstance(Context context) {

			Builder<LocalDatabase> dbBuilder = Room.databaseBuilder(context, LocalDatabase.class, DB_NAME);


			/*
			dbBuilder.addCallback(new Callback() {
				@Override
				public void onCreate(@NonNull SupportSQLiteDatabase db) {
					super.onCreate(db);

					//Journal triggers
					//I don't actually think the DROP TRIGGER statements work... Are they POSTed?
					//Gotta delete the whole DB to reset them

					//When a file row is inserted or updated, add a record to the Journal.
					//The journal inserts themselves are identical, there are just two triggers for insert and update respectively
					db.execSQL("DROP TRIGGER IF EXISTS file_insert_to_journal;");
					db.execSQL("CREATE TRIGGER IF NOT EXISTS file_insert_to_journal AFTER INSERT ON file FOR EACH ROW " +
							"BEGIN " +
							"INSERT INTO journal (accountuid, fileuid, filehash, attrhash, changetime) " +
							"VALUES (NEW.accountuid, NEW.fileuid,  NEW.filehash, NEW.attrhash, NEW.changetime); " +
							"END;");

					db.execSQL("DROP TRIGGER IF EXISTS file_update_to_journal;");
					db.execSQL("CREATE TRIGGER IF NOT EXISTS file_update_to_journal AFTER UPDATE OF filehash, attrhash, isdeleted " +
							"ON file FOR EACH ROW " +
							"WHEN (NEW.attrhash != OLD.attrhash) OR (NEW.filehash != OLD.filehash) " +
							"OR (NEW.isdeleted == false AND OLD.isdeleted == true) " +
							"BEGIN " +
							"INSERT INTO journal (accountuid, fileuid, filehash, attrhash, changetime) " +
							"VALUES (NEW.accountuid, NEW.fileuid, NEW.filehash, NEW.attrhash, NEW.changetime); " +
							"END;");


					//Note: No DELETE trigger, since to 'delete' a file we actually set the isdeleted bit.
					// Actual row deletion would be the result of admin work like scheduled cleanup or a file domain move
					// (local -> server, vice versa).
				}
			});
			 */


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