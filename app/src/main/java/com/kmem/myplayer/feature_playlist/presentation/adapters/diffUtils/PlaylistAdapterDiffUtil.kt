package com.kmem.myplayer.feature_playlist.presentation.adapters.diffUtils

import androidx.recyclerview.widget.DiffUtil
import com.kmem.myplayer.core.domain.model.Track

class PlaylistAdapterDiffUtil(
    private val oldList: List<Track>,
    private val newList: List<Track>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].uri == newList[newItemPosition].uri
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return when {
            oldList[oldItemPosition].title != newList[newItemPosition].title -> false
            oldList[oldItemPosition].artist != newList[newItemPosition].artist -> false
            oldList[oldItemPosition].duration != newList[newItemPosition].duration -> false
            oldList[oldItemPosition].position != newList[newItemPosition].position -> false
            else -> true
        }
    }
}