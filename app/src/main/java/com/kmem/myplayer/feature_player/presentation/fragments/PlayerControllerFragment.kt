package com.kmem.myplayer.feature_player.presentation.fragments

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.kmem.myplayer.MyApplication
import com.kmem.myplayer.R
import com.kmem.myplayer.databinding.FragmentPlayerControllerBinding
import com.kmem.myplayer.feature_player.presentation.viewmodels.PlayerControllerViewModel
import com.kmem.myplayer.feature_player.service.PlayerService
import com.kmem.myplayer.feature_playlist.presentation.fragments.PlaylistFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


/**
 *  Фрагмент экрана состояния плеера.
 *  Отвечает за графические элементы и взаимодействие с пользователем.
 *  Получает информацию от сервиса через PlayerServiceBinder.
 */

@AndroidEntryPoint
class PlayerControllerFragment : Fragment() {

    companion object {
        private const val FROM_ALPHA = 0.3f
        private const val TO_ALPHA = 1f
        private const val PERMISSION_STRING = Manifest.permission.READ_EXTERNAL_STORAGE
        private const val PERMISSION_CODE = 600
    }

    private var _binding: FragmentPlayerControllerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerControllerViewModel by viewModels()

    private var playerServiceBinder: PlayerService.PlayerServiceBinder? = null
    private var mediaController: MediaControllerCompat? = null
    private var callback: MediaControllerCompat.Callback? = null
    private var serviceConnection: ServiceConnection? = null
    private var playerService: PlayerService? = null
    private var isPlaying = false
    private var currentState = -1
    private var durationBarJob: Job? = null
    private var isStarted = false
    private var shuffleMode = MyApplication.getShuffleModeFromPreferences()
    private var repeatMode = false

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerControllerBinding.inflate(inflater, container, false)

        binding.prevBtn.setOnClickListener(prevButtonClickListener)
        binding.playBtn.setOnClickListener(playButtonClickListener)
        binding.nextBtn.setOnClickListener(nextButtonClickListener)
        binding.shuffleBtn.setOnClickListener(shuffleButtonClickListener)
        binding.repeatBtn.setOnClickListener(repeatButtonClickListener)
        binding.toPlaylistBtn.setOnClickListener(toPlaylistButtonClickListener)

        binding.shuffleBtn.isEnabled = false
        binding.repeatBtn.isEnabled = false

        // select TextViews for sliding long text
        binding.trackTitleTv.isSelected = true
        binding.artistTv.isSelected = true

        callback = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                if (state == null) return

                currentState = state.state

                if (state.state == PlaybackStateCompat.STATE_STOPPED) {
                    clearPlayer()
                    durationBarJob?.cancel()
                    checkPlaylistExistence()
                    return
                }

                isPlaying = state.state == PlaybackStateCompat.STATE_PLAYING
                if (isPlaying) {
                    binding.playBtn.setImageResource(R.drawable.baseline_pause_24)
                    durationBarJob?.cancel()

                    if (isStarted) {
                        runDurationBarUpdate()
                    }
                } else {
                    binding.playBtn.setImageResource(R.drawable.baseline_play_arrow_24)
                    durationBarJob?.cancel()
                    val position = mediaController?.playbackState?.position?.toInt() ?: 0
                    binding.durationSb.progress = position
                    updateCurrentDurationView(position)
                }
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
                    playerService = playerServiceBinder!!.getService()
                    val metadataFlow = playerServiceBinder?.getMetadataFlow()
                    observeMetadataFromFlow(metadataFlow)
                    shuffleMode = playerService?.shuffleMode ?: false
                    repeatMode = playerService?.repeatMode ?: false
                    binding.shuffleBtn.alpha = if (shuffleMode) TO_ALPHA else FROM_ALPHA
                    binding.repeatBtn.alpha = if (repeatMode) TO_ALPHA else FROM_ALPHA
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

        checkPlaylistExistence()
        setupToolbar()
        setupDurationBarListener()
        observeCurrentTrackFromViewModel()

        return binding.root
    }

    override fun onStart() {
        super.onStart()

        if (!checkPermission()) requestPermission()

        // update duration if there is already playing track on pause
        binding.durationSb.progress = mediaController?.playbackState?.position?.toInt() ?: 0

        shuffleMode = playerService?.shuffleMode ?: false
        binding.shuffleBtn.alpha = if (shuffleMode) TO_ALPHA else FROM_ALPHA

        repeatMode = playerService?.repeatMode ?: false
        binding.repeatBtn.alpha = if (repeatMode) TO_ALPHA else FROM_ALPHA

        if (isPlaying) runDurationBarUpdate()

        isStarted = true
    }

    override fun onStop() {
        super.onStop()
        durationBarJob?.cancel()
        isStarted = false
    }

    override fun onDestroy() {
        super.onDestroy()
        playerServiceBinder = null

        if (mediaController != null) {
            callback?.let { mediaController?.unregisterCallback(callback!!) }
            mediaController = null
        }

        durationBarJob?.cancel()

        serviceConnection?.let {
            activity?.unbindService(serviceConnection!!)
        }
    }

    private fun observeMetadataFromFlow(metadataFlow: StateFlow<MediaMetadataCompat?>?) {
        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                metadataFlow?.collectLatest { newMetadata ->
                    viewModel.setMetadata(newMetadata)
                }
            }
        }
    }

    private fun observeCurrentTrackFromViewModel() {
        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentTrackInfo.collectLatest { trackInfo ->
                    if (trackInfo == null) return@collectLatest

                    if (currentState == PlaybackStateCompat.STATE_STOPPED || currentState == -1) {
                        return@collectLatest
                    }

                    binding.shuffleBtn.isEnabled = true
                    binding.repeatBtn.isEnabled = true
                    binding.durationSb.isEnabled = true

                    binding.artistTv.text = trackInfo.artist
                    binding.trackTitleTv.text = trackInfo.title
                    binding.maxDurationTv.text = trackInfo.durationString
                    binding.durationSb.max = trackInfo.duration
                    binding.albumImg.setImageBitmap(trackInfo.albumImgBitmap)
                }
            }
        }
    }

    /**
     * Hides "To playlist" button if there is no playlist created
     */
    private fun checkPlaylistExistence() {
        lifecycleScope.launch {
            if (viewModel.isPlaylistCreated(requireContext())) {
                binding.toPlaylistBtn.visibility = View.VISIBLE
            } else {
                binding.toPlaylistBtn.visibility = View.GONE
            }
        }
    }

    private fun setupToolbar() {
        val drawer = activity?.findViewById<DrawerLayout>(R.id.drawer)
        (activity as AppCompatActivity).setSupportActionBar(binding.toolbar)
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

    private val playButtonClickListener = View.OnClickListener {
        if (mediaController != null) {
            if (isPlaying) {
                mediaController?.transportControls?.pause()
            } else {
                mediaController?.transportControls?.play()
            }
        }
    }

    private val nextButtonClickListener = View.OnClickListener {
        if (mediaController != null) {
            // nullify duration
            binding.durationSb.progress = 0
            mediaController?.transportControls?.skipToNext()
        }
    }

    private val prevButtonClickListener = View.OnClickListener {
        if (mediaController != null) {
            // nullify duration
            binding.durationSb.progress = 0
            mediaController?.transportControls?.skipToPrevious()
        }
    }

    private val shuffleButtonClickListener = View.OnClickListener {
        var fromAlpha = FROM_ALPHA
        var toAlpha = TO_ALPHA
        if (shuffleMode)
            fromAlpha = toAlpha.also { toAlpha = fromAlpha }
        val alphaAnimator =
            ObjectAnimator.ofFloat(binding.shuffleBtn, View.ALPHA, fromAlpha, toAlpha)
        alphaAnimator.duration = 200
        alphaAnimator.start()
        shuffleMode = !shuffleMode
        playerService?.shuffleMode = shuffleMode
        MyApplication.setShuffleModeInPreferences(shuffleMode)
    }

    private val repeatButtonClickListener = View.OnClickListener {
        var fromAlpha = FROM_ALPHA
        var toAlpha = TO_ALPHA
        if (repeatMode)
            fromAlpha = toAlpha.also { toAlpha = fromAlpha }
        val alphaAnimator =
            ObjectAnimator.ofFloat(binding.repeatBtn, View.ALPHA, fromAlpha, toAlpha)
        alphaAnimator.duration = 200
        alphaAnimator.start()
        repeatMode = !repeatMode
        playerService?.repeatMode = repeatMode
    }

    private val toPlaylistButtonClickListener = View.OnClickListener {
        val playlistId = MyApplication.getCurrentPlaylistIdFromPreferences()

        if (playlistId == -1) {
            Toast.makeText(requireContext(), "No playlist created", Toast.LENGTH_SHORT).show()
            return@OnClickListener
        }

        val action = PlayerControllerFragmentDirections.actionPlayerToPlaylist(playlistId)

        findNavController().navigate(action)
    }

    @SuppressLint("SetTextI18n")
    private fun clearPlayer() {
        binding.artistTv.text = ""
        binding.trackTitleTv.text = ""
        binding.durationSb.isEnabled = false
        binding.durationSb.progress = 0
        updateCurrentDurationView(0)
        binding.maxDurationTv.text = "0:00"

        binding.albumImg.setImageBitmap(
            BitmapFactory.decodeResource(resources, R.drawable.without_album)
        )

        binding.shuffleBtn.isEnabled = false
        binding.repeatBtn.isEnabled = false
        isPlaying = false
        binding.repeatBtn.setImageResource(R.drawable.baseline_play_arrow_24)
    }

    private fun updateCurrentDurationView(position: Int) {
        val mins = position / 1000 / 60
        val secs = position / 1000 % 60
        val duration = if (secs < 10) "$mins:0$secs" else "$mins:$secs" // "0:00" duration format
        binding.currDurationTv.text = duration
    }

    private fun setupDurationBarListener() {
        binding.durationSb.isEnabled = false
        binding.durationSb.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            var changedDuration = 0
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mediaController?.transportControls?.seekTo(changedDuration.toLong())
                binding.durationSb.progress = changedDuration
                if (isPlaying)
                    runDurationBarUpdate(true)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                durationBarJob?.cancel()
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    changedDuration = progress
                    updateCurrentDurationView(changedDuration)
                }
            }
        })
    }

    private fun runDurationBarUpdate(onSeek: Boolean = false) {
        if (durationBarJob == null || durationBarJob!!.isCancelled) {
            durationBarJob = MainScope().launch {
                if (onSeek)
                    delay(100) // wait for exo player to seek
                while (true) {
                    val position = mediaController?.playbackState?.position?.toInt() ?: 0
                    binding.durationSb.progress = position
                    updateCurrentDurationView(position)
                    delay(500)
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        // shouldn't ask for permission if it wasn't granted before
        if (!MyApplication.wasPermissionAlreadyGranted()) return true

        return ContextCompat.checkSelfPermission(
            requireContext(),
            PERMISSION_STRING
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), PlaylistFragment.PERMISSION_STRING)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(PlaylistFragment.PERMISSION_STRING),
                PERMISSION_CODE
            )
        }
    }
}