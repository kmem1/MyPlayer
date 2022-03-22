package com.kmem.myplayer.feature_player.domain.repository

import android.content.Context
import android.net.Uri
import com.kmem.myplayer.core.domain.model.PlaylistState
import com.kmem.myplayer.core.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface PlayerServiceRepository {
    var shuffle: Boolean
    val isInitialized: Flow<Boolean>

    fun getCurrent(): Track?
    fun getNext(): Track?
    fun getPrevious(): Track?
    fun setPlaylist(playlistId: Int, position: Int)
    fun isEnded(): Boolean
    fun savePlaylistState(playlistId: Int, uri: Uri, position: Int)
    suspend fun getPlaylistState(context: Context, playlistId: Int): PlaylistState
}