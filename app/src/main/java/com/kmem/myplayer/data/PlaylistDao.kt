package com.kmem.myplayer.data

import androidx.room.*

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlist")
    fun getPlaylists(): List<Playlist>

    @Query("SELECT * FROM playlist WHERE playlist_id = :playlistId")
    fun getPlaylist(playlistId: Int): Playlist

    @Query("SELECT last_played_uri, last_played_position FROM playlist WHERE playlist_id = :playlistId")
    fun getState(playlistId: Int): PlaylistState

    @Insert
    fun insertPlaylist(playlist: Playlist)

    @Update
    fun updatePlaylist(playlist: Playlist)

    @Delete
    fun deletePlaylist(playlist: Playlist)

}