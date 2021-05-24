package com.kmem.myplayer.ui.fragments

import android.Manifest
import android.content.ComponentName
import android.content.Context
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kmem.myplayer.MyApplication
import com.kmem.myplayer.R
import com.kmem.myplayer.data.*
import com.kmem.myplayer.service.PlayerService
import com.kmem.myplayer.ui.MainActivity
import com.kmem.myplayer.ui.adapters.PlaylistAdapter
import com.kmem.myplayer.ui.helpers.PlaylistItemTouchHelperCallback
import kotlinx.coroutines.*

/**
 *  Фрагмент экрана управления плейлистом.
 *  Отвечает за графические элементы и взаимодействие с пользователем.
 *  Получает информацию от сервиса через PlayerServiceBinder.
 *  Использует PlaylistViewModel для получения данных из БД.
 */

class PlaylistFragment : Fragment(), PlaylistAdapter.Listener {

    interface Repository {
        suspend fun getTracksFromPlaylist(context: Context, playlistId: Int): LiveData<List<Track>>
        suspend fun getPlaylistName(context: Context, playlistId: Int): String
        suspend fun getPlaylistState(context: Context, playlistId: Int): PlaylistState
        fun deleteTracks(context: Context, tracks: ArrayList<Track>, playlistId: Int)
        fun updatePositions(context: Context, tracks: ArrayList<Track>)
    }

    companion object {
        const val PERMISSION_STRING = Manifest.permission.READ_EXTERNAL_STORAGE
        const val PERMISSION_CODE = 596
    }

    private var audios: ArrayList<Track> = ArrayList()
    private lateinit var repository: Repository
    private lateinit var list: RecyclerView
    private lateinit var touchHelper: ItemTouchHelper
    private lateinit var layout: View
    private var playlistId: Int = 0
    private var playlistState: PlaylistState = PlaylistState(Uri.EMPTY, 0)
    private var playerService: PlayerService? = null
    private var playerServiceBinder: PlayerService.PlayerServiceBinder? = null
    private var mediaController: MediaControllerCompat? = null
    private var callback: MediaControllerCompat.Callback? = null
    private var serviceConnection: ServiceConnection? = null

    override var currentUri: Uri = Uri.EMPTY
    override var deleteMode: Boolean = false
    override var selectedCheckboxesPositions: ArrayList<Int> = ArrayList()

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        layout = inflater.inflate(R.layout.fragment_playlist, container, false)

        playlistId = arguments?.getInt("playlist_id") ?: 0
        (activity as MainActivity).lastOpenedPlaylistId = playlistId

        repository = MusicRepository.getInstance()

        list = layout.findViewById<View>(R.id.songs_list) as RecyclerView
        val adapter = PlaylistAdapter(audios)
        val touchCallback = PlaylistItemTouchHelperCallback(adapter)
        touchHelper = ItemTouchHelper(touchCallback)
        touchHelper.attachToRecyclerView(list)
        adapter.listener = this@PlaylistFragment
        list.adapter = adapter
        list.layoutManager = LinearLayoutManager(context)

        val addButton = layout.findViewById<ImageButton>(R.id.add_tracks)
        val removeButton = layout.findViewById<ImageButton>(R.id.remove_tracks)
        val deleteTracksButton = layout.findViewById<ImageButton>(R.id.delete_tracks)
        val selectAllButton = layout.findViewById<ImageButton>(R.id.select_all)

        addButton.setOnClickListener(addButtonClickListener)
        removeButton.setOnClickListener(removeButtonClickListener)
        deleteTracksButton.setOnClickListener(deleteTracksButtonClickListener)
        selectAllButton.setOnClickListener(selectAllButtonClickListener)

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
                    mediaController = playerServiceBinder!!.getMediaSessionToken()
                        ?.let { MediaControllerCompat(activity, it) }
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

        activity?.bindService(
            Intent(activity, PlayerService::class.java),
            serviceConnection!!,
            BIND_AUTO_CREATE
        )

        setupToolbar()
        getPlaylistState()

        return layout
    }


    override fun onResume() {
        super.onResume()
        MainScope().launch {
            observeTracksFromRepository()
        }
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
    
    private val removeButtonClickListener = View.OnClickListener {
        deleteMode = !deleteMode
        onDeleteModeChanged()
        list.adapter?.notifyDataSetChanged()
    }

    private val deleteTracksButtonClickListener = View.OnClickListener {
        val tracks = ArrayList<Track>()
        for (pos in selectedCheckboxesPositions)
            tracks.add(audios[pos])

        deleteMode = false
        onDeleteModeChanged()

        repository.deleteTracks(requireContext(), tracks, playlistId)
    }

    private val selectAllButtonClickListener = View.OnClickListener {
        if (selectedCheckboxesPositions.size == audios.size) {
            selectedCheckboxesPositions.clear()
        } else {
            for (pos in 0 until audios.size)
                if (pos !in selectedCheckboxesPositions)
                    selectedCheckboxesPositions.add(pos)
        }

        list.adapter?.notifyDataSetChanged()
    }

    private val addButtonClickListener = View.OnClickListener {
        if (checkPermission()) {
            val bundle = Bundle()
            bundle.putInt("playlist_id", playlistId)
            findNavController().navigate(R.id.action_playlist_to_filechooser, bundle)
        }
        else {
            requestPermission()
        }
    }

    private val uriObserver = Observer<Uri> { newUri ->
        currentUri = newUri
        list.adapter?.notifyDataSetChanged()
    }

    private fun setupToolbar() {
        val toolbar = layout.findViewById<Toolbar>(R.id.toolbar)

        MainScope().launch {
            setToolbarTitle()
        }

        val drawer = activity?.findViewById<DrawerLayout>(R.id.drawer)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        drawer?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

        val toggle = ActionBarDrawerToggle(
            activity,
            drawer,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )

        drawer?.addDrawerListener(toggle)
        toggle.isDrawerIndicatorEnabled = true
        toggle.syncState()
    }

    private fun getPlaylistState() {
        MainScope().launch {
            val state: PlaylistState
            withContext(Dispatchers.IO) {
                state = repository.getPlaylistState(requireContext(), playlistId)
            }
            playlistState = state
            currentUri = state.uri
        }
    }

    private suspend fun setToolbarTitle() {
        val title = layout.findViewById<TextView>(R.id.playlist_name_toolbar)
        var name: String
        withContext(Dispatchers.IO) {
            name = repository.getPlaylistName(requireContext(), playlistId)
        }
        title.text = name
    }

    private suspend fun observeTracksFromRepository() {
        var liveData: LiveData<List<Track>>?

        withContext(Dispatchers.IO) {
            liveData = repository.getTracksFromPlaylist(requireContext(), playlistId)
            delay(150)
        }

        liveData?.observe(viewLifecycleOwner) {
            audios.clear()
            audios.addAll(it)

            list.adapter?.notifyDataSetChanged()
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            PERMISSION_STRING
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), PERMISSION_STRING)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(PERMISSION_STRING),
                PERMISSION_CODE
            )
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                val bundle = Bundle()
                bundle.putInt("playlist_id", playlistId)
                findNavController().navigate(R.id.action_playlist_to_filechooser, bundle)
            } else {
                Toast.makeText(
                    context,
                    getString(R.string.on_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    }

    override fun onAudioClick(position: Int) {
        val bundle = Bundle()
        bundle.putParcelable(PlayerService.EXTRA_TRACK, audios[position])

        if (playlistId != MyApplication.getCurrentPlaylistIdFromPreferences() &&
            audios[position].uri == playlistState.uri) {
                bundle.putInt(PlayerService.EXTRA_POSITION, playlistState.position)
        } else {
            bundle.putInt(PlayerService.EXTRA_POSITION, 0)
        }


        mediaController?.transportControls?.sendCustomAction(
            PlayerService.ACTION_PLAY_SELECTED_TRACK,
            bundle
        )
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun updatePositions() {
        repository.updatePositions(requireContext(), audios)
    }

    /*
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data == null) return
        val paths = data.getStringArrayListExtra(FileChooserFragment.PATHS) ?: ArrayList<String>()
        if (paths.size == 0) return
        MainScope().launch {
            val loadingSpinner = layout.findViewById<ProgressBar>(R.id.progress_bar)
            loadingSpinner.visibility = View.VISIBLE
            model.addTracks(paths)
            playerService?.addNewTracks()
            loadingSpinner.visibility = View.GONE
        }
    }
 */

}