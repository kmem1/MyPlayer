package com.kmem.myplayer.feature_playlist.presentation.adapters

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.R
import com.kmem.myplayer.core.domain.model.Track
import com.kmem.myplayer.feature_playlist.presentation.adapters.diffUtils.PlaylistAdapterDiffUtil
import com.kmem.myplayer.feature_playlist.presentation.helpers.ItemTouchHelperAdapter
import java.util.*
import kotlin.collections.ArrayList

/**
 * @param audios Audios to be shown in list
 * @param listener Listener for adapter callbacks
 */
class PlaylistAdapter(
    private val listener: Listener? = null
) :
    RecyclerView.Adapter<PlaylistAdapter.ViewHolder>(), ItemTouchHelperAdapter {

    interface Listener {
        var currentUri: Uri
        var deleteMode: Boolean
        var selectedCheckboxesPositions: ArrayList<Int>

        fun onAudioClick(position: Int)
        fun updatePositions()
    }

    private var tracks = emptyList<Track>()

    class ViewHolder(val audioView: LinearLayout) : RecyclerView.ViewHolder(audioView)

    override fun getItemCount(): Int = tracks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val audioView = LayoutInflater.from(parent.context)
            .inflate(R.layout.audio_list_item, parent, false) as LinearLayout
        return ViewHolder(audioView)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val audioView = holder.audioView
        val currAudio = tracks[position]
        val positionView = audioView.findViewById<TextView>(R.id.position)
        val titleView = audioView.findViewById<TextView>(R.id.title)
        val durationView = audioView.findViewById<TextView>(R.id.duration)
        val positionString = (position + 1).toString() + "."
        positionView.text = positionString
        if (currAudio.title == Track.UNKNOWN || currAudio.artist == Track.UNKNOWN) {
            titleView.text = currAudio.title
        } else {
            titleView.text = "${currAudio.artist} - ${currAudio.title}"
        }
        val mins = currAudio.duration / 1000 / 60
        val secs = currAudio.duration / 1000 % 60
        val duration = if (secs < 10) "$mins:0$secs" else "$mins:$secs" // "0:00" duration format
        durationView.text = duration

        val deleteCheckbox = audioView.findViewById<CheckBox>(R.id.delete_checkbox)
        val positions = listener!!.selectedCheckboxesPositions
        deleteCheckbox.isChecked = position in positions
        deleteCheckbox.setOnClickListener {
            if (position in positions) {
                positions.remove(position)
            } else {
                positions.add(position)
            }
        }
        if (listener.deleteMode)
            deleteCheckbox.visibility = View.VISIBLE
        else
            deleteCheckbox.visibility = View.GONE

        audioView.setOnClickListener { listener.onAudioClick(position) }
        audioView.isSelected = currAudio.uri == listener.currentUri
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
        Log.d("adapter", "MoveItem")

        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun setData(newAudioList: List<Track>) {
        val diffUtil = PlaylistAdapterDiffUtil(tracks, newAudioList)
        val diffResults = DiffUtil.calculateDiff(diffUtil)
        tracks = newAudioList
        diffResults.dispatchUpdatesTo(this)
    }

    override fun updatePositions(from: Int, to: Int) {
        notifyDataSetChanged()
        listener?.updatePositions()
    }
}