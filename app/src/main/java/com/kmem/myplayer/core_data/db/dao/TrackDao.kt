package com.kmem.myplayer.core_data.db.dao

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.room.*
import com.kmem.myplayer.core_data.db.entities.TrackEntity

/**
 * Data access object for tracks in database
 */
@Dao
interface TrackDao {

    @Query("SELECT * FROM track_in_playlist WHERE playlist_id = :playlistId ORDER BY position")
    fun getTracksFromPlaylistAsLiveData(playlistId: Int): LiveData<List<TrackEntity>>

    @Query("SELECT * FROM track_in_playlist WHERE playlist_id = :playlistId ORDER BY position")
    fun getTracksFromPlaylist(playlistId: Int): List<TrackEntity>

    @Query(
        "SELECT * FROM track_in_playlist " +
            "WHERE playlist_id = :playlistId AND position_in_stack != -1 " +
            "ORDER BY position_in_stack"
    )
    fun getShuffleStackForPlaylist(playlistId: Int): List<TrackEntity>

    @Query("UPDATE track_in_playlist SET " +
            "position_in_stack = :newPosition " +
            "WHERE uri = :uri AND playlist_id = :playlistId")
    fun updateStackPositionOfTrack(uri: Uri, playlistId: Int, newPosition: Int)

    @Query("UPDATE track_in_playlist SET " +
            "position = :newPosition " +
            "WHERE uri = :uri AND playlist_id = :playlistId")
    fun updatePositionOfTrack(uri: Uri, playlistId: Int, newPosition: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<TrackEntity>)

    @Update
    fun updateTrack(track: TrackEntity)

    @Update
    fun updateAll(tracks: List<TrackEntity>)

    @Delete
    fun deleteTrack(track: TrackEntity)

    @Delete
    fun deleteAll(tracks: List<TrackEntity>)
}