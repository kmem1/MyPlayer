package com.kmem.myplayer.service

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
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.*
import com.kmem.myplayer.R
import com.kmem.myplayer.data.Track
import com.kmem.myplayer.ui.activities.MainActivity
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.ArrayList

class PlayerService : Service() {
    companion object {
        const val ACTION_PLAY_AT_POSITION = "play_at_position"
        const val EXTRA_POSITION = "extra_position"
        private const val NOTIFICATION_ID = 404
        private const val NOTIFICATION_DEFAULT_CHANNEL_ID = "default_channel"
        private const val INACTIVITY_TIMEOUT = 600000L // 10 mins
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

    private var mediaSession : MediaSessionCompat? = null

    private var audioManager : AudioManager? = null
    private var audioFocusRequest : AudioFocusRequest? = null
    private var audioFocusRequested = false

    private var exoPlayer : SimpleExoPlayer? = null
    private var extractorsFactory : ExtractorsFactory? = null
    private var dataSourceFactory : DataSource.Factory? = null

    private var musicRepository : MusicRepository? = null

    private var inactivityCheckJob : Job? = null

    val currentMetadata : MutableLiveData<MediaMetadataCompat> = MutableLiveData<MediaMetadataCompat>()
    val currentPosition : MutableLiveData<Int> = MutableLiveData<Int>()
    val currentUri : MutableLiveData<Uri> = MutableLiveData<Uri>()
    var repeatMode : Boolean = false

    @SuppressLint("WrongConstant")
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(NOTIFICATION_DEFAULT_CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManagerCompat.IMPORTANCE_DEFAULT)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

        MainScope().launch {
            withContext(Dispatchers.IO) {
                musicRepository = MusicRepository(context)
            }
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaSession = MediaSessionCompat(this, "PlayerService")
        mediaSession!!.setCallback(mediaSessionCallback)

        val activityIntent = Intent(applicationContext, MainActivity::class.java)
        mediaSession!!.setSessionActivity(PendingIntent.getActivity(applicationContext, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT))

        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, applicationContext, MediaButtonReceiver::class.java)
        mediaSession!!.setMediaButtonReceiver(PendingIntent.getBroadcast(applicationContext, 0, mediaButtonIntent, 0))

        exoPlayer = SimpleExoPlayer.Builder(this)
                .setTrackSelector(DefaultTrackSelector(this))
                .setLoadControl(DefaultLoadControl())
                .build()
        exoPlayer?.addListener(exoPlayerListener)

        val fileDataSource = FileDataSource.Factory()
        this.dataSourceFactory = fileDataSource
        this.extractorsFactory = DefaultExtractorsFactory()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        exoPlayer?.release()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updatePositions() {
        MainScope().launch {
            musicRepository?.updatePositions()
        }
    }

    fun addNewTracks() {
        MainScope().launch {
            musicRepository?.addNewTracks()
        }
    }

    fun setShuffle(value: Boolean) {
        musicRepository?.shuffle = value
    }

    fun getShuffle() : Boolean {
        return musicRepository?.shuffle ?: false
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun deleteTracks(tracks: ArrayList<Track>) {
        musicRepository?.deleteTracks(tracks)

        // refresh current track
        if (musicRepository?.getCurrent()?.uri != currentUri.value) {
            val state = mediaSessionCallback.currentState
            mediaSessionCallback.onPause()
            mediaSessionCallback.onPlay() // play for update track
            if (state != PlaybackStateCompat.STATE_PLAYING)
                mediaSessionCallback.onPause()
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        var currentState = PlaybackStateCompat.STATE_STOPPED

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onPlay() {
            if (!exoPlayer!!.playWhenReady || repeatMode) {
                startService(Intent(baseContext, PlayerService::class.java))

                if (musicRepository == null) return

                val track = musicRepository?.getCurrent()

                if (track == null) {
                    Toast.makeText(applicationContext, "Add items to playlist", Toast.LENGTH_SHORT).show()
                    return
                }

                currentPosition.value = musicRepository?.currentItemIndex
                updateMetadataFromTrack(track)

                prepareToPlay(track.uri)

                if (!audioFocusRequested) {
                    audioFocusRequested = true

                    val audioFocusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager?.requestAudioFocus(audioFocusRequest!!)
                    } else {
                        audioManager?.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                    }
                    if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                        return
                }

                mediaSession?.isActive = true
                registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                exoPlayer!!.playWhenReady = true
            }

            mediaSession?.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, exoPlayer?.contentPosition!!, 1F).build())
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

                mediaSession?.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, exoPlayer?.contentPosition!!, 1F).build())
                currentState = PlaybackStateCompat.STATE_PAUSED

                refreshNotificationAndForegroundStatus(currentState)
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
            mediaSession?.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, exoPlayer?.contentPosition!!, 1F).build())
            currentState = PlaybackStateCompat.STATE_STOPPED

            refreshNotificationAndForegroundStatus(currentState)

            //stopSelf()
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onSkipToNext() {
            if (musicRepository == null) return

            val track = musicRepository?.getNext()

            if (track == null) {
                Toast.makeText(applicationContext, "Add items to playlist", Toast.LENGTH_SHORT).show()
                return
            }

            currentPosition.value = musicRepository?.currentItemIndex
            updateMetadataFromTrack(track)

            prepareToPlay(track.uri)

            mediaSession?.setPlaybackState(stateBuilder.setState(currentState, exoPlayer?.contentPosition!!, 1F).build())

            refreshNotificationAndForegroundStatus(currentState)
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onSkipToPrevious() {
            if (musicRepository == null) return

            val track = musicRepository?.getPrevious()

            if (track == null) {
                Toast.makeText(applicationContext, "Add items to playlist", Toast.LENGTH_SHORT).show()
                return
            }

            currentPosition.value = musicRepository?.currentItemIndex
            updateMetadataFromTrack(track)

            prepareToPlay(track.uri)

            mediaSession?.setPlaybackState(stateBuilder.setState(currentState, exoPlayer?.contentPosition!!, 1F).build())

            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onSeekTo(pos: Long) {
            exoPlayer?.seekTo(pos)

            mediaSession?.setPlaybackState(stateBuilder.setState(currentState, exoPlayer?.contentPosition!!, 1F).build())

            refreshNotificationAndForegroundStatus(currentState)
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (action == ACTION_PLAY_AT_POSITION && extras != null) {
                startService(Intent(baseContext, PlayerService::class.java))

                if (musicRepository == null) return

                val position = extras.getInt(EXTRA_POSITION)
                val track = musicRepository?.getAtPosition(position)

                if (track == null) {
                    Toast.makeText(applicationContext, "Add items to playlist", Toast.LENGTH_SHORT).show()
                    return
                }

                currentPosition.value = musicRepository?.currentItemIndex
                updateMetadataFromTrack(track)

                prepareToPlay(track.uri)

                if (!audioFocusRequested) {
                    audioFocusRequested = true

                    val audioFocusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager?.requestAudioFocus(audioFocusRequest!!)
                    } else {
                        audioManager?.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                    }
                    if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                        return
                }

                mediaSession?.isActive = true
                registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                exoPlayer!!.playWhenReady = true


                mediaSession?.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, exoPlayer?.contentPosition!!, 1F).build())
                currentState = PlaybackStateCompat.STATE_PLAYING

                refreshNotificationAndForegroundStatus(currentState)
            }
        }

        private fun prepareToPlay(uri: Uri) {
            if (uri != currentUri.value || repeatMode) {
                currentUri.value = uri
                val mediaItem = MediaItem.fromUri(uri)
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory!!, extractorsFactory!!).createMediaSource(mediaItem)
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
            var metadata = metadataBuilder.build()
            currentMetadata.value = metadata
            mediaSession?.setMetadata(metadata)

            MainScope().launch {
                withContext(Dispatchers.IO) {
                    val mmr = MediaMetadataRetriever().apply { setDataSource(context, track.uri) }
                    val art = mmr.embeddedPicture
                    if (art != null)
                        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeByteArray(art, 0, art.size
                                ?: 0))
                    else
                        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(resources, R.drawable.without_album))
                }

                metadata = metadataBuilder.build()
                currentMetadata.value = metadata
                mediaSession?.setMetadata(metadata)
            }
        }
    }

        private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener = object : AudioManager.OnAudioFocusChangeListener {
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

        private var exoPlayerListener: Player.EventListener = object : Player.EventListener {
            override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {}

            override fun onLoadingChanged(isLoading: Boolean) {}

            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playWhenReady && playbackState == ExoPlayer.STATE_ENDED) {
                    if (repeatMode) {
                        mediaSessionCallback.onPlay()
                    } else {
                        val isEnded = musicRepository?.isEnded()
                        mediaSessionCallback.onSkipToNext()
                        if (isEnded == true)
                            mediaSessionCallback.onPause()
                    }
                }
            }

            override fun onPlayerError(error: ExoPlaybackException) {}

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}
        }

        override fun onBind(intent: Intent?): IBinder {
            return PlayerServiceBinder()
        }

        inner class PlayerServiceBinder : Binder() {
            fun getMediaSessionToken(): MediaSessionCompat.Token? {
                return mediaSession?.sessionToken
            }

            fun getLiveMetadata(): LiveData<MediaMetadataCompat> {
                return currentMetadata
            }

            fun getLiveUri(): LiveData<Uri> {
                return currentUri
            }

            fun getService(): PlayerService {
                return this@PlayerService
            }
        }

        private fun refreshNotificationAndForegroundStatus(playbackState: Int) {
            when (playbackState) {
                PlaybackStateCompat.STATE_PLAYING -> {
                    startForeground(NOTIFICATION_ID, getNotification(playbackState))
                    NotificationManagerCompat.from(baseContext).notify(NOTIFICATION_ID, getNotification(playbackState))
                    inactivityCheckJob?.cancel()
                }
                PlaybackStateCompat.STATE_PAUSED -> {
                    NotificationManagerCompat.from(baseContext).notify(NOTIFICATION_ID, getNotification(playbackState))
                    inactivityCheckJob = MainScope().launch {
                        withContext(Dispatchers.Default) {
                            delay(INACTIVITY_TIMEOUT)
                        }
                        mediaSessionCallback.onStop()
                    }
                }
                else -> {
                    mediaSession?.isActive = false
                }
            }
        }

        private fun getNotification(playbackState: Int): Notification {
            Log.d("state", playbackState.toString())
            val builder = MediaStyleHelper.from(baseContext, mediaSession!!)
            builder.addAction(NotificationCompat.Action(android.R.drawable.ic_media_previous, getString(R.string.previous),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)))

            if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
                builder.addAction(NotificationCompat.Action(android.R.drawable.ic_media_pause, getString(R.string.pause),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)))
            } else {
                builder.addAction(NotificationCompat.Action(android.R.drawable.ic_media_play, getString(R.string.play),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)))
            }

            builder.addAction(NotificationCompat.Action(android.R.drawable.ic_media_next, getString(R.string.next),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))
            builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
                    .setMediaSession(mediaSession?.sessionToken)) // setMediaSession required for Android Wear
            builder.setSmallIcon(R.mipmap.ic_launcher)
            builder.color = ContextCompat.getColor(this, R.color.design_default_color_primary_dark) // The whole background (in MediaStyle), not just icon background
            builder.setShowWhen(false)
            builder.setContentTitle(resources.getString(R.string.app_name))
            builder.priority = NotificationCompat.PRIORITY_HIGH
            //builder.setOnlyAlertOnce(true)
            builder.setNotificationSilent()
            builder.setChannelId(NOTIFICATION_DEFAULT_CHANNEL_ID)

            return builder.build()
        }
}