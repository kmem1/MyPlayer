package com.kmem.myplayer.feature_player.domain.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.kmem.myplayer.core_data.db.entities.Playlist

interface PlayerControllerRepository {
    suspend fun getPlaylists(context: Context): ArrayList<Playlist>
}