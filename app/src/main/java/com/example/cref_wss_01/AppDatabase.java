package com.example.cref_wss_01;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Survey.class, Answer.class, MediaAttachment.class, OfflineMapArea.class},
        version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract SurveyDao surveyDao();
    public abstract AnswerDao answerDao();
    public abstract MediaAttachmentDao mediaAttachmentDao();
    public abstract OfflineMapAreaDao offlineMapAreaDao();

    private static volatile AppDatabase INSTANCE;

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `offline_map_areas` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "`name` TEXT," +
                "`north` REAL NOT NULL," +
                "`south` REAL NOT NULL," +
                "`east` REAL NOT NULL," +
                "`west` REAL NOT NULL," +
                "`zoomMin` INTEGER NOT NULL," +
                "`zoomMax` INTEGER NOT NULL," +
                "`tileCount` INTEGER NOT NULL," +
                "`downloadedAt` INTEGER NOT NULL)"
            );
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "wss_survey_database")
                            .addMigrations(MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
