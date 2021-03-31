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
    private var shuffle_data : ArrayList<Track> = ArrayList() // additional copy for shuffle mode
    private var shuffle_stack : ArrayList<Track> = ArrayList() // stack trace for shuffle mode
    private var stack_index = 0
    private var maxIndex = 0
    private var stack : ArrayList<Track> = ArrayList<Track>()
    var currentUri : Uri? = null
    var currentItemIndex = 0
    var shuffle = false
        set(value) {
            field = value
            val isAlreadyShuffled = shuffle_stack.size == 1 && shuffle_stack[0].uri == currentUri
            if (value && data != null && !isAlreadyShuffled) {
                shuffle_data.clear()
                shuffle_data.addAll(data!!)
                shuffle_data.shuffle()
                shuffle_stack.clear()
                shuffle_stack.add(data!!.first { it.uri == currentUri })
                stack_index = 0
            }
        }

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
        if (shuffle) {
            return getNextOnShuffle()
        }

        if (currentItemIndex == maxIndex)
            currentItemIndex = 0
        else
            currentItemIndex++
        return getCurrent()
    }

    private fun getNextOnShuffle() : Track {
        var track : Track? = null
        if (stack_index == shuffle_stack.size - 1) {
            track = shuffle_data.removeAt(0)
            shuffle_stack.add(track)
            stack_index++
        } else {
            track = shuffle_stack[++stack_index]
        }

        currentUri = track.uri
        currentItemIndex = data?.indexOf(track) ?: 0
        return track
    }

    fun getPrevious() : Track? {
        if (shuffle)
            return getPreviousOnShuffle()

        if (currentItemIndex == 0)
            currentItemIndex = maxIndex
        else
            currentItemIndex--
        return getCurrent()
    }

    private fun getPreviousOnShuffle() : Track {
        if (stack_index != 0) stack_index--
        val track = shuffle_stack[stack_index]
        currentUri = track.uri
        currentItemIndex = data?.indexOf(track) ?: 0
        return track
    }

    fun getCurrent() : Track? {
        if (maxIndex == -1) return null
        currentUri = data!![currentItemIndex].uri
        return data!![currentItemIndex]
    }
}