package com.kmem.myplayer.core_data.db.dao

import androidx.room.*
import com.kmem.myplayer.core_data.db.entities.Playlist
import com.kmem.myplayer.core.domain.model.PlaylistState

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