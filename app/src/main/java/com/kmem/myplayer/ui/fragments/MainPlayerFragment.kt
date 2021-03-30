package com.kmem.myplayer.ui.fragments

import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
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
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
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
    private lateinit var layout : View
    private var isPlaying = false
    private var durationBarJob : Job? = null
    private var isStarted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        layout = inflater.inflate(R.layout.fragment_main_player, container, false)
        val prevButton = layout.findViewById<View>(R.id.prev_button) as ImageView
        val playButton = layout.findViewById<View>(R.id.play_button) as ImageView
        val nextButton = layout.findViewById<View>(R.id.next_button) as ImageView

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

    /*

    override fun onStart() {
        super.onStart()
        durationBar = view!!.findViewById<View>(R.id.durationBar) as SeekBar
        mediaPlayer = MediaPlayer()
        //getAudio();
        //setupDurationBarListener()
        //runDurationBarUpdate()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.prevButton -> onPrevClick()
            R.id.playButton -> onTogglePlayClick()
            R.id.nextButton -> onNextClick()
        }
    }

    private fun onTogglePlayClick() {
        if (isPlaying) pausePlayer() else startPlayer()
    }

    private fun onPrevClick() {
        stopPlayer()
        currentPos = (currentPos - 1) % musicIds.size
        if (currentPos < 0) currentPos = musicIds.size - 1
        mediaPlayer = MediaPlayer.create(context, musicIds[currentPos])
        startPlayer()
    }

    private fun onNextClick() {
        stopPlayer()
        currentPos = (currentPos + 1) % musicIds.size
        mediaPlayer = MediaPlayer.create(context, musicIds[currentPos])
        startPlayer()
    }


    /*
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case GET_AUDIO_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    final Uri uri = data.getData();
                    setUpMetadata(uri);
                    startSongFromUri(uri);
                }
                break;
        }
    }
     */

    private fun setUpMetadata(uri: Uri) {
        val groupView = view!!.findViewById<View>(R.id.group) as TextView
        val trackNameView = view!!.findViewById<View>(R.id.trackName) as TextView
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val group = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val trackName = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        if (group == null || trackName == null) {
            groupView.text = "None"
        } else {
            groupView.text = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            trackNameView.text = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        }
        setSongImage(mmr.embeddedPicture)
    }

    private fun setSongImage(art: ByteArray?) {
        if (art != null) {
            val songImage = view!!.findViewById<View>(R.id.albumImage) as ImageView
            val songbm = BitmapFactory.decodeByteArray(art, 0, art.size)
            songImage.setImageBitmap(songbm)
        }
    }

    private fun startSongFromUri(uri: Uri) {
        try {
            mediaPlayer!!.setDataSource(context!!, uri)
            mediaPlayer!!.prepare()
            startPlayer()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    /*
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
     */
    private fun runDurationBarUpdate() {
        durationBarHandler.post(object : Runnable {
            override fun run() {
                if (mediaPlayer != null) {
                    val currentPositionView = view!!.findViewById<View>(R.id.currDuration) as TextView
                    val duration = mediaPlayer!!.currentPosition / 1000
                    durationBar!!.progress = duration
                    val date = Date(duration.toLong() * 1000)
                    val formattedDuration = SimpleDateFormat("m:ss", Locale.getDefault()).format(date)
                    currentPositionView.text = formattedDuration
                }
                durationBarHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun stopDurationBarUpdate() {
        durationBarHandler.removeCallbacksAndMessages(null)
    }

    private fun setupDurationBarListener() {
        durationBar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            var changedDuration = 0
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mediaPlayer!!.seekTo(changedDuration)
                runDurationBarUpdate()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                stopDurationBarUpdate()
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (mediaPlayer != null && fromUser) {
                    changedDuration = progress * 1000
                    val date = Date(changedDuration.toLong())
                    val formattedDuration = SimpleDateFormat("m:ss", Locale.getDefault()).format(date)
                    val currDurationView = view!!.findViewById<View>(R.id.currDuration) as TextView
                    currDurationView.text = formattedDuration
                }
            }
        })
    }

    private fun startPlayer() {
        val playButton = view!!.findViewById<View>(R.id.playButton) as ImageView
        playButton.setImageResource(R.drawable.baseline_pause_24)
        mediaPlayer!!.start()
        isPlaying = true
        val duration = mediaPlayer!!.duration / 1000
        durationBar!!.max = duration
        val maxDurationView = view!!.findViewById<View>(R.id.maxDuration) as TextView
        val date = Date(duration.toLong() * 1000)
        val formattedDuration = SimpleDateFormat("m:ss", Locale.getDefault()).format(date)
        maxDurationView.text = formattedDuration
    }

    private fun stopPlayer() {
        val playButton = view!!.findViewById<View>(R.id.playButton) as ImageView
        playButton.setImageResource(R.drawable.baseline_play_arrow_24)
        mediaPlayer!!.stop()
        isPlaying = false
    }

    private fun pausePlayer() {
        val playButton = view!!.findViewById<View>(R.id.playButton) as ImageView
        playButton.setImageResource(R.drawable.baseline_play_arrow_24)
        mediaPlayer!!.pause()
        isPlaying = false
    } /*
    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(getContext(), PERMISSION_STRING)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{PERMISSION_STRING},
                    PERMISSION_CODE);
        }
    }
     */

    companion object {
        var musicIds = intArrayOf(R.raw.louna, R.raw.piknik, R.raw.years)
        var PERMISSION_STRING = Manifest.permission.READ_EXTERNAL_STORAGE
    }

     */
}