package com.kmem.myplayer.feature_playlist.presentation.fragments

import android.Manifest
import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.kmem.myplayer.MyApplication
import com.kmem.myplayer.R
import com.kmem.myplayer.core.domain.model.PlaylistState
import com.kmem.myplayer.core.domain.model.Track
import com.kmem.myplayer.core.presentation.MainActivity
import com.kmem.myplayer.core_utils.ui.base.BaseBindingFragment
import com.kmem.myplayer.databinding.FragmentPlaylistBinding
import com.kmem.myplayer.feature_player.service.PlayerService
import com.kmem.myplayer.feature_playlist.presentation.adapters.PlaylistAdapter
import com.kmem.myplayer.feature_playlist.presentation.helpers.PlaylistItemTouchHelperCallback
import com.kmem.myplayer.feature_playlist.presentation.viewmodels.PlaylistViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 *  Фрагмент экрана управления плейлистом.
 *  Отвечает за графические элементы и взаимодействие с пользователем.
 *  Получает информацию от сервиса через PlayerServiceBinder.
 *  Использует PlaylistViewModel для получения данных из БД.
 */

@AndroidEntryPoint
class PlaylistFragment :
    BaseBindingFragment<FragmentPlaylistBinding, PlaylistViewModel>(R.layout.fragment_playlist),
    PlaylistAdapter.Listener {

    companion object {
        const val PERMISSION_STRING = Manifest.permission.READ_EXTERNAL_STORAGE
    }

    override val vm: PlaylistViewModel by viewModels()

    private var currentTrackFlow: StateFlow<Track?>? = null

    private val args: PlaylistFragmentArgs by navArgs()

    private var playlistAdapter: PlaylistAdapter? = null
    private var touchHelper: ItemTouchHelper? = null

    private var playlistId: Int = 0
    private var playlistState: PlaylistState = PlaylistState(Uri.EMPTY, 0)

    private var playerService: PlayerService? = null
    private var playerServiceBinder: PlayerService.PlayerServiceBinder? = null
    private var serviceConnection: ServiceConnection? = null

    private var mediaController: MediaControllerCompat? = null
    private var callback: MediaControllerCompat.Callback? = null

    override var currentUri: Uri = Uri.EMPTY
    override var selectedCheckboxesPositions: ArrayList<Int> = ArrayList()

    override fun onInitBinding(binding: FragmentPlaylistBinding, savedInstanceState: Bundle?) {
        playlistId = args.playlistId
        (activity as MainActivity).lastOpenedPlaylistId = playlistId
        setupService()
        setupAdapter()
        setupListeners()
        setupViewModel()
        setupToolbar()
        getPlaylistState()
        observeTracksFromViewModel()
    }

    override fun onStart() {
        super.onStart()

        // User can disable permission in app settings
        // We should check it for playing tracks
        if (MyApplication.wasPermissionAlreadyGranted() && !isPermissionGranted(PERMISSION_STRING)) {
            requestPermissionForPlayingTracksLauncher.launch(PERMISSION_STRING)
        }
    }

    private fun setupListeners() {
        binding.addTracksBtn.setOnClickListener(addButtonClickListener)
        binding.deleteTracksBtn.setOnClickListener(deleteTracksButtonClickListener)
        binding.selectAllBtn.setOnClickListener(selectAllButtonClickListener)
    }

    private fun setupViewModel() {
        lifecycleScope.launchWhenCreated {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.deleteMode.collectLatest { value ->
                    onDeleteModeChanged(value)
                }
            }
        }
    }

    private fun setupAdapter() {
        playlistAdapter = PlaylistAdapter(this)
        val touchCallback = PlaylistItemTouchHelperCallback(playlistAdapter!!)
        touchHelper = ItemTouchHelper(touchCallback)
        touchHelper!!.attachToRecyclerView(binding.songsListRv)
        binding.songsListRv.adapter = playlistAdapter
        binding.songsListRv.layoutManager = LinearLayoutManager(context)
    }

    private fun setupService() {
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
                    currentTrackFlow = playerServiceBinder?.getCurrentTrackFlow()
                    observeCurrentTrackFromService()
                    callback?.onPlaybackStateChanged(mediaController?.playbackState)
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
    }

    private fun observeCurrentTrackFromService() {
        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                currentTrackFlow?.collectLatest { track ->
                    if (track != null) {
                        val oldTrackPosition = vm.getTrackPositionByUri(currentUri)
                        val newTrackPosition = vm.getTrackPositionByUri(track.uri)
                        currentUri = track.uri
                        binding.songsListRv.adapter?.notifyItemChanged(newTrackPosition)
                        binding.songsListRv.adapter?.notifyItemChanged(oldTrackPosition)
                    }
                }
            }
        }
    }

    private val deleteTracksButtonClickListener = View.OnClickListener {
        vm.deleteTracks(requireContext(), selectedCheckboxesPositions) {
            playerService?.onTracksDeleted()
        }
    }

    private val selectAllButtonClickListener = View.OnClickListener {
        if (selectedCheckboxesPositions.size == vm.getTrackListSize()) {
            selectedCheckboxesPositions.clear()
        } else {
            for (pos in 0 until vm.getTrackListSize()) {
                if (pos !in selectedCheckboxesPositions) {
                    selectedCheckboxesPositions.add(pos)
                }
            }
        }

        binding.songsListRv.adapter?.notifyDataSetChanged()
    }

    private val addButtonClickListener = View.OnClickListener {
        if (isPermissionGranted(PERMISSION_STRING)) {
            vm.openFileChooser()
        } else {
            requestPermissionForFileChooserLauncher.launch(PERMISSION_STRING)
        }
    }

    private fun setupToolbar() {
        setToolbarTitle()

        val drawer = activity?.findViewById<DrawerLayout>(R.id.drawer)
        (activity as AppCompatActivity).setSupportActionBar(binding.toolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        drawer?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

        val toggle = ActionBarDrawerToggle(
            activity,
            drawer,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )

        drawer?.addDrawerListener(toggle)
        toggle.isDrawerIndicatorEnabled = true
        toggle.syncState()
    }

    private fun getPlaylistState() {
        lifecycleScope.launch {
            val state = vm.getPlaylistState(requireContext())

            if (state != null) {
                playlistState = state
                currentUri = state.uri
            }
        }
    }

    private fun setToolbarTitle() {
        lifecycleScope.launchWhenCreated {
            val name = vm.getPlaylistName(requireContext())
            binding.playlistNameToolbarTv.text = name ?: getString(R.string.unknown)
        }
    }

    private fun observeTracksFromViewModel() {
        lifecycleScope.launchWhenCreated {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val context = this@PlaylistFragment.requireContext()
                vm.getTracks(context).collectLatest { tracks ->
                    playlistAdapter?.setData(tracks)
                }
            }
        }
    }

    private fun onDeleteModeChanged(value: Boolean) {
        if (value) {
            touchHelper?.attachToRecyclerView(null)
        } else {
            touchHelper?.attachToRecyclerView(binding.songsListRv)
            selectedCheckboxesPositions.clear()
        }

        playlistAdapter?.deleteMode = value
    }

    private val requestPermissionForFileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                MyApplication.setPermissionGrantedInPreferences(true)
                vm.openFileChooser()
            } else {
                vm.onPermissionDenied()
            }
        }

    private val requestPermissionForPlayingTracksLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                vm.onPermissionDenied()
            }
        }

    override fun onAudioClick(position: Int) {
        val bundle = Bundle()
        val track = vm.getTrackAtPosition(position)
        bundle.putParcelable(PlayerService.EXTRA_TRACK, track)

        if (playlistId != MyApplication.getCurrentPlaylistIdFromPreferences() &&
            track.uri == playlistState.uri
        ) {
            bundle.putInt(PlayerService.EXTRA_POSITION, playlistState.position)
        } else {
            bundle.putInt(PlayerService.EXTRA_POSITION, 0)
        }

        val oldTrackPosition = vm.getTrackPositionByUri(currentUri)
        currentUri = track.uri
        binding.songsListRv.adapter?.notifyItemChanged(track.position)
        binding.songsListRv.adapter?.notifyItemChanged(oldTrackPosition)

        mediaController?.transportControls?.sendCustomAction(
            PlayerService.ACTION_PLAY_SELECTED_TRACK,
            bundle
        )
    }

    override fun updatePositions() {
        vm.updatePositions(requireContext())
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
}