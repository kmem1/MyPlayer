package com.kmem.myplayer.core.domain.repository

import android.content.Context
import com.kmem.myplayer.core_data.db.entities.Playlist

interface NavRepository {

    suspend fun addPlaylist(context: Context, playlistName: String)
    suspend fun getPlaylists(context: Context): ArrayList<Playlist>
    suspend fun deletePlaylist(context: Context, playlist: Playlist)
}