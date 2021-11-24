package com.kmem.myplayer.feature_playlist.domain.repository

import android.content.Context

interface FileChooserRepository {
    fun addTracks(context: Context, paths: ArrayList<String>, playlistId: Int)
}