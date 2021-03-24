package com.kmem.myplayer.ui.adapters

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.R
import com.kmem.myplayer.data.Track
import com.kmem.myplayer.ui.helpers.ItemTouchHelperAdapter
import java.util.*
import kotlin.collections.ArrayList

class PlaylistAdapter(private val audios: ArrayList<Track>): RecyclerView.Adapter<PlaylistAdapter.ViewHolder>()
                                                                                    , ItemTouchHelperAdapter {
    private var listener : Listener? = null

    interface Listener {
        var currentUri : Uri

        fun onClick(position: Int)
        fun updatePositions()
    }

    fun setListener(listener : Listener?) {
        this.listener = listener
    }

    class ViewHolder(val audioView: LinearLayout) : RecyclerView.ViewHolder(audioView)

    override fun getItemCount(): Int = audios.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val audioView = LayoutInflater.from(parent.context)
                .inflate(R.layout.audio_list_item, parent, false) as LinearLayout
        return ViewHolder(audioView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val audioView = holder.audioView
        val currAudio = audios[position]
        val positionView = audioView.findViewById<TextView>(R.id.position)
        val titleView = audioView.findViewById<TextView>(R.id.title)
        val durationView = audioView.findViewById<TextView>(R.id.duration)
        val positionString = (position + 1).toString() + "."
        positionView.text = positionString
        if (currAudio.title == "Unknown" || currAudio.artist == "Unknown") {
            titleView.text = currAudio.title
        } else {
            val correctTitle = currAudio.artist + " - " + currAudio.title
            titleView.text = correctTitle
        }
        val mins = currAudio.duration / 1000 / 60
        val secs = currAudio.duration / 1000 % 60
        val duration = if (secs < 10) "$mins:0$secs" else "$mins:$secs" // "0:00" duration format
        durationView.text = duration

        audioView.setOnClickListener { listener?.onClick(position) }

        audioView.isSelected = currAudio.uri == listener?.currentUri
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        Log.d("qwe", "$fromPosition $toPosition")

        if (fromPosition > toPosition) {
            for (i in toPosition until fromPosition) {
                audios[i].position = i + 1
                audios[i+1].position = i
                Collections.swap(audios, i, i + 1)
            }
        } else if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                audios[i].position = i + 1
                audios[i+1].position = i
                Collections.swap(audios, i, i + 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun updatePositions(from: Int, to: Int) {
        notifyDataSetChanged()
        listener?.updatePositions()
    }
}