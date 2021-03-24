package com.kmem.myplayer.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.internal.synchronized

@Database(entities = [Track::class], version = 1)
@TypeConverters(UriConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao() : TrackDao

    companion object {
        private var instance: AppDatabase? = null
        private const val DATABASE_NAME = "PlayerDatabase"

        fun getInstance(context: Context): AppDatabase {
            return instance ?: buildDatabase(context).also { instance = it }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()
        }
    }
}