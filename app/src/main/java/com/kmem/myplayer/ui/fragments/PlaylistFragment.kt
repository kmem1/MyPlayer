package com.kmem.myplayer.ui.fragments

import android.Manifest
import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.R
import com.kmem.myplayer.data.AppDatabase
import com.kmem.myplayer.data.Track
import com.kmem.myplayer.service.PlayerService
import com.kmem.myplayer.ui.activities.FileChooserActivity
import com.kmem.myplayer.ui.adapters.PlaylistAdapter
import com.kmem.myplayer.ui.helpers.PlaylistItemTouchHelperCallback
import com.kmem.myplayer.utils.MetadataHelper
import com.kmem.myplayer.viewmodels.PlaylistViewModel
import kotlinx.coroutines.*
import java.io.File

class PlaylistFragment : Fragment(), PlaylistAdapter.Listener {
    private var audios : ArrayList<Track> = ArrayList()
    private lateinit var list : RecyclerView
    private lateinit var touchHelper : ItemTouchHelper
    private lateinit var layout : View
    private var playerService : PlayerService? = null
    private var playerServiceBinder : PlayerService.PlayerServiceBinder? = null
    private var mediaController : MediaControllerCompat? = null
    private var callback : MediaControllerCompat.Callback? = null
    private var serviceConnection : ServiceConnection? = null
    private lateinit var model : PlaylistViewModel

    override var currentUri : Uri = Uri.EMPTY
    override var deleteMode : Boolean = false
    override var selectedCheckboxesPositions : ArrayList<Int> = ArrayList()

    companion object {
        const val PERMISSION_STRING = Manifest.permission.READ_EXTERNAL_STORAGE
        const val PERMISSION_CODE = 596
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        layout = inflater.inflate(R.layout.fragment_playlist, container, false)

        model = ViewModelProvider(requireActivity()).get(PlaylistViewModel::class.java)

        model.audios.observe(viewLifecycleOwner, audiosObserver)

        list = layout.findViewById<View>(R.id.songs_list) as RecyclerView
        val adapter = PlaylistAdapter(audios)
        val touchCallback = PlaylistItemTouchHelperCallback(adapter)
        touchHelper = ItemTouchHelper(touchCallback)
        touchHelper.attachToRecyclerView(list)
        adapter.setListener(this@PlaylistFragment)
        list.adapter = adapter
        list.layoutManager = LinearLayoutManager(context)

        val addButton = layout.findViewById<ImageButton>(R.id.add_tracks)
        addButton.setOnClickListener {
            val intent = Intent(context, FileChooserActivity::class.java)
            if (checkPermission())
                startActivityForResult(intent,1)
            else
                requestPermission()
        }

        val removeButton = layout.findViewById<ImageButton>(R.id.remove_tracks)
        val deleteTracksButton = layout.findViewById<ImageButton>(R.id.delete_tracks)
        val selectAllButton = layout.findViewById<ImageButton>(R.id.select_all)
        removeButton.setOnClickListener {
            deleteMode = !deleteMode
            onDeleteModeChanged()
            list.adapter?.notifyDataSetChanged()
        }

        deleteTracksButton.setOnClickListener {
            val tracks = ArrayList<Track>()
            for (pos in selectedCheckboxesPositions)
                tracks.add(audios[pos])

            deleteMode = false
            onDeleteModeChanged()

            // async update Database
            MainScope().launch {
                model.deleteTracks(tracks)
            }

            playerService?.deleteTracks(tracks)
        }

        selectAllButton.setOnClickListener {
            if (selectedCheckboxesPositions.size == audios.size) {
                selectedCheckboxesPositions.clear()
            } else {
                for (pos in 0 until audios.size)
                    if (pos !in selectedCheckboxesPositions)
                        selectedCheckboxesPositions.add(pos)
            }

            list.adapter?.notifyDataSetChanged()
        }




        callback = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                if (state == null)
                    return
            }
        }

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                playerServiceBinder = service as PlayerService.PlayerServiceBinder
                try {
                    mediaController = playerServiceBinder!!.getMediaSessionToken()?.let { MediaControllerCompat(activity, it) }
                    mediaController?.registerCallback(callback!!)
                    callback?.onPlaybackStateChanged(mediaController?.playbackState)
                    playerServiceBinder!!.getLiveUri().observe(viewLifecycleOwner, uriObserver)
                    playerService = playerServiceBinder?.getService()
                } catch (e: RemoteException) {
                    mediaController = null
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playerServiceBinder = null
                if (mediaController != null) {
                    mediaController?.unregisterCallback(callback!!)
                    mediaController = null
                }
            }
        }

        activity?.bindService(Intent(activity, PlayerService::class.java), serviceConnection!!, BIND_AUTO_CREATE)

        return layout
    }

    private val audiosObserver = Observer<ArrayList<Track>> { newAudios ->
        audios.clear()
        audios.addAll(newAudios)
        list.adapter?.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerServiceBinder = null
        if (mediaController != null) {
            mediaController?.unregisterCallback(callback!!)
            mediaController = null
        }
        activity?.unbindService(serviceConnection!!)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data == null) return
        val paths = data.getStringArrayListExtra(FileChooserActivity.PATHS) ?: ArrayList<String>()
        if (paths.size == 0) return
        MainScope().launch {
            model.addTracks(paths)
            playerService?.addNewTracks()
        }
    }

    private val uriObserver = Observer<Uri> {newUri ->
        currentUri = newUri
        list.adapter?.notifyDataSetChanged()
    }

    private fun checkPermission() : Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), PERMISSION_STRING) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), PERMISSION_STRING)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(PERMISSION_STRING),
                    PERMISSION_CODE)
        }
    }

    private fun onDeleteModeChanged() {
        val addButton = layout.findViewById<ImageButton>(R.id.add_tracks)
        val selectAllButton = layout.findViewById<ImageButton>(R.id.select_all)
        val deleteTracksButton = layout.findViewById<ImageButton>(R.id.delete_tracks)

        if (deleteMode) {
            touchHelper.attachToRecyclerView(null)
            selectAllButton.visibility = View.VISIBLE
            deleteTracksButton.visibility = View.VISIBLE
            addButton.visibility = View.GONE
        } else {
            touchHelper.attachToRecyclerView(list)
            selectAllButton.visibility = View.GONE
            deleteTracksButton.visibility = View.GONE
            addButton.visibility = View.VISIBLE
            selectedCheckboxesPositions.clear()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_CODE) {
           if (grantResults.isNotEmpty() &&
                   grantResults[0] == PackageManager.PERMISSION_GRANTED) {
               val intent = Intent(context, FileChooserActivity::class.java)
               startActivityForResult(intent,1)
           } else {
               Toast.makeText(context, getString(R.string.on_permission_denied), Toast.LENGTH_SHORT).show()
           }
        }

    }

    override fun onClick(position: Int) {
        val bundle = Bundle()
        bundle.putInt(PlayerService.EXTRA_POSITION, position)
        mediaController?.transportControls?.sendCustomAction(PlayerService.ACTION_PLAY_AT_POSITION, bundle)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun updatePositions() {
        MainScope().launch {
            withContext(Dispatchers.IO) {
                model.updatePositions(audios)
                playerService?.updatePositions()
            }
        }
    }
}