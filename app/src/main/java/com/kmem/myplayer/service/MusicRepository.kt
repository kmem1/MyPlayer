package com.kmem.myplayer.service

import android.content.Context
import android.net.Uri
import com.kmem.myplayer.R
import com.kmem.myplayer.data.AppDatabase
import com.kmem.myplayer.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicRepository(val context: Context) {
    private var data : List<Track>? = null
    private var maxIndex = 0
    private var stack : ArrayList<Track> = ArrayList<Track>()
    var shuffle = false
    var currentUri : Uri? = null
    var currentItemIndex = 0

    init {
        data = AppDatabase.getInstance(context).trackDao().getTracks()
        maxIndex = if (data == null) -1 else data!!.size - 1
    }

    fun updatePositions() {
        data = AppDatabase.getInstance(context).trackDao().getTracks()
        for (track in data!!) {
            if (track.uri == currentUri) {
                currentItemIndex = track.position
                break
            }
        }
    }

    fun getNewTracks() {
        data = AppDatabase.getInstance(context).trackDao().getTracks()
        maxIndex = if (data == null) -1 else data!!.size - 1
    }

    fun getNext() : Track? {
        if (currentItemIndex == maxIndex)
            currentItemIndex = 0
        else
            currentItemIndex++
        return getCurrent()
    }

    fun getPrevious() : Track? {
        if (currentItemIndex == 0)
            currentItemIndex = maxIndex
        else
            currentItemIndex--
        return getCurrent()
    }

    fun getCurrent() : Track? {
        if (maxIndex == -1) return null
        currentUri = data!![currentItemIndex].uri
        return data!![currentItemIndex]
    }
}