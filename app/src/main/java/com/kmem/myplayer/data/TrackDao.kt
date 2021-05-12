package com.kmem.myplayer.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * Класс, который отвечает за запросы к БД.
 */

@Dao
interface TrackDao {

    @Query("SELECT * FROM track_in_playlist ORDER BY position")
    fun getTracks(): List<Track>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrack(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<Track>)

    @Update
    fun updateAll(tracks: List<Track>)

    @Delete
    fun deleteTrack(track: Track)

    @Delete
    fun deleteAll(tracks: List<Track>)

}