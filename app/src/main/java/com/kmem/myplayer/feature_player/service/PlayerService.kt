package com.kmem.myplayer.feature_player.service

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.*
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.*
import com.kmem.myplayer.MyApplication
import com.kmem.myplayer.R
import com.kmem.myplayer.core.domain.model.PlaylistState
import com.kmem.myplayer.core.domain.model.Track
import com.kmem.myplayer.core.presentation.MainActivity
import com.kmem.myplayer.feature_player.domain.repository.PlayerServiceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.util.*
import javax.inject.Inject

/**
 * Service for playing music
 */

@AndroidEntryPoint
class PlayerService : Service() {

    companion object {
        const val ACTION_PLAY_SELECTED_TRACK = "play_selected"
        const val EXTRA_TRACK = "extra_track"
        const val EXTRA_POSITION = "extra_position"
        private const val NOTIFICATION_ID = 404
        private const val NOTIFICATION_DEFAULT_CHANNEL_ID = "default_channel"
        private const val INACTIVITY_TIMEOUT = 600_000L // 10 minutes
        private const val UPDATE_STATE_INTERVAL = 10_000L // 10 seconds
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private val metadataBuilder = MediaMetadataCompat.Builder()

    private val stateBuilder = PlaybackStateCompat.Builder().setActions(
        PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    )

    private val context = this
    private var mediaSession: MediaSessionCompat? = null

    @Inject
    lateinit var repository: PlayerServiceRepository

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusRequested = false

    private var exoPlayer: SimpleExoPlayer? = null
    private var extractorsFactory: ExtractorsFactory? = null
    private var dataSourceFactory: DataSource.Factory? = null

    private var isRepositoryInitialized = false

    private var inactivityCheckJob: Job? = null
    private var savePlaylistStateJob: Job? = null

    private val _currentMetadata = MutableStateFlow<MediaMetadataCompat?>(null)
    val currentMetadata = _currentMetadata.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    var repeatMode: Boolean = MyApplication.getRepeatModeFromPreferences()
        set(value) {
            MyApplication.setRepeatModeInPreferences(value)
            field = value
        }

    var shuffleMode: Boolean = false
        get() {
            return this.repository.shuffle
        }
        set(value) {
            this.repository.shuffle = value
            field = value
        }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("WrongConstant")
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_DEFAULT_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.USAGE_MEDIA)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setAudioAttributes(audioAttributes)
                .build()
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaSession = MediaSessionCompat(this, "PlayerService")
        mediaSession!!.setCallback(mediaSessionCallback)

        val activityIntent = Intent(applicationContext, MainActivity::class.java)
        mediaSession!!.setSessionActivity(
            PendingIntent.getActivity(
                applicationContext,
                0,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        )

        val mediaButtonIntent = Intent(
            Intent.ACTION_MEDIA_BUTTON,
            null,
            applicationContext,
            MediaButtonReceiver::class.java
        )

        mediaSession!!.setMediaButtonReceiver(
            PendingIntent.getBroadcast(
                applicationContext,
                0,
                mediaButtonIntent,
                0
            )
        )

        exoPlayer = SimpleExoPlayer.Builder(this)
            .setTrackSelector(DefaultTrackSelector(this))
            .setLoadControl(DefaultLoadControl())
            .build()
        exoPlayer?.addListener(exoPlayerListener)

        val fileDataSource = FileDataSource.Factory()
        this.dataSourceFactory = fileDataSource
        this.extractorsFactory = DefaultExtractorsFactory()

        MainScope().launch {
            repository.isInitialized.collectLatest { value ->
                if (value) {
                    onRepositoryInitialized()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        exoPlayer?.release()
        savePlaylistStateJob?.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    suspend fun onRepositoryInitialized() {
        val track = this@PlayerService.repository.getCurrent() ?: return
        if (!checkFileExistence(track.uri, showMessage = false)) return

        if (!isRepositoryInitialized) {
            val state: PlaylistState
            withContext(Dispatchers.IO) {
                state = this@PlayerService.repository.getPlaylistState(
                    this@PlayerService, MyApplication.getCurrentPlaylistIdFromPreferences()
                )
            }

            prepareToPlay(track)
            _currentTrack.value = track

            exoPlayer?.seekTo(state.position.toLong())
            setPlaybackState(MyApplication.getPlaybackStateFromPreferences())
            isRepositoryInitialized = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun onTracksDeleted() {
        // update player with new track
        mediaSessionCallback.onRemoveQueueItem(null)
    }

    fun onCurrentPlaylistDeleted() {
        mediaSessionCallback.onStop()
    }

    /**
     * Determines actions for control player
     */
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        var currentState = MyApplication.getPlaybackStateFromPreferences()
            set(value) {
                MyApplication.setPlaybackStateInPreferences(value)
                field = value
            }

        init {
            setPlaybackState(currentState, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN)
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onPlay() {
            if (!exoPlayer!!.playWhenReady || repeatMode) {
                startService(Intent(baseContext, PlayerService::class.java))

                val track = this@PlayerService.repository.getCurrent()

                if (isTrackNull(track)) return
                track as Track // remove nullability

                if (!checkFileExistence(track.uri)) return
                prepareToPlay(track)

                if (!audioFocusRequested) {
                    audioFocusRequested = true

                    val audioFocusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager?.requestAudioFocus(audioFocusRequest!!)
                    } else {
                        audioManager?.requestAudioFocus(
                            audioFocusChangeListener,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN
                        )
                    }
                    if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                        return
                }

                mediaSession?.isActive = true
                registerReceiver(
                    becomingNoisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                )
                exoPlayer!!.playWhenReady = true
                saveCurrentPlaylistState(track)
                runPlaylistStateUpdater()
            }

            setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
            currentState = PlaybackStateCompat.STATE_PLAYING
            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onPause() {
            if (exoPlayer!!.playWhenReady) {
                exoPlayer?.playWhenReady = false
                unregisterReceiver(becomingNoisyReceiver)
            }

            if (audioFocusRequested)
                audioFocusRequested = false

            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            currentState = PlaybackStateCompat.STATE_PAUSED

            saveCurrentPlaylistState(this@PlayerService.repository.getCurrent())
            refreshNotificationAndForegroundStatus(currentState)
            savePlaylistStateJob?.cancel()
        }

        override fun onStop() {
            if (exoPlayer!!.playWhenReady) {
                exoPlayer?.playWhenReady = false
                unregisterReceiver(becomingNoisyReceiver)
            }

            if (audioFocusRequested) {
                audioFocusRequested = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
                } else {
                    audioManager?.abandonAudioFocus(audioFocusChangeListener)
                }
            }

            mediaSession?.isActive = false
            setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
            currentState = PlaybackStateCompat.STATE_STOPPED
            refreshNotificationAndForegroundStatus(currentState)
            savePlaylistStateJob?.cancel()
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onSkipToNext() {
            var track = this@PlayerService.repository.getNext()
            if (isTrackNull(track)) return

            while (!checkFileExistence(track!!.uri, false)) {
                if (track.uri == currentTrack.value?.uri) {
                    break
                }

                track = this@PlayerService.repository.getNext()
            }

            // all tracks are deleted from memory
            if (!checkFileExistence(track.uri, showMessage = false)) onStop()

            if (track.uri == currentTrack.value?.uri) {
                exoPlayer?.seekTo(0)
            } else {
                prepareToPlay(track)
            }

            setPlaybackState(currentState)
            saveCurrentPlaylistState(track)
            refreshNotificationAndForegroundStatus(currentState)
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onSkipToPrevious() {
            var track = this@PlayerService.repository.getPrevious()
            if (isTrackNull(track)) return

            while (!checkFileExistence(track!!.uri, false)) {
                if (track!!.uri == currentTrack.value?.uri) {
                    break
                }

                track = this@PlayerService.repository.getPrevious()
            }

            // all tracks are deleted from memory
            if (!checkFileExistence(track!!.uri, showMessage = false)) onStop()

            if (track!!.uri == currentTrack.value?.uri) {
                exoPlayer?.seekTo(0)
            } else {
                prepareToPlay(track!!)
            }

            setPlaybackState(currentState)
            saveCurrentPlaylistState(track)
            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onSeekTo(pos: Long) {
            exoPlayer?.seekTo(pos)

            setPlaybackState(currentState)
            saveCurrentPlaylistState(this@PlayerService.repository.getCurrent())
            refreshNotificationAndForegroundStatus(currentState)
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onRemoveQueueItem(description: MediaDescriptionCompat?) {
            if (this@PlayerService.repository.getCurrent()?.uri != currentTrack.value?.uri) {
                val track = this@PlayerService.repository.getCurrent()

                if (track == null || !checkFileExistence(track.uri)) {
                    onStop()
                    return
                }

                prepareToPlay(track)
                setPlaybackState(currentState)
                saveCurrentPlaylistState(this@PlayerService.repository.getCurrent())
            }
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (action == ACTION_PLAY_SELECTED_TRACK && extras != null) {
                val track = extras.getParcelable<Track>(EXTRA_TRACK)!!
                val position = extras.getInt(EXTRA_POSITION)

                if (!checkFileExistence(track.uri)) return

                startService(Intent(baseContext, PlayerService::class.java))

                if (track.playlistId != MyApplication.getCurrentPlaylistIdFromPreferences()) {
                    if (this@PlayerService.repository.getCurrent() != null) {
                        saveCurrentPlaylistState(this@PlayerService.repository.getCurrent())
                    }
                    this@PlayerService.repository.savePlaylistState(
                        track.playlistId,
                        track.uri,
                        position
                    )
                    prepareToPlay(track)
                } else {
                    prepareToPlay(track)
                    saveCurrentPlaylistState(track)
                }

                this@PlayerService.repository.setPlaylist(track.playlistId, track.position)

                if (position != 0) {
                    exoPlayer?.seekTo(position.toLong())
                }

                if (!audioFocusRequested) {
                    audioFocusRequested = true

                    val audioFocusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager?.requestAudioFocus(audioFocusRequest!!)
                    } else {
                        audioManager?.requestAudioFocus(
                            audioFocusChangeListener,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN
                        )
                    }
                    if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                        return
                }

                mediaSession?.isActive = true
                registerReceiver(
                    becomingNoisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                )
                exoPlayer!!.playWhenReady = true

                setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                currentState = PlaybackStateCompat.STATE_PLAYING

                refreshNotificationAndForegroundStatus(currentState)
                runPlaylistStateUpdater()
            }
        }

        private fun isTrackNull(track: Track?): Boolean {
            if (track == null) {
                Toast.makeText(
                    applicationContext,
                    "Empty Playlist",
                    Toast.LENGTH_SHORT
                ).show()
                onStop()
                return true
            }
            return false
        }

        /**
         * @param track Track which state should be saved
         */
        private fun saveCurrentPlaylistState(track: Track?) {
            if (track != null) {
                this@PlayerService.repository.savePlaylistState(
                    MyApplication.getCurrentPlaylistIdFromPreferences(),
                    track.uri,
                    exoPlayer!!.contentPosition.toInt()
                )
            } else {
                this@PlayerService.repository.savePlaylistState(
                    MyApplication.getCurrentPlaylistIdFromPreferences(), Uri.EMPTY, 0
                )
            }
        }

        /**
         *  Runs updater that updates current state of playlist every UPDATE_STATE_INTERVAL seconds
         */
        private fun runPlaylistStateUpdater() {
            if (savePlaylistStateJob == null || savePlaylistStateJob!!.isCancelled) {
                savePlaylistStateJob = MainScope().launch {
                    while (true) {
                        try {
                            saveCurrentPlaylistState(this@PlayerService.repository.getCurrent()!!)
                            delay(UPDATE_STATE_INTERVAL)
                        } catch (e: Exception) {
                            // musicRepository is not ready
                            delay(UPDATE_STATE_INTERVAL)
                        }
                    }
                }
            }
        }
    }

    private fun setPlaybackState(state: Int, position: Long = exoPlayer?.contentPosition!!) {
        mediaSession?.setPlaybackState(
            stateBuilder.setState(state, position, 1F).build()
        )
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun prepareToPlay(track: Track) {
        if (track.uri != currentTrack.value?.uri || repeatMode) {
            if (track.uri != currentTrack.value?.uri) {
                updateMetadataFromTrack(track)
            }
            _currentTrack.value = track
            val mediaItem = MediaItem.fromUri(track.uri)
            val mediaSource =
                ProgressiveMediaSource.Factory(dataSourceFactory!!, extractorsFactory!!)
                    .createMediaSource(mediaItem)
            exoPlayer?.setMediaSource(mediaSource)
            exoPlayer?.prepare()
        }
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun updateMetadataFromTrack(track: Track) {
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.artist)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)
        metadataBuilder.putBitmap(
            MediaMetadataCompat.METADATA_KEY_ART,
            BitmapFactory.decodeResource(resources, R.drawable.without_album)
        )
        var metadata = metadataBuilder.build()
        _currentMetadata.value = metadata
        mediaSession?.setMetadata(metadata)

        MainScope().launch {
            withContext(Dispatchers.IO) {
                val mmr =
                    MediaMetadataRetriever().apply { setDataSource(context, track.uri) }
                val art = mmr.embeddedPicture
                if (art != null) {
                    metadataBuilder.putBitmap(
                        MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeByteArray(
                            art, 0, art.size
                        )
                    )
                }
            }

            metadata = metadataBuilder.build()
            _currentMetadata.value = metadata
            mediaSession?.setMetadata(metadata)
        }
    }

    private fun checkFileExistence(uri: Uri, showMessage: Boolean = true): Boolean {
        val file = File(uri.path!!)
        if (showMessage && (!file.exists() || !file.canRead())) {
            Toast.makeText(context, "Error: File not found", Toast.LENGTH_SHORT).show()
        }

        return file.exists() && file.canRead()
    }

    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener =
        object : AudioManager.OnAudioFocusChangeListener {
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onAudioFocusChange(focusChange: Int) {
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        exoPlayer?.volume = 1F
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        exoPlayer?.volume = 0.3F
                    }
                    else -> mediaSessionCallback.onPause()
                }
            }
        }

    private val becomingNoisyReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Disconnecting headphones - stop playback
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action)
                mediaSessionCallback.onPause()
        }
    }

    private var exoPlayerListener: Player.Listener = object : Player.Listener {
        override fun onTracksChanged(
            trackGroups: TrackGroupArray,
            trackSelections: TrackSelectionArray
        ) {
        }

        override fun onLoadingChanged(isLoading: Boolean) {}

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (playWhenReady && playbackState == ExoPlayer.STATE_ENDED) {
                if (repeatMode) {
                    mediaSessionCallback.onPlay()
                } else {
                    val isEnded = this@PlayerService.repository.isEnded()
                    mediaSessionCallback.onSkipToNext()
                    if (isEnded)
                        mediaSessionCallback.onPause()
                }
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}
    }

    override fun onBind(intent: Intent?): IBinder {
        return PlayerServiceBinder()
    }

    inner class PlayerServiceBinder : Binder() {
        fun getMediaSessionToken(): MediaSessionCompat.Token? {
            return mediaSession?.sessionToken
        }

        fun getMetadataFlow(): StateFlow<MediaMetadataCompat?> {
            return currentMetadata
        }

        fun getCurrentTrackFlow(): StateFlow<Track?> {
            return currentTrack
        }

        fun getService(): PlayerService {
            return this@PlayerService
        }
    }

    private fun refreshNotificationAndForegroundStatus(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                startForeground(NOTIFICATION_ID, getNotification(playbackState))
                NotificationManagerCompat.from(baseContext)
                    .notify(NOTIFICATION_ID, getNotification(playbackState))
                // cancel INACTIVITY_TIMEOUT
                inactivityCheckJob?.cancel()
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                NotificationManagerCompat.from(baseContext)
                    .notify(NOTIFICATION_ID, getNotification(playbackState))
                // wait INACTIVITY_TIMEOUT and stop player
                inactivityCheckJob = MainScope().launch {
                    withContext(Dispatchers.Default) {
                        delay(INACTIVITY_TIMEOUT)
                    }
                    stopSelf()
                }
            }
            else -> {
                stopForeground(true)
                //NotificationManagerCompat.from(baseContext).cancel(NOTIFICATION_ID)
            }
        }
    }

    /**
     * @param playbackState State of player
     * @return Notification with current player state
     */
    private fun getNotification(playbackState: Int): Notification {
        val builder = MediaStyleHelper.from(baseContext, mediaSession!!)
        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_previous, getString(R.string.previous),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
        )

        if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
            builder.addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause, getString(R.string.pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            )
        } else {
            builder.addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play, getString(R.string.play),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            )
        }

        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_next, getString(R.string.next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
        )

        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_STOP
                    )
                )
                .setMediaSession(mediaSession?.sessionToken) // setMediaSession required for Android Wear
        )

        builder.setSmallIcon(R.mipmap.ic_launcher)
        builder.color = ContextCompat.getColor(
            this,
            R.color.design_default_color_primary_dark
        ) // The whole background (in MediaStyle), not just icon background
        builder.setShowWhen(false)
        builder.setContentTitle(resources.getString(R.string.app_name))
        builder.priority = NotificationCompat.PRIORITY_HIGH
        builder.setNotificationSilent()
        builder.setChannelId(NOTIFICATION_DEFAULT_CHANNEL_ID)

        return builder.build()
    }
}