package com.kmem.myplayer.data

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * Data access object for tracks in database
 */
@Dao
interface TrackDao {

    @Query("SELECT * FROM track_in_playlist WHERE playlist_id = :playlistId ORDER BY position")
    fun getTracksFromPlaylistAsLiveData(playlistId: Int): LiveData<List<Track>>

    @Query("SELECT * FROM track_in_playlist WHERE playlist_id = :playlistId ORDER BY position")
    fun getTracksFromPlaylist(playlistId: Int): List<Track>

    @Query(
        "SELECT * FROM track_in_playlist " +
            "WHERE playlist_id = :playlistId AND position_in_stack != -1 " +
            "ORDER BY position_in_stack"
    )
    fun getShuffleStackForPlaylist(playlistId: Int): List<Track>

    @Query("UPDATE track_in_playlist SET " +
            "position_in_stack = :newPosition " +
            "WHERE uri = :uri AND playlist_id = :playlistId")
    fun updateStackPositionOfTrack(uri: Uri, playlistId: Int, newPosition: Int)

    @Query("UPDATE track_in_playlist SET " +
            "position = :newPosition " +
            "WHERE uri = :uri AND playlist_id = :playlistId")
    fun updatePositionOfTrack(uri: Uri, playlistId: Int, newPosition: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrack(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<Track>)

    @Update
    fun updateTrack(track: Track)

    @Update
    fun updateAll(tracks: List<Track>)

    @Delete
    fun deleteTrack(track: Track)

    @Delete
    fun deleteAll(tracks: List<Track>)

}