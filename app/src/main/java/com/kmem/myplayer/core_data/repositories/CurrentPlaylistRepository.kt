package com.kmem.myplayer.core_data.repositories

import android.net.Uri
import android.util.Log
import com.kmem.myplayer.MyApplication
import com.kmem.myplayer.core_data.db.AppDatabase
import com.kmem.myplayer.core_data.db.entities.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository that contains data for current playlist
 */
class CurrentPlaylistRepository {

    private var data: ArrayList<TrackEntity> = ArrayList()
    private var shuffleData: ArrayList<TrackEntity> = ArrayList() // additional copy for shuffle mode
    private var shuffleStack: ArrayList<TrackEntity> = ArrayList() // stack trace for shuffle mode
    private var stackIndex = -1
    private var maxIndex = 0
    var playlistId = MyApplication.getCurrentPlaylistIdFromPreferences()
    private var isInitialized = false
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
                    removeStackPositionsFromDatabase()
                    currTrack.positionInStack = stackIndex
                    updateStackPositionInDatabase(currTrack)
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
                    shuffleData.shuffle()
                    shuffleStack.addAll(
                        AppDatabase.getInstance(MyApplication.context())
                            .trackDao().getShuffleStackForPlaylist(playlistId)
                    )
                    if (shuffleStack.isNotEmpty()) {
                        stackIndex = shuffleStack.lastIndex
                        shuffleData.removeAll(shuffleStack)
                        val currTrack = shuffleStack[stackIndex]
                        currentUri = currTrack.uri
                        currentItemIndex = data.indexOfFirst { it.uri == currentUri }
                    }
                } else {
                    val savedState = AppDatabase.getInstance(MyApplication.context())
                        .playlistDao().getState(playlistId)
                    currentItemIndex = data.indexOfFirst { it.uri == savedState.uri }
                    if (currentItemIndex == -1) currentItemIndex = 0
                }
            }

            if (!isInitialized) {
                MusicRepository.getInstance().isInitialized.value = true
                isInitialized = true
            }
        }
    }

    /**
     * Update order of tracks
     */
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

    /**
     * Update current data with new tracks from database
     */
    suspend fun addNewTracks() {
        withContext(Dispatchers.IO) {
            data.clear()
            data.addAll(
                AppDatabase.getInstance(MyApplication.context())
                .trackDao().getTracksFromPlaylist(playlistId)
            )
            maxIndex = data.lastIndex
            currentItemIndex = 0

            val isAlreadyShuffled = shuffleStack.size == 1 && shuffleStack[0].uri == currentUri
            if (shuffle || isAlreadyShuffled) {
                shuffleData.clear()
                shuffleData.addAll(data)
                shuffleData.removeAll(shuffleStack)
                shuffleData.shuffle()
            }
        }
    }

    /**
     * Delete tracks and updates current state of repository
     * @param tracks Tracks to delete
     */
    suspend fun deleteTracks(tracks: List<TrackEntity>) {
        data.clear()

        withContext(Dispatchers.IO) {
            data.addAll(
                AppDatabase.getInstance(MyApplication.context())
                    .trackDao().getTracksFromPlaylist(playlistId)
            )
        }

        maxIndex = data.lastIndex
        var indexOfCurrentTrack = data.indexOfFirst { it.uri == currentUri }
        // track that was playing is deleted
        if (indexOfCurrentTrack == -1) {
            if (currentItemIndex > maxIndex) currentItemIndex = maxIndex
        } else {
            currentItemIndex = indexOfCurrentTrack
        }

        val isAlreadyShuffled = shuffleStack.size == 1 && shuffleStack[0].uri == currentUri
        if (shuffle || isAlreadyShuffled) {
            shuffleData.removeAll(tracks)
            shuffleStack.removeAll(tracks)
            if (shuffleStack.isEmpty()) {
                if (shuffleData.isNotEmpty()) {
                    shuffleStack.add(shuffleData.removeAt(0))
                    stackIndex = 0
                } else {
                    stackIndex = -1
                }
            } else {
                indexOfCurrentTrack = shuffleStack.indexOfFirst { it.uri == currentUri }
                if (indexOfCurrentTrack == -1) {
                    if (stackIndex > shuffleStack.lastIndex) stackIndex = shuffleStack.lastIndex
                } else {
                    stackIndex = indexOfCurrentTrack
                }
            }
            currentItemIndex = data.indexOfFirst { it.uri == shuffleStack[stackIndex].uri }

            updateStackPositionsInDatabase()
        }
    }

    /**
     * @return Next track from repository
     */
    fun getNext(): TrackEntity? {
        if (shuffle) {
            return getNextOnShuffle()
        }

        if (currentItemIndex == maxIndex)
            currentItemIndex = 0
        else
            currentItemIndex++

        return getCurrent()
    }

    /**
     * @return Next track when shuffle mode is turned on
     */
    private fun getNextOnShuffle(): TrackEntity {
        val track: TrackEntity?
        if (stackIndex == shuffleStack.size - 1) {
            var isRefreshed = false
            if (shuffleData.isEmpty()) {
                refreshShuffleData()
                isRefreshed = true
            }

            track = shuffleData.removeAt(0)
            shuffleStack.add(track)
            stackIndex++

            MainScope().launch {
                if (isRefreshed) removeStackPositionsFromDatabase()
                track.positionInStack = stackIndex
                updateStackPositionInDatabase(track)
            }
        } else {
            track = shuffleStack[++stackIndex]
        }

        currentUri = track.uri
        currentItemIndex = data.indexOfFirst { it.uri == track.uri }

        return track
    }

    /**
     * @param track Track to update
     */
    private suspend fun updateStackPositionInDatabase(track: TrackEntity) {
        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(MyApplication.context()).trackDao()
                .updateStackPositionOfTrack(track.uri, track.playlistId, track.positionInStack)
        }
    }

    /**
     * Update positions of shuffled tracks in database
     */
    private suspend fun updateStackPositionsInDatabase() {
        withContext(Dispatchers.IO) {
            for ((index, track) in shuffleStack.withIndex()) {
                track.positionInStack = index
                AppDatabase.getInstance(MyApplication.context())
                    .trackDao().updateStackPositionOfTrack(track.uri, track.playlistId, index)
            }
        }
    }

    /**
     * Remove positions of shuffled tracks in database
     */
    private suspend fun removeStackPositionsFromDatabase() {
        withContext(Dispatchers.IO) {
            for (track in data) {
                track.positionInStack = -1
                AppDatabase.getInstance(MyApplication.context()).trackDao()
                    .updateStackPositionOfTrack(track.uri, track.playlistId, track.positionInStack)
            }
        }
    }

    private fun refreshShuffleData() {
        val prevLastTrack = shuffleStack.removeLast()
        shuffleData.addAll(shuffleStack)
        shuffleData.shuffle()
        // previous last track shouldn't be first in new shuffle
        val position = if (shuffleStack.isNotEmpty()) (1..shuffleData.lastIndex+1).random() else 0
        shuffleData.add(position, prevLastTrack)
        shuffleStack.clear()
        stackIndex = -1
    }

    /**
     * @return previous track from repository
     */
    fun getPrevious(): TrackEntity? {
        if (shuffle) return getPreviousOnShuffle()

        if (currentItemIndex == 0)
            currentItemIndex = maxIndex
        else
            currentItemIndex--

        return getCurrent()
    }

    /**
     * @return previous track when shuffle mode is turned on
     */
    private fun getPreviousOnShuffle(): TrackEntity {
        if (stackIndex != 0) stackIndex--

        val track = shuffleStack[stackIndex]

        currentUri = track.uri
        currentItemIndex = data.indexOfFirst { it.uri == track.uri }

        return track
    }

    /**
     * @return Current track from repository
     */
    fun getCurrent(): TrackEntity? {
        if (maxIndex == -1 || currentItemIndex < 0 || data.isEmpty()) return null
        currentUri = data[currentItemIndex].uri
        val currentTrack = data[currentItemIndex]

        if (shuffle && shuffleStack.isEmpty()) {
            shuffleStack.add(currentTrack)
            stackIndex = 0
            currentTrack.positionInStack = 0
            MainScope().launch {
                withContext(Dispatchers.IO) {
                    updateStackPositionInDatabase(currentTrack)
                }
            }
        }

        return currentTrack
    }

    /**
     * Set data from playlist specified by id
     * Update currentTrack
     * @param id Id of playlist to set
     * @param pos Position of new current track
     */
    fun setPlaylist(id: Int, pos: Int) {
        MainScope().launch {
            val track: TrackEntity
            if (id != playlistId) {
                playlistId = id
                MyApplication.setCurrentPlaylistIdInPreferences(id)
                withContext(Dispatchers.IO) {
                    data.clear()
                    data.addAll(
                        AppDatabase.getInstance(MyApplication.context())
                        .trackDao().getTracksFromPlaylist(id))
                    maxIndex = data.lastIndex
                    shuffleStack.clear()
                    shuffleData.clear()
                }
                track = data[pos]
                if (shuffle) {
                    val stackFromDatabase = ArrayList<TrackEntity>()
                    withContext(Dispatchers.IO) {
                        stackFromDatabase.addAll(
                            AppDatabase.getInstance(MyApplication.context())
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
                        // push the new track to the top of the stack
                        shuffleStack.remove(track)
                        shuffleStack.add(0, track)
                    } else {
                        shuffleData.remove(track)
                        shuffleStack.add(track)
                    }
                    stackIndex = shuffleStack.lastIndex

                    updateStackPositionsInDatabase()
                }
            }

            currentItemIndex = pos
            currentUri = track.uri
        }
    }

    /**
     * @return True if playlist is ended
     */
    fun isEnded(): Boolean {
        return if (shuffle) {
            shuffleData.isEmpty()
        } else {
            currentItemIndex == maxIndex
        }
    }

}