package com.kmem.myplayer.feature_playlist.presentation.fragments

import android.Manifest
import android.annotation.SuppressLint
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.kmem.myplayer.MyApplication
import com.kmem.myplayer.R
import com.kmem.myplayer.core.domain.model.PlaylistState
import com.kmem.myplayer.core.domain.model.Track
import com.kmem.myplayer.core.presentation.MainActivity
import com.kmem.myplayer.databinding.FragmentPlaylistBinding
import com.kmem.myplayer.feature_player.service.PlayerService
import com.kmem.myplayer.feature_playlist.presentation.adapters.PlaylistAdapter
import com.kmem.myplayer.feature_playlist.presentation.helpers.PlaylistItemTouchHelperCallback
import com.kmem.myplayer.feature_playlist.presentation.viewmodels.PlaylistViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 *  Фрагмент экрана управления плейлистом.
 *  Отвечает за графические элементы и взаимодействие с пользователем.
 *  Получает информацию от сервиса через PlayerServiceBinder.
 *  Использует PlaylistViewModel для получения данных из БД.
 */

@AndroidEntryPoint
class PlaylistFragment : Fragment(), PlaylistAdapter.Listener {

    companion object {
        const val PERMISSION_STRING = Manifest.permission.READ_EXTERNAL_STORAGE
        const val PERMISSION_CODE_FOR_FILECHOOSER = 596
        const val PERMISSION_CODE_FOR_PLAYING_TRACKS = 597
    }

    private val viewModel: PlaylistViewModel by viewModels()

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    private val args: PlaylistFragmentArgs by navArgs()

    private var adapter: PlaylistAdapter? = null
    private var touchHelper: ItemTouchHelper? = null
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
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)

        playlistId = args.playlistId
        (activity as MainActivity).lastOpenedPlaylistId = playlistId

        adapter = PlaylistAdapter(this)
        val touchCallback = PlaylistItemTouchHelperCallback(adapter!!)
        touchHelper = ItemTouchHelper(touchCallback)
        touchHelper!!.attachToRecyclerView(binding.songsListRv)
        binding.songsListRv.adapter = adapter
        binding.songsListRv.layoutManager = LinearLayoutManager(context)

        binding.addTracksBtn.setOnClickListener(addButtonClickListener)
        binding.removeTracksBtn.setOnClickListener(removeButtonClickListener)
        binding.deleteTracksBtn.setOnClickListener(deleteTracksButtonClickListener)
        binding.selectAllBtn.setOnClickListener(selectAllButtonClickListener)

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
        observeTracksFromViewModel()

        return binding.root
    }

    override fun onStart() {
        super.onStart()

        // check permission on start fragment
        if (MyApplication.wasPermissionAlreadyGranted() && !checkPermission())
            requestPermission(PERMISSION_CODE_FOR_PLAYING_TRACKS)
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
        binding.songsListRv.adapter?.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private val deleteTracksButtonClickListener = View.OnClickListener {
        val tracks = ArrayList<Track>()
        for (pos in selectedCheckboxesPositions)
            tracks.add(viewModel.getTrackAtPosition(pos))

        deleteMode = false
        onDeleteModeChanged()

        if (tracks.isNotEmpty()) {
            val context = this.requireContext()
            viewModel.deleteTracks(context, tracks) { playerService?.onTracksDeleted() }
        } else {
            binding.songsListRv.adapter?.notifyDataSetChanged()
        }
    }

    private val selectAllButtonClickListener = View.OnClickListener {
        if (selectedCheckboxesPositions.size == viewModel.getTrackListSize()) {
            selectedCheckboxesPositions.clear()
        } else {
            for (pos in 0 until viewModel.getTrackListSize())
                if (pos !in selectedCheckboxesPositions)
                    selectedCheckboxesPositions.add(pos)
        }

        binding.songsListRv.adapter?.notifyDataSetChanged()
    }

    private val addButtonClickListener = View.OnClickListener {
        if (checkPermission()) {
            val action = PlaylistFragmentDirections.actionPlaylistToFilechooser(playlistId)
            findNavController().navigate(action)
        } else {
            requestPermission(PERMISSION_CODE_FOR_FILECHOOSER)
        }
    }

    private val uriObserver = Observer<Uri> { newUri ->
        if (MyApplication.getCurrentPlaylistIdFromPreferences() == playlistId) {
            currentUri = newUri
            binding.songsListRv.adapter?.notifyDataSetChanged()
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
            val state = viewModel.getPlaylistState(requireContext())

            if (state != null) {
                playlistState = state
                currentUri = state.uri
            }
        }
    }

    private fun setToolbarTitle() {
        lifecycleScope.launchWhenCreated {
            val name = viewModel.getPlaylistName(requireContext())
            binding.playlistNameToolbarTv.text = name ?: "Unknown"
        }
    }

    private fun observeTracksFromViewModel() {
        lifecycleScope.launchWhenCreated {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val context = this@PlaylistFragment.requireContext()
                viewModel.getTracks(context).collectLatest { tracks ->
                    adapter?.setData(tracks)
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            PERMISSION_STRING
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission(permissionCode: Int) {
        if (ContextCompat.checkSelfPermission(requireContext(), PERMISSION_STRING)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(PERMISSION_STRING),
                permissionCode
            )
        }
    }

    private fun onDeleteModeChanged() {
        if (deleteMode) {
            touchHelper?.attachToRecyclerView(null)
            binding.selectAllBtn.visibility = View.VISIBLE
            binding.deleteTracksBtn.visibility = View.VISIBLE
            binding.addTracksBtn.visibility = View.GONE
        } else {
            touchHelper?.attachToRecyclerView(binding.songsListRv)
            binding.selectAllBtn.visibility = View.GONE
            binding.deleteTracksBtn.visibility = View.GONE
            binding.addTracksBtn.visibility = View.VISIBLE
            selectedCheckboxesPositions.clear()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_CODE_FOR_FILECHOOSER) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                MyApplication.setPermissionGrantedInPreferences(true)
                val action = PlaylistFragmentDirections.actionPlaylistToFilechooser(playlistId)
                findNavController().navigate(action)
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
        val track = viewModel.getTrackAtPosition(position)
        bundle.putParcelable(PlayerService.EXTRA_TRACK, track)

        if (playlistId != MyApplication.getCurrentPlaylistIdFromPreferences() &&
                track.uri == playlistState.uri
        ) {
            bundle.putInt(PlayerService.EXTRA_POSITION, playlistState.position)
        } else {
            bundle.putInt(PlayerService.EXTRA_POSITION, 0)
        }

        val oldTrackPosition = viewModel.getTrackPositionByUri(currentUri)
        currentUri = track.uri
        binding.songsListRv.adapter?.notifyItemChanged(track.position)
        binding.songsListRv.adapter?.notifyItemChanged(oldTrackPosition)

        mediaController?.transportControls?.sendCustomAction(
            PlayerService.ACTION_PLAY_SELECTED_TRACK,
            bundle
        )
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun updatePositions() {
        viewModel.updatePositions(requireContext())
    }
}