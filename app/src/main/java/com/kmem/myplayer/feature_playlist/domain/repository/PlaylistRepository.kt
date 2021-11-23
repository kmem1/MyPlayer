package com.kmem.myplayer.feature_playlist.domain.repository

import android.content.Context
import com.kmem.myplayer.core.domain.model.PlaylistState
import com.kmem.myplayer.core.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {

    suspend fun getTracksFromPlaylist(context: Context, playlistId: Int): Flow<List<Track>>
    suspend fun getPlaylistName(context: Context, playlistId: Int): String
    suspend fun getPlaylistState(context: Context, playlistId: Int): PlaylistState
    suspend fun deleteTracks(context: Context, tracks: List<Track>, playlistId: Int)
    fun updatePositions(context: Context, tracks: List<Track>)
}