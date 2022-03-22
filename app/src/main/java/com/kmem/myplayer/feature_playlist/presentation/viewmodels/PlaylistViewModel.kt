package com.kmem.myplayer.feature_playlist.presentation.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kmem.myplayer.R
import com.kmem.myplayer.core.domain.model.PlaylistState
import com.kmem.myplayer.core.domain.model.Track
import com.kmem.myplayer.core_utils.SingleLiveEvent
import com.kmem.myplayer.core_utils.extensions.toNavResult
import com.kmem.myplayer.core_utils.extensions.toToastResult
import com.kmem.myplayer.core_utils.ui.base.BaseBindingViewModel
import com.kmem.myplayer.feature_playlist.domain.repository.PlaylistRepository
import com.kmem.myplayer.feature_playlist.presentation.fragments.PlaylistFragmentDirections
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    state: SavedStateHandle
) : BaseBindingViewModel() {

    val playlistId = state.get<Int>("playlist_id")

    private var isCollectingData = false

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    private val tracks = _tracks.asStateFlow()

    private val _deleteMode = MutableStateFlow(false)
    val deleteMode = _deleteMode.asStateFlow()

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

    fun deleteTracks(context: Context, trackPositionList: List<Int>, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (playlistId != null) {
                _deleteMode.value = false

                val trackList = trackPositionList.map { getTrackAtPosition(it) }
                repository.deleteTracks(context, trackList, playlistId)

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

    fun openFileChooser() {
        playlistId?.let {
            navLiveData.value = PlaylistFragmentDirections.toFilechooser(playlistId).toNavResult()
        }
    }

    fun toggleDeleteMode() {
        _deleteMode.value = !deleteMode.value
    }

    fun onPermissionDenied() {
        toastLiveData.value = R.string.on_permission_denied.toToastResult()
    }
}