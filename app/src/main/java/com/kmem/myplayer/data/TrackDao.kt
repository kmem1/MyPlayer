package com.kmem.myplayer.data

import androidx.room.*

@Dao
interface TrackDao {
    @Query("SELECT * FROM playlist ORDER BY position")
    fun getTracks(): List<Track>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrack(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<Track>)

    @Update
    fun updateAll(tracks: List<Track>)

    @Delete
    fun deleteTrack(track: Track)
}