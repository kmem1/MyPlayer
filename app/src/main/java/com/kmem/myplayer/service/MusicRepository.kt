package com.kmem.myplayer.service

import android.content.Context
import android.net.Uri
import com.kmem.myplayer.data.AppDatabase
import com.kmem.myplayer.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Предоставляет данные для сервиса. Сервис делегирует запросы к БД этому классу.
 */

class MusicRepository(val context: Context) {
    private var data: ArrayList<Track> = ArrayList()
    private var shuffle_data: ArrayList<Track> = ArrayList() // additional copy for shuffle mode
    private var shuffle_stack: ArrayList<Track> = ArrayList() // stack trace for shuffle mode
    private var stack_index = 0
    private var maxIndex = 0
    var currentUri: Uri? = null
    var currentItemIndex = 0
    var shuffle = false
        set(value) {
            field = value
            val isAlreadyShuffled = shuffle_stack.size == 1 && shuffle_stack[0].uri == currentUri
            if (value && !isAlreadyShuffled) {
                shuffle_data.clear()
                shuffle_data.addAll(data)
                shuffle_data.shuffle()
                shuffle_stack.clear()
                shuffle_stack.add(data.first { it.uri == currentUri })
                shuffle_data.remove(data.first { it.uri == currentUri })
                stack_index = 0
            }
        }

    init {
        data.addAll(AppDatabase.getInstance(context).trackDao().getTracks())
        maxIndex = data.lastIndex
    }

    suspend fun updatePositions() {
        withContext(Dispatchers.IO) {
            data.clear()
            data.addAll(AppDatabase.getInstance(context).trackDao().getTracks())
            currentItemIndex = data.indexOfFirst { it.uri == currentUri }
            if (currentItemIndex == -1)
                currentItemIndex = 0
        }
    }

    suspend fun addNewTracks() {
        withContext(Dispatchers.IO) {
            data.clear()
            data.addAll(AppDatabase.getInstance(context).trackDao().getTracks())
            maxIndex = data.lastIndex

            val isAlreadyShuffled = shuffle_stack.size == 1 && shuffle_stack[0].uri == currentUri
            if (shuffle || isAlreadyShuffled) {
                shuffle_data.clear()
                shuffle_data.addAll(data)
                shuffle_data.removeAll(shuffle_stack)
                shuffle_data.shuffle()
            }
        }
    }

    suspend fun deleteTracks(tracks: ArrayList<Track>) {
        data.clear()

        withContext(Dispatchers.IO) {
            data.addAll(AppDatabase.getInstance(context).trackDao().getTracks())
        }

        maxIndex = data.lastIndex
        var indexOfCurrentTrack = data.indexOfFirst { it.uri == currentUri }
        // track that was playing is deleted
        if (indexOfCurrentTrack == -1) {
            if (currentItemIndex > maxIndex)
                currentItemIndex = maxIndex
        } else {
            currentItemIndex = indexOfCurrentTrack
        }

        val isAlreadyShuffled = shuffle_stack.size == 1 && shuffle_stack[0].uri == currentUri
        if (shuffle || isAlreadyShuffled) {
            shuffle_data.removeAll(tracks)
            shuffle_stack.removeAll(tracks)
            indexOfCurrentTrack = shuffle_stack.indexOfFirst { it.uri == currentUri }
            if (indexOfCurrentTrack == -1) {
                if (stack_index > shuffle_stack.lastIndex)
                    stack_index = shuffle_stack.lastIndex
            } else {
                stack_index = indexOfCurrentTrack
            }
        }
    }

    fun getNext(): Track? {
        if (shuffle) {
            return getNextOnShuffle()
        }

        if (currentItemIndex == maxIndex)
            currentItemIndex = 0
        else
            currentItemIndex++

        return getCurrent()
    }

    private fun getNextOnShuffle(): Track {
        var track: Track? = null
        if (stack_index == shuffle_stack.size - 1) {
            if (shuffle_data.isEmpty())
                refreshShuffleData()
            track = shuffle_data.removeAt(0)
            shuffle_stack.add(track)
            stack_index++
        } else {
            track = shuffle_stack[++stack_index]
        }

        currentUri = track.uri
        currentItemIndex = data.indexOf(track)

        return track
    }

    private fun refreshShuffleData() {
        shuffle_data.addAll(shuffle_stack)
        shuffle_data.shuffle()
        shuffle_stack.clear()
        shuffle_stack.add(shuffle_data.removeAt(0))
        stack_index = 0
    }

    fun getPrevious(): Track? {
        if (shuffle)
            return getPreviousOnShuffle()

        if (currentItemIndex == 0)
            currentItemIndex = maxIndex
        else
            currentItemIndex--
        return getCurrent()
    }

    private fun getPreviousOnShuffle(): Track {
        if (stack_index != 0) stack_index--

        val track = shuffle_stack[stack_index]

        currentUri = track.uri
        currentItemIndex = data.indexOf(track)

        return track
    }

    fun getAtPosition(pos: Int): Track {
        val track = data[pos]
        if (shuffle) {
            if (track in shuffle_stack) {
                shuffle_stack.remove(track)
                shuffle_stack.add(track)
            } else {
                shuffle_data.remove(track)
                shuffle_stack.add(track)
            }
            stack_index = shuffle_stack.lastIndex
        }

        currentItemIndex = pos
        currentUri = track.uri

        return track
    }

    fun getCurrent(): Track? {
        if (maxIndex == -1) return null
        currentUri = data[currentItemIndex].uri

        return data[currentItemIndex]
    }

    fun isEnded(): Boolean {
        return if (shuffle) {
            shuffle_data.isEmpty()
        } else {
            currentItemIndex == maxIndex
        }
    }
}