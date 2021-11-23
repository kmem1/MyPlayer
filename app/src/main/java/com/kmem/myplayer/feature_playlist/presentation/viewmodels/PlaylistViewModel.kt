package com.kmem.myplayer.feature_playlist.presentation.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmem.myplayer.core.domain.model.PlaylistState
import com.kmem.myplayer.core.domain.model.Track
import com.kmem.myplayer.feature_playlist.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
        private val repository: PlaylistRepository,
        private val state: SavedStateHandle
) : ViewModel() {

    val playlistId = state.get<Int>("playlist_id")

    private var isCollectingData = false

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val tracks = _tracks.asStateFlow()

    fun getTracks(context: Context): StateFlow<List<Track>> {
        if (!isCollectingData && playlistId != null) {
            viewModelScope.launch {
                repository.getTracksFromPlaylist(context, playlistId).collectLatest { newTracks ->
                    _tracks.value = newTracks
                }
            }
        }

        return tracks
    }

    fun deleteTracks(context: Context, tracks: List<Track>, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (playlistId != null) {
                repository.deleteTracks(context, tracks, playlistId)
                onComplete()
            }
        }
    }

    fun getTrackAtPosition(position: Int): Track {
        return tracks.value[position]
    }

    fun getTrackPositionByUri(uri: Uri): Int {
        return tracks.value.indexOfFirst { it.uri == uri }
    }

    fun getTrackListSize(): Int = tracks.value.size

    fun updatePositions(context: Context) {
        repository.updatePositions(context, tracks.value)
    }

    suspend fun getPlaylistState(context: Context): PlaylistState? {
        var state: PlaylistState? = null

        if (playlistId != null) {
            state = repository.getPlaylistState(context, playlistId)
        }

        return state
    }

    suspend fun getPlaylistName(context: Context): String? {
        var name: String? = null

        if (playlistId != null) {
            name = repository.getPlaylistName(context, playlistId)
        }

        return name
    }
}