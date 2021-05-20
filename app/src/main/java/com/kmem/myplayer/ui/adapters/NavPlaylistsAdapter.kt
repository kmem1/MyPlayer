package com.kmem.myplayer.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.R
import com.kmem.myplayer.data.Playlist

class NavPlaylistsAdapter(private val playlists: ArrayList<Playlist>) : RecyclerView.Adapter<NavPlaylistsAdapter.ViewHolder>() {

    class ViewHolder(val playlistView: LinearLayout) : RecyclerView.ViewHolder(playlistView)

    var listener: Listener? = null

    interface Listener {
        fun onPlaylistClicked(playlistId: Int)
        fun onDeletePlaylistButtonClicked(playlist: Playlist)
    }

    override fun getItemCount(): Int = playlists.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val navItemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.nav_playlist_item, parent, false) as LinearLayout
        return ViewHolder(navItemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val view = holder.playlistView
        val currentPlaylist = playlists[position]

        val playlistNameView = view.findViewById<TextView>(R.id.playlist_name)
        playlistNameView.text = currentPlaylist.name

        val deletePlaylistButton = view.findViewById<ImageButton>(R.id.delete_playlist_button)

        deletePlaylistButton.setOnClickListener {
            listener?.onDeletePlaylistButtonClicked(currentPlaylist)
        }
        view.setOnClickListener { listener?.onPlaylistClicked(currentPlaylist.playlistId) }
    }

}