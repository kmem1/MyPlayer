package com.kmem.myplayer.feature_player.presentation.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import androidx.lifecycle.ViewModel
import com.kmem.myplayer.feature_player.domain.repository.PlayerControllerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlayerControllerViewModel @Inject constructor(
    private val repository: PlayerControllerRepository
) : ViewModel() {

    private val _currentTrackInfo = MutableStateFlow<TrackInfo?>(null)
    val currentTrackInfo = _currentTrackInfo.asStateFlow()

    fun setMetadata(metadata: MediaMetadataCompat?) {
        val artist: String

        if (metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) == "Unknown") {
            artist = ""
        } else {
            artist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
        }

        val title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
        val imageBitmap = metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
        val duration = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)?.toInt() ?: 1

        val durationString: String
        if (metadata == null) {
            durationString = "0:00"
        } else {
            val mins: Long = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000 / 60
            val secs: Long = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000 % 60
            durationString =
                if (secs < 10) "$mins:0$secs" else "$mins:$secs" // "0:00" duration format
        }

        _currentTrackInfo.value = TrackInfo(
            artist,
            title ?: "",
            imageBitmap,
            duration,
            durationString
        )
    }

    suspend fun isPlaylistCreated(context: Context): Boolean {
        val result: Boolean

        withContext(Dispatchers.IO) {
            result = repository.getPlaylists(context).isNotEmpty()
        }

        return result
    }

    data class TrackInfo(
        val artist: String,
        val title: String,
        val albumImgBitmap: Bitmap?,
        val duration: Int,
        val durationString: String,
    )
}