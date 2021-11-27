package com.kmem.myplayer.feature_player.domain.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.kmem.myplayer.core_data.db.entities.Playlist

interface PlayerControllerRepository {
    val isInitialized: LiveData<Boolean>

    suspend fun getPlaylists(context: Context): ArrayList<Playlist>
}