package com.kmem.myplayer.ui.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.TransitionDrawable
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.core.animation.addListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.kmem.myplayer.R
import com.kmem.myplayer.service.PlayerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainPlayerFragment : Fragment()/*, View.OnClickListener */ {
    private val GET_AUDIO_REQUEST_CODE = 222


    private var playerServiceBinder : PlayerService.PlayerServiceBinder? = null
    private var mediaController : MediaControllerCompat? = null
    private var callback : MediaControllerCompat.Callback? = null
    private var serviceConnection : ServiceConnection? = null
    private lateinit var playerService : PlayerService
    private lateinit var layout : View
    private var isPlaying = false
    private var durationBarJob : Job? = null
    private var isStarted = false
    private var shuffled = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        layout = inflater.inflate(R.layout.fragment_main_player, container, false)
        val prevButton = layout.findViewById<View>(R.id.prev_button) as ImageView
        val playButton = layout.findViewById<View>(R.id.play_button) as ImageView
        val nextButton = layout.findViewById<View>(R.id.next_button) as ImageView
        val shuffleButton = layout.findViewById<ImageButton>(R.id.shuffle_button) as ImageButton
        val repeatButton = layout.findViewById<ImageButton>(R.id.repeat_button) as ImageButton

        // select TextViews for sliding long text
        layout.findViewById<TextView>(R.id.track_title).isSelected = true
        layout.findViewById<TextView>(R.id.artist).isSelected = true

        callback = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                if (state == null)
                    return

                val positionView = layout.findViewById<SeekBar>(R.id.duration_bar)
                isPlaying = state.state == PlaybackStateCompat.STATE_PLAYING
                if (isPlaying) {
                    playButton.setImageResource(R.drawable.baseline_pause_24)
                    durationBarJob?.cancel()
                    if (isStarted)
                        runDurationBarUpdate()
                }
                else {
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
                    mediaController = playerServiceBinder!!.getMediaSessionToken()?.let { MediaControllerCompat(activity, it) }
                    mediaController?.registerCallback(callback!!)
                    callback?.onPlaybackStateChanged(mediaController?.playbackState)
                    playerServiceBinder!!.getLiveMetadata().observe(viewLifecycleOwner, metadataObserver)
                    playerService = playerServiceBinder!!.getService()
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

        playButton.setOnClickListener {
            if (mediaController != null) {
                if (isPlaying)
                    mediaController?.transportControls?.pause()
                else
                    mediaController?.transportControls?.play()
            }
        }

        nextButton.setOnClickListener {
            if (mediaController != null) {
                // nullify duration
                layout.findViewById<SeekBar>(R.id.duration_bar).progress = 0
                mediaController?.transportControls?.skipToNext()
            }
        }

        prevButton.setOnClickListener {
            if (mediaController != null) {
                // nullify duration
                layout.findViewById<SeekBar>(R.id.duration_bar).progress = 0
                mediaController?.transportControls?.skipToPrevious()
            }
        }

        if (!shuffled) shuffleButton.alpha = 0.3f
        shuffleButton.setOnClickListener {
            var fromAlpha = 0.3f
            var toAlpha = 1.0f
            if (shuffled)
                fromAlpha = toAlpha.also { toAlpha = fromAlpha }
            val alphaAnimator = ObjectAnimator.ofFloat(shuffleButton, View.ALPHA, fromAlpha, toAlpha)
            alphaAnimator.duration = 200
            alphaAnimator.start()
            shuffled = !shuffled
            playerService.setShuffle(shuffled)
        }

        setupDurationBarListener()

        return layout
    }

    override fun onStart() {
        super.onStart()
        val durationBar = layout.findViewById<SeekBar>(R.id.duration_bar)
        // update duration if there is already playing track on pause
        durationBar.progress = mediaController?.playbackState?.position?.toInt() ?: 0

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

    private val metadataObserver = Observer<MediaMetadataCompat> { newMetadata ->
        val artistView = layout.findViewById<TextView>(R.id.artist)
        val titleView = layout.findViewById<TextView>(R.id.track_title)
        val albumImageView = layout.findViewById<ImageView>(R.id.album_image)
        val durationBar = layout.findViewById<SeekBar>(R.id.duration_bar)
        val maxDurationView = layout.findViewById<TextView>(R.id.max_duration)


        if (newMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) == "Unknown") {
            artistView.text = ""
        }
        else {
            artistView.text = newMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        }
        titleView.text = newMetadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
        albumImageView.setImageBitmap(newMetadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART))
        durationBar.max = newMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)?.toInt() ?: 1
        val mins = newMetadata!!.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000 / 60
        val secs = newMetadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000 % 60
        val duration = if (secs < 10) "$mins:0$secs" else "$mins:$secs" // "0:00" duration format
        maxDurationView.text = duration
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
                    Log.d("qwe", "qwe")
                    delay(500)
                }
            }
        }
    }
}