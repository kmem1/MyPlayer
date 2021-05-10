package com.kmem.myplayer.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlist")
    fun getPlaylists(): List<Playlist>

}