package com.kmem.myplayer.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * Класс, который отвечает за запросы к БД.
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