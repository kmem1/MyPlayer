package com.kmem.myplayer.ui.fragments

import android.Manifest
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.kmem.myplayer.MyApplication
import com.kmem.myplayer.R
import com.kmem.myplayer.data.MusicRepository
import com.kmem.myplayer.data.Playlist
import com.kmem.myplayer.service.PlayerService
import kotlinx.coroutines.*


/**
 *  Фрагмент экрана состояния плеера.
 *  Отвечает за графические элементы и взаимодействие с пользователем.
 *  Получает информацию от сервиса через PlayerServiceBinder.
 */

class MainPlayerFragment : Fragment() {

    companion object {
        private const val FROM_ALPHA = 0.3f
        private const val TO_ALPHA = 1f
        private const val PERMISSION_STRING = Manifest.permission.READ_EXTERNAL_STORAGE
        private const val PERMISSION_CODE = 600
    }

    interface Repository {
        val isInitialized: LiveData<Boolean>

        suspend fun getPlaylists(context: Context): ArrayList<Playlist>
    }

    private val repository: Repository = MusicRepository.getInstance()
    private var playerServiceBinder: PlayerService.PlayerServiceBinder? = null
    private var mediaController: MediaControllerCompat? = null
    private var callback: MediaControllerCompat.Callback? = null
    private var serviceConnection: ServiceConnection? = null
    private var playerService: PlayerService? = null
    private lateinit var layout: View
    private var isPlaying = false
    private var currentState = -1
    private var durationBarJob: Job? = null
    private var isStarted = false
    private var shuffled = MyApplication.getShuffleModeFromPreferences()
    private var repeated = false

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        layout = inflater.inflate(R.layout.fragment_main_player, container, false)

        val prevButton = layout.findViewById<ImageView>(R.id.prev_button)
        val playButton = layout.findViewById<ImageView>(R.id.play_button)
        val nextButton = layout.findViewById<ImageView>(R.id.next_button)
        val shuffleButton = layout.findViewById<ImageButton>(R.id.shuffle_button)
        val repeatButton = layout.findViewById<ImageButton>(R.id.repeat_button)
        val toPlaylistButton = layout.findViewById<ImageButton>(R.id.to_playlist_button)

        prevButton.setOnClickListener(prevButtonClickListener)
        playButton.setOnClickListener(playButtonClickListener)
        nextButton.setOnClickListener(nextButtonClickListener)
        shuffleButton.setOnClickListener(shuffleButtonClickListener)
        repeatButton.setOnClickListener(repeatButtonClickListener)
        toPlaylistButton.setOnClickListener(toPlaylistButtonClickListener)

        MainScope().launch {
            withContext(Dispatchers.IO) {
                if (repository.getPlaylists(requireContext()).isNotEmpty()) {
                    toPlaylistButton.visibility = View.VISIBLE
                }
            }
        }

        shuffleButton.isEnabled = false
        repeatButton.isEnabled = false

        // select TextViews for sliding long text
        layout.findViewById<TextView>(R.id.track_title).isSelected = true
        layout.findViewById<TextView>(R.id.artist).isSelected = true

        callback = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                if (state == null) return

                currentState = state.state

                Log.d("qwe", "state changed ${state.state}")

                if (state.state == PlaybackStateCompat.STATE_STOPPED) {
                    clearPlayer()
                    durationBarJob?.cancel()
                    Log.d("qwe", "onStop")
                    checkPlaylistExistence()
                    return
                }

                val positionView = layout.findViewById<SeekBar>(R.id.duration_bar)
                isPlaying = state.state == PlaybackStateCompat.STATE_PLAYING
                if (isPlaying) {
                    playButton.setImageResource(R.drawable.baseline_pause_24)
                    durationBarJob?.cancel()
                    if (isStarted)
                        runDurationBarUpdate()
                } else {
                    playButton.setImageResource(R.drawable.baseline_play_arrow_24)
                    durationBarJob?.cancel()
                    val position = mediaController?.playbackState?.position?.toInt() ?: 0
                    positionView.progress = position
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
                    playerServiceBinder!!.getLiveMetadata()
                        .observe(viewLifecycleOwner, metadataObserver)
                    playerService = playerServiceBinder!!.getService()
                    shuffled = playerService?.getShuffle() ?: false
                    repeated = playerService?.repeatMode ?: false
                    shuffleButton.alpha = if (shuffled) TO_ALPHA else FROM_ALPHA
                    repeatButton.alpha = if (repeated) TO_ALPHA else FROM_ALPHA
                    observeRepositoryInitialization()
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

        return layout
    }

    override fun onStart() {
        super.onStart()

        if (!checkPermission()) requestPermission()

        val durationBar = layout.findViewById<SeekBar>(R.id.duration_bar)
        // update duration if there is already playing track on pause
        durationBar.progress = mediaController?.playbackState?.position?.toInt() ?: 0

        val shuffleButton = layout.findViewById<ImageButton>(R.id.shuffle_button)
        shuffled = playerService?.getShuffle() ?: false
        shuffleButton.alpha = if (shuffled) TO_ALPHA else FROM_ALPHA

        val repeatButton = layout.findViewById<ImageButton>(R.id.repeat_button)
        repeated = playerService?.repeatMode ?: false
        repeatButton.alpha = if (repeated) TO_ALPHA else FROM_ALPHA

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
            mediaController?.unregisterCallback(callback!!)
            mediaController = null
        }
        durationBarJob?.cancel()
        activity?.unbindService(serviceConnection!!)
    }

    /**
     * Hides "To playlist" button if there is no playlist created
     */
    private fun checkPlaylistExistence() {
        val toPlaylistButton = layout.findViewById<ImageButton>(R.id.to_playlist_button)
        val playlists = ArrayList<Playlist>()
        MainScope().launch {
            withContext(Dispatchers.IO) {
                playlists.addAll(repository.getPlaylists(requireContext()))
            }
            if (playlists.isEmpty()) toPlaylistButton.visibility = View.GONE
        }
    }

    private fun setupToolbar() {
        val toolbar = layout.findViewById<Toolbar>(R.id.toolbar)
        val drawer = activity?.findViewById<DrawerLayout>(R.id.drawer)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
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

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun observeRepositoryInitialization() {
        repository.isInitialized.observe(viewLifecycleOwner) { value ->
            if (value) {
                playerService?.onRepositoryInitialized()
            }
        }
    }

    private val playButtonClickListener = View.OnClickListener {
        if (mediaController != null) {
            if (isPlaying)
                mediaController?.transportControls?.pause()
            else
                mediaController?.transportControls?.play()
        }
    }

    private val nextButtonClickListener = View.OnClickListener {
        if (mediaController != null) {
            // nullify duration
            layout.findViewById<SeekBar>(R.id.duration_bar).progress = 0
            mediaController?.transportControls?.skipToNext()
        }
    }

    private val prevButtonClickListener = View.OnClickListener {
        if (mediaController != null) {
            // nullify duration
            layout.findViewById<SeekBar>(R.id.duration_bar).progress = 0
            mediaController?.transportControls?.skipToPrevious()
        }
    }

    private val shuffleButtonClickListener = View.OnClickListener {
        val shuffleButton = layout.findViewById<ImageButton>(R.id.shuffle_button)
        var fromAlpha = FROM_ALPHA
        var toAlpha = TO_ALPHA
        if (shuffled)
            fromAlpha = toAlpha.also { toAlpha = fromAlpha }
        val alphaAnimator =
            ObjectAnimator.ofFloat(shuffleButton, View.ALPHA, fromAlpha, toAlpha)
        alphaAnimator.duration = 200
        alphaAnimator.start()
        shuffled = !shuffled
        playerService?.setShuffle(shuffled)
        MyApplication.setShuffleModeInPreferences(shuffled)
    }

    private val repeatButtonClickListener = View.OnClickListener {
        val repeatButton = layout.findViewById<ImageButton>(R.id.repeat_button)
        var fromAlpha = FROM_ALPHA
        var toAlpha = TO_ALPHA
        if (repeated)
            fromAlpha = toAlpha.also { toAlpha = fromAlpha }
        val alphaAnimator = ObjectAnimator.ofFloat(repeatButton, View.ALPHA, fromAlpha, toAlpha)
        alphaAnimator.duration = 200
        alphaAnimator.start()
        repeated = !repeated
        playerService?.repeatMode = repeated
    }

    private val toPlaylistButtonClickListener = View.OnClickListener {
        val bundle = Bundle()
        val playlistId = MyApplication.getCurrentPlaylistIdFromPreferences()
        if (playlistId == -1) {
            Toast.makeText(requireContext(), "No playlist created", Toast.LENGTH_SHORT).show()
            return@OnClickListener
        }
        bundle.putInt("playlist_id", MyApplication.getCurrentPlaylistIdFromPreferences())
        findNavController().navigate(R.id.action_player_to_playlist, bundle)
    }

    private val metadataObserver = Observer<MediaMetadataCompat?> { newMetadata ->
        Log.d("state", "$currentState")
        if (currentState == PlaybackStateCompat.STATE_STOPPED || currentState == -1) return@Observer

        val artistView = layout.findViewById<TextView>(R.id.artist)
        val titleView = layout.findViewById<TextView>(R.id.track_title)
        val albumImageView = layout.findViewById<ImageView>(R.id.album_image)
        val durationBar = layout.findViewById<SeekBar>(R.id.duration_bar)
        val maxDurationView = layout.findViewById<TextView>(R.id.max_duration)
        val shuffleButton = layout.findViewById<ImageButton>(R.id.shuffle_button)
        val repeatButton = layout.findViewById<ImageButton>(R.id.repeat_button)

        shuffleButton.isEnabled = true
        repeatButton.isEnabled = true

        if (newMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) == "Unknown") {
            artistView.text = ""
        } else {
            artistView.text = newMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        }
        titleView.text = newMetadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
        albumImageView.setImageBitmap(newMetadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART))
        durationBar.max =
            newMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)?.toInt() ?: 1
        durationBar.isEnabled = true
        val mins = newMetadata!!.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000 / 60
        val secs = newMetadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000 % 60
        val duration = if (secs < 10) "$mins:0$secs" else "$mins:$secs" // "0:00" duration format
        maxDurationView.text = duration
    }

    private fun clearPlayer() {
        val artistView = layout.findViewById<TextView>(R.id.artist)
        val titleView = layout.findViewById<TextView>(R.id.track_title)
        val albumImageView = layout.findViewById<ImageView>(R.id.album_image)
        val durationBar = layout.findViewById<SeekBar>(R.id.duration_bar)
        val maxDurationView = layout.findViewById<TextView>(R.id.max_duration)
        val shuffleButton = layout.findViewById<ImageButton>(R.id.shuffle_button)
        val repeatButton = layout.findViewById<ImageButton>(R.id.repeat_button)
        val playButton = layout.findViewById<ImageView>(R.id.play_button)

        artistView.text = ""
        titleView.text = ""
        durationBar.isEnabled = false
        durationBar.progress = 0
        updateCurrentDurationView(0)
        maxDurationView.text = "0:00"
        albumImageView.setImageBitmap(
            BitmapFactory.decodeResource(resources, R.drawable.without_album))

        shuffleButton.isEnabled = false
        repeatButton.isEnabled = false
        isPlaying = false
        playButton.setImageResource(R.drawable.baseline_play_arrow_24)
    }

    private fun updateCurrentDurationView(position: Int) {
        val currDurationView = layout.findViewById<TextView>(R.id.curr_duration)
        val mins = position / 1000 / 60
        val secs = position / 1000 % 60
        val duration = if (secs < 10) "$mins:0$secs" else "$mins:$secs" // "0:00" duration format
        currDurationView.text = duration
    }

    private fun setupDurationBarListener() {
        val durationBar = layout.findViewById<SeekBar>(R.id.duration_bar)
        durationBar.isEnabled = false
        durationBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            var changedDuration = 0
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mediaController?.transportControls?.seekTo(changedDuration.toLong())
                durationBar.progress = changedDuration
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
                val positionView = layout.findViewById<SeekBar>(R.id.duration_bar)
                if (onSeek)
                    delay(100) // wait for exo player to seek
                while (true) {
                    val position = mediaController?.playbackState?.position?.toInt() ?: 0
                    positionView.progress = position
                    updateCurrentDurationView(position)
                    delay(500)
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        // shouldn't ask for permission if it isn't granted before
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