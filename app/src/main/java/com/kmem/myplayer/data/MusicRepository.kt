package com.kmem.myplayer.data

import com.kmem.myplayer.service.PlayerService
import com.kmem.myplayer.viewmodels.PlaylistViewModel


class MusicRepository : PlayerService.Repository, PlaylistViewModel.Repository {

    companion object {
        private var instance: MusicRepository? = null

        fun getInstance(): MusicRepository {
            return instance ?: MusicRepository().also { instance = it }
        }
    }

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

    override fun addTracks(tracks: ArrayList<Track>) {
        TODO("Not yet implemented")
    }

    override fun deleteTracks(tracks: ArrayList<Track>) {
        TODO("Not yet implemented")
    }

    override fun updatePositions() {
        TODO("Not yet implemented")
    }

    override fun addNewTracks() {
        TODO("Not yet implemented")
    }

}