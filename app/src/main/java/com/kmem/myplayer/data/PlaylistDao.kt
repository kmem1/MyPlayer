package com.kmem.myplayer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlist")
    fun getPlaylists(): List<Playlist>

    @Insert
    fun insertPlaylist(playlist: Playlist)

    @Delete
    fun deletePlaylist(playlist: Playlist)

}