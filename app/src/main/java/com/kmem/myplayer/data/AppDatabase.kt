package com.kmem.myplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database class for Room framework
 */
@Database(entities = [Track::class, Playlist::class], version = 1)
@TypeConverters(UriConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao() : TrackDao
    abstract fun playlistDao() : PlaylistDao

    companion object {
        private var instance: AppDatabase? = null
        private const val DATABASE_NAME = "PlayerDatabase"

        fun getInstance(context: Context): AppDatabase {
            return instance ?: buildDatabase(context).also { instance = it }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addCallback(DB_CALLBACK)
                .build()
        }

        private val DB_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL(
                    """
                        CREATE TRIGGER IF NOT EXISTS playlist_deleted AFTER DELETE ON playlist
                        BEGIN
                            DELETE FROM track_in_playlist
                            WHERE old.playlist_id = playlist_id;
                        END;
                    """.trimIndent()
                )
            }
        }
    }

}