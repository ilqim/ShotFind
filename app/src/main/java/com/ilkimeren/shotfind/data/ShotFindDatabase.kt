package com.ilkimeren.shotfind.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScreenshotEntity::class, TagEntity::class],
    version =6,
    exportSchema = false
)
abstract class ShotFindDatabase : RoomDatabase() {

    abstract fun screenshotDao(): ScreenshotDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var instance: ShotFindDatabase? = null

        /** Version 4 -> 5: adds the tags table. Existing screenshots, notes and OCR text are kept. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`colorArgb` INTEGER NOT NULL, " +
                            "`folderName` TEXT NOT NULL, " +
                            "`sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_folderName` ON `tags` (`folderName`)"
                )
            }
        }

        /** Version 5 -> 6: remembers screenshots we moved to the trash, so they can be restored. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `screenshots` ADD COLUMN `trashedAt` INTEGER")
            }
        }

        /** Returns the single database instance for the whole app. */
        fun getInstance(context: Context): ShotFindDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShotFindDatabase::class.java,
                    "shotfind.db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    // Still a safety net for older development versions (1-3).
                    // Must be removed before release.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}