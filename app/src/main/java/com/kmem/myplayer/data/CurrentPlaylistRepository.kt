package com.kmem.myplayer.data

import android.net.Uri
import com.kmem.myplayer.MyApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CurrentPlaylistRepository {

    private var data: ArrayList<Track> = ArrayList()
    private var shuffleData: ArrayList<Track> = ArrayList() // additional copy for shuffle mode
    private var shuffleStack: ArrayList<Track> = ArrayList() // stack trace for shuffle mode
    private var stackIndex = 0
    private var maxIndex = 0
    private var playlistId = MyApplication.getCurrentPlaylistIdFromPreferences()
    var currentUri: Uri? = null
    var currentItemIndex = 0
    var shuffle = MyApplication.getShuffleModeFromPreferences()
        set(value) {
            field = value
            val isAlreadyShuffled = shuffleStack.size == 1 && shuffleStack[0].uri == currentUri
            if (value && !isAlreadyShuffled) {
                shuffleData.clear()
                shuffleData.addAll(data)
                shuffleData.shuffle()
                shuffleStack.clear()
                val currTrack = data.first { it.uri == currentUri }
                shuffleStack.add(currTrack)
                shuffleData.remove(currTrack)
                stackIndex = 0
                MainScope().launch {
                    removeStackPositionsInDatabase()
                    currTrack.positionInStack = stackIndex
                    updateTrackInDatabase(currTrack)
                }
            }
        }

    init {
        MainScope().launch {
            withContext(Dispatchers.IO) {
                data.addAll(
                    AppDatabase.getInstance(MyApplication.context())
                        .trackDao().getTracksFromPlaylist(playlistId)
                )
                maxIndex = data.lastIndex

                if (shuffle) {
                    shuffleData.addAll(data)
                    shuffleStack.addAll(
                        AppDatabase.getInstance(MyApplication.context())
                            .trackDao().getShuffleStackForPlaylist(playlistId)
                    )
                    stackIndex = shuffleStack.lastIndex
                    shuffleData.removeAll(shuffleStack)
                    val currTrack = shuffleStack[stackIndex]
                    currentUri = currTrack.uri
                    currentItemIndex = data.indexOfFirst { it.uri == currentUri }
                }
            }
        }
    }

    suspend fun updatePositions() {
        withContext(Dispatchers.IO) {
            data.clear()
            data.addAll(
                AppDatabase.getInstance(MyApplication.context()).
                trackDao().getTracksFromPlaylist(playlistId)
            )
            currentItemIndex = data.indexOfFirst { it.uri == currentUri }
            if (currentItemIndex == -1)
                currentItemIndex = 0
        }
    }

    suspend fun addNewTracks() {
        withContext(Dispatchers.IO) {
            data.clear()
            data.addAll(AppDatabase.getInstance(MyApplication.context())
                .trackDao().getTracksFromPlaylist(playlistId)
            )
            maxIndex = data.lastIndex

            val isAlreadyShuffled = shuffleStack.size == 1 && shuffleStack[0].uri == currentUri
            if (shuffle || isAlreadyShuffled) {
                shuffleData.clear()
                shuffleData.addAll(data)
                shuffleData.removeAll(shuffleStack)
                shuffleData.shuffle()
            }
        }
    }

    suspend fun deleteTracks(tracks: ArrayList<Track>) {
        data.clear()

        withContext(Dispatchers.IO) {
            data.addAll(AppDatabase.getInstance(MyApplication.context())
                .trackDao().getTracksFromPlaylist(playlistId)
            )
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

        val isAlreadyShuffled = shuffleStack.size == 1 && shuffleStack[0].uri == currentUri
        if (shuffle || isAlreadyShuffled) {
            shuffleData.removeAll(tracks)
            shuffleStack.removeAll(tracks)
            indexOfCurrentTrack = shuffleStack.indexOfFirst { it.uri == currentUri }
            if (indexOfCurrentTrack == -1) {
                if (stackIndex > shuffleStack.lastIndex)
                    stackIndex = shuffleStack.lastIndex
            } else {
                stackIndex = indexOfCurrentTrack
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
        val track: Track?
        if (stackIndex == shuffleStack.size - 1) {
            if (shuffleData.isEmpty())
                refreshShuffleData()
            track = shuffleData.removeAt(0)
            shuffleStack.add(track)
            stackIndex++
            track.positionInStack = stackIndex
            updateTrackInDatabase(track)
        } else {
            track = shuffleStack[++stackIndex]
        }

        currentUri = track.uri
        currentItemIndex = data.indexOf(track)

        return track
    }

    private fun updateTrackInDatabase(track: Track) {
        MainScope().launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(MyApplication.context()).trackDao().updateTrack(track)
            }
        }
    }

    private suspend fun removeStackPositionsInDatabase() {
        for (track in data) {
            track.positionInStack = -1
        }

        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(MyApplication.context()).trackDao().updateAll(data)
        }
    }

    private fun refreshShuffleData() {
        shuffleData.addAll(shuffleStack)
        shuffleData.shuffle()
        shuffleStack.clear()
        shuffleStack.add(shuffleData.removeAt(0))
        stackIndex = 0
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
        if (stackIndex != 0) stackIndex--

        val track = shuffleStack[stackIndex]

        currentUri = track.uri
        currentItemIndex = data.indexOf(track)

        return track
    }

    fun getCurrent(): Track? {
        if (maxIndex == -1) return null
        currentUri = data[currentItemIndex].uri

        return data[currentItemIndex]
    }

    fun updateCurrentPlaylist(id: Int, pos: Int) {
        MainScope().launch {
            val track: Track
            if (id != playlistId) {
                playlistId = id
                MyApplication.setCurrentPlaylistIdInPreferences(id)
                withContext(Dispatchers.IO) {
                    data.clear()
                    data.addAll(AppDatabase.getInstance(MyApplication.context())
                        .trackDao().getTracksFromPlaylist(id))
                    maxIndex = data.lastIndex
                    shuffleStack.clear()
                    shuffleData.clear()
                }
                track = data[pos]
                if (shuffle) {
                    val stackFromDatabase = ArrayList<Track>()
                    withContext(Dispatchers.IO) {
                        stackFromDatabase.addAll(AppDatabase.getInstance(MyApplication.context())
                            .trackDao().getShuffleStackForPlaylist(playlistId))
                    }
                    shuffleStack.addAll(stackFromDatabase)
                    shuffleStack.remove(track)
                    shuffleStack.add(track)
                    stackIndex = shuffleStack.lastIndex
                    shuffleData.clear()
                    shuffleData.addAll(data)
                    shuffleData.removeAll(shuffleStack)
                }
            } else {
                track = data[pos]
                if (shuffle) {
                    if (track in shuffleStack) {
                        shuffleStack.remove(track)
                        shuffleStack.add(track)
                    } else {
                        shuffleData.remove(track)
                        shuffleStack.add(track)
                    }
                    stackIndex = shuffleStack.lastIndex
                }
            }

            currentItemIndex = pos
            currentUri = track.uri
        }
    }

    fun isEnded(): Boolean {
        return if (shuffle) {
            shuffleData.isEmpty()
        } else {
            currentItemIndex == maxIndex
        }
    }

}