package com.kmem.myplayer.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kmem.myplayer.MyApplication
import com.kmem.myplayer.service.PlayerService
import com.kmem.myplayer.ui.MainActivity
import com.kmem.myplayer.ui.fragments.MainPlayerFragment
import com.kmem.myplayer.ui.fragments.PlaylistFragment
import com.kmem.myplayer.utils.MetadataHelper
import com.kmem.myplayer.viewmodels.FileChooserViewModel
import kotlinx.coroutines.*
import java.io.File

class MusicRepository : PlayerService.Repository,
                        PlaylistFragment.Repository,
                        FileChooserViewModel.Repository,
                        MainActivity.Repository,
                        MainPlayerFragment.Repository {

    companion object {
        private var instance: MusicRepository? = null

        fun getInstance(): MusicRepository {
            return instance ?: MusicRepository().also { instance = it }
        }
    }

    private val currentPlaylistRepository: CurrentPlaylistRepository = CurrentPlaylistRepository()

    override val isInitialized = MutableLiveData<Boolean>(false)

    override var shuffle: Boolean = MyApplication.getShuffleModeFromPreferences()
        set(value) {
            currentPlaylistRepository.shuffle = value
            field = value
        }

    override fun getCurrent(): Track? {
        return currentPlaylistRepository.getCurrent()
    }

    override fun getNext(): Track? {
        return currentPlaylistRepository.getNext()
    }

    override fun getPrevious(): Track? {
        return currentPlaylistRepository.getPrevious()
    }

    override fun updateCurrentPlaylist(playlistId: Int, position: Int) {
        currentPlaylistRepository.updateCurrentPlaylist(playlistId, position)
    }

    override fun isEnded(): Boolean {
        return currentPlaylistRepository.isEnded()
    }

    override suspend fun addPlaylist(context: Context, playlistName: String) {
        var playlist: Playlist

        withContext(Dispatchers.IO) {
            playlist = Playlist(0, playlistName, Uri.EMPTY, 0)
            AppDatabase.getInstance(context).playlistDao().insertPlaylist(playlist)
        }
    }

    override suspend fun getPlaylists(context: Context): ArrayList<Playlist> {
        val playlists: ArrayList<Playlist> = ArrayList()

        withContext(Dispatchers.IO) {
            playlists.addAll(
                AppDatabase.getInstance(context).playlistDao().getPlaylists()
            )
        }

        return playlists
    }

    override fun deletePlaylist(context: Context, playlist: Playlist) {
        MainScope().launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).playlistDao().deletePlaylist(playlist)
            }
        }
    }

    override fun deleteTracks(context: Context, tracks: ArrayList<Track>, playlistId: Int) {
        MainScope().launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).trackDao().deleteAll(tracks)

                // update positions of tracks after delete
                val tracksInPlaylist = AppDatabase.getInstance(context)
                    .trackDao().getTracksFromPlaylist(playlistId)
                val size = tracksInPlaylist.size
                for (i in 0 until size) {
                    tracksInPlaylist[i].position = i
                }

                AppDatabase.getInstance(context).trackDao().updateAll(tracksInPlaylist)
            }

            val playlistIdOfTracks = tracks[0].playlistId
            if (playlistIdOfTracks == MyApplication.getCurrentPlaylistIdFromPreferences())
                currentPlaylistRepository.deleteTracks(tracks)
        }
    }

    override fun updatePositions(context: Context, tracks: ArrayList<Track>) {
        MainScope().launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).trackDao().updateAll(tracks)
            }

            val playlistIdOfTracks = tracks[0].playlistId
            if (playlistIdOfTracks == MyApplication.getCurrentPlaylistIdFromPreferences())
                currentPlaylistRepository.updatePositions()
        }
    }

    override fun addTracks(context: Context, paths: ArrayList<String>, playlistId: Int) {
        MainScope().launch {
            val newTracks = ArrayList<Track>()

            withContext(Dispatchers.IO) {
                val currentTracks = AppDatabase.getInstance(context)
                    .trackDao().getTracksFromPlaylist(playlistId)

                val sizeOffset = currentTracks.size
                for ((position, path) in paths.withIndex()) {
                    val track = createTrackFromPath(
                        context,
                        path,
                        position + sizeOffset, // offset to positions of new tracks
                        playlistId
                    )
                    newTracks.add(track)
                }

                AppDatabase.getInstance(context).trackDao().insertAll(newTracks)
            }

            if (playlistId == MyApplication.getCurrentPlaylistIdFromPreferences())
                currentPlaylistRepository.addNewTracks()
        }
    }

    override suspend fun getTracksFromPlaylist(
        context: Context,
        playlistId: Int
    ): LiveData<List<Track>> {
        var tracks: LiveData<List<Track>>?

        withContext(Dispatchers.IO) {
            tracks = AppDatabase.getInstance(context)
                .trackDao().getTracksFromPlaylistAsLiveData(playlistId)
        }

        return tracks!!
    }

    override suspend fun getPlaylistName(context: Context, playlistId: Int): String {
        var name: String

        withContext(Dispatchers.IO) {
            val playlist = AppDatabase.getInstance(context).playlistDao().getPlaylist(playlistId)
            name = playlist.name
        }

        return name
    }

    override fun savePlaylistState(playlistId: Int, uri: Uri, position: Int) {
        MainScope().launch {
            withContext(Dispatchers.IO) {
                val playlist = AppDatabase.getInstance(MyApplication.context())
                    .playlistDao().getPlaylist(playlistId)
                playlist.lastPlayedUri = uri
                playlist.lastPlayedPosition = position

                AppDatabase.getInstance(MyApplication.context())
                    .playlistDao().updatePlaylist(playlist)
            }
        }
    }

    override suspend fun getPlaylistState(context: Context, playlistId: Int): PlaylistState {
        val state: PlaylistState

        withContext(Dispatchers.IO) {
            state = AppDatabase.getInstance(context).playlistDao().getState(playlistId)
        }

        return state
    }

    private fun createTrackFromPath(
        context: Context,
        path: String,
        position: Int,
        playlistId: Int
    ): Track {
        val uri = Uri.fromFile(File(path))
        val helper = MetadataHelper(context, uri)
        var title = helper.getTitle() ?: "Unknown"
        val artist = helper.getAuthor() ?: "Unknown"
        val fileName = File(path).name.replace(".mp3", "")
        if (title == "Unknown" || artist == "Unknown")
            title = fileName

        return Track(uri, playlistId, position, title, artist, helper.getDuration(), fileName, -1)
    }

}