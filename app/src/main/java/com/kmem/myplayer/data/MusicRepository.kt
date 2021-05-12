package com.kmem.myplayer.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kmem.myplayer.service.PlayerService
import com.kmem.myplayer.ui.fragments.PlaylistFragment
import com.kmem.myplayer.utils.MetadataHelper
import com.kmem.myplayer.viewmodels.FileChooserViewModel
import com.kmem.myplayer.viewmodels.PlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class MusicRepository : PlayerService.Repository,
                        PlaylistFragment.Repository,
                        FileChooserViewModel.Repository {

    companion object {
        private var instance: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return instance ?: MusicRepository().also {
                instance = it
                it._tracks.value = ArrayList()
                MainScope().launch {
                    it._tracks.value?.addAll(AppDatabase.getInstance(context).trackDao().getTracks())
                }
            }
        }
    }

    private val _tracks: MutableLiveData<ArrayList<Track>> = MutableLiveData<ArrayList<Track>>()
    override val tracks: LiveData<ArrayList<Track>> = _tracks

    override var shuffle: Boolean = false

    override fun getCurrent(): Track? {
        TODO("Not yet implemented")
    }

    override fun getNext(): Track? {
        TODO("Not yet implemented")
    }

    override fun getPrevious(): Track? {
        TODO("Not yet implemented")
    }

    override fun getAtPosition(position: Int): Track? {
        TODO("Not yet implemented")
    }

    override fun isEnded(): Boolean {
        TODO("Not yet implemented")
    }

    override fun deleteTracks(tracks: ArrayList<Track>) {
        TODO("Not yet implemented")
    }

    override fun updatePositions(tracks: ArrayList<Track>) {
        TODO("Not yet implemented")
    }

    override fun addTracks(context: Context, paths: ArrayList<String>) {
        MainScope().launch {
            val tracks = ArrayList<Track>()
            withContext(Dispatchers.IO) {
                val sizeOffset = _tracks.value?.size ?: 0
                for ((position, path) in paths.withIndex()) {
                    val track = createTrackFromPath(
                        context,
                        path,
                        position + sizeOffset // offset to positions of new tracks
                    )
                    tracks.add(track)
                }
                AppDatabase.getInstance(context).trackDao().insertAll(tracks)
                _tracks.value?.clear()
                _tracks.value?.addAll(AppDatabase.getInstance(context).trackDao().getTracks())
            }
            _tracks.notifyObservers()
        }
    }

    private fun createTrackFromPath(context: Context, path: String, position: Int): Track {
        val uri = Uri.fromFile(File(path))
        val helper = MetadataHelper(context, uri)
        var title = helper.getTitle() ?: "Unknown"
        val artist = helper.getAuthor() ?: "Unknown"
        val fileName = File(path).name.replace(".mp3", "")
        if (title == "Unknown" || artist == "Unknown")
            title = fileName

        return Track(uri, 0, position, title, artist, helper.getDuration(), fileName)
    }

    private fun <T> MutableLiveData<T>.notifyObservers() {
        this.value = this.value
    }

}