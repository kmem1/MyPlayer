package com.kmem.myplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Класс, который содержит базу данных.
 * */
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