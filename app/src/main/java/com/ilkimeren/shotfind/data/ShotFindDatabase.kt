package com.ilkimeren.shotfind.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ScreenshotEntity::class],
    version =3,
    exportSchema = false
)
abstract class ShotFindDatabase : RoomDatabase() {

    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        @Volatile
        private var instance: ShotFindDatabase? = null

        /** Returns the single database instance for the whole app. */
        fun getInstance(context: Context): ShotFindDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShotFindDatabase::class.java,
                    "shotfind.db"
                )
                    // During development: if the schema changes, wipe and rebuild.
                    // Must be replaced with real migrations before release.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}