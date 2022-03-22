package com.kmem.myplayer.feature_playlist.presentation.adapters

import android.annotation.SuppressLint
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.R
import com.kmem.myplayer.core.domain.model.Track
import com.kmem.myplayer.databinding.AudioListItemBinding
import com.kmem.myplayer.feature_playlist.presentation.adapters.diffUtils.PlaylistAdapterDiffUtil
import com.kmem.myplayer.feature_playlist.presentation.helpers.ItemTouchHelperAdapter
import java.util.*

/**
 * @param listener Listener for adapter callbacks
 */
class PlaylistAdapter(
    private val listener: Listener? = null
) :
    RecyclerView.Adapter<PlaylistAdapter.ViewHolder>(), ItemTouchHelperAdapter {

    interface Listener {
        var currentUri: Uri
        var selectedCheckboxesPositions: ArrayList<Int>

        fun onAudioClick(position: Int)
        fun updatePositions()
    }

    private var tracks = emptyList<Track>()

    override fun getItemCount(): Int = tracks.size

    var deleteMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.audio_list_item, parent, false)
        val binding = AudioListItemBinding.bind(view)
        return ViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(tracks[position], position, deleteMode, listener)
    }

    class ViewHolder(private val binding: AudioListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(item: Track, position: Int, deleteMode: Boolean, listener: Listener?) {
            val positionString = (position + 1).toString() + "."
            binding.positionTv.text = positionString

            if (item.title == Track.UNKNOWN || item.artist == Track.UNKNOWN) {
                binding.titleTv.text = item.title
            } else {
                binding.titleTv.text = "${item.artist} - ${item.title}"
            }

            val mins = item.duration / 1000 / 60
            val secs = item.duration / 1000 % 60
            val duration =
                if (secs < 10) "$mins:0$secs" else "$mins:$secs" // "0:00" duration format
            binding.durationTv.text = duration

            if (listener != null) {
                val positions = listener.selectedCheckboxesPositions
                binding.deleteCheckbox.isChecked = position in positions
                binding.deleteCheckbox.setOnClickListener {
                    if (position in positions) {
                        positions.remove(position)
                    } else {
                        positions.add(position)
                    }
                }

                if (deleteMode) {
                    binding.deleteCheckbox.visibility = View.VISIBLE
                } else {
                    binding.deleteCheckbox.visibility = View.GONE
                }

                binding.root.setOnClickListener { listener.onAudioClick(position) }
                binding.root.isSelected = item.uri == listener.currentUri
            }
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition > toPosition) {
            for (i in toPosition until fromPosition) {
                tracks[i].position = i + 1
                tracks[i + 1].position = i
                Collections.swap(tracks, i, i + 1)
            }
        } else if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                tracks[i].position = i + 1
                tracks[i + 1].position = i
                Collections.swap(tracks, i, i + 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun setData(newAudioList: List<Track>) {
        val diffUtil = PlaylistAdapterDiffUtil(tracks, newAudioList)
        val diffResults = DiffUtil.calculateDiff(diffUtil)
        tracks = newAudioList
        diffResults.dispatchUpdatesTo(this)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun updatePositions(from: Int, to: Int) {
        notifyDataSetChanged()
        listener?.updatePositions()
    }
}