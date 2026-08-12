package com.mahanverse.galactichub

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaPlayer
import android.media.MediaPlayer.OnPreparedListener
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full‑screen with system bars hidden
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()

        setContentView(R.layout.activity_video_player)
        videoView = findViewById(R.id.videoView)

        val uriString = intent.getStringExtra("video_uri") ?: run {
            finish()
            return
        }
        val uri = Uri.parse(uriString)

        // MediaController for full player controls (play, pause, seek, rotation)
        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        videoView.setVideoURI(uri)

        videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            videoView.start()
        }

        videoView.setOnErrorListener { _, _, _ ->
            finish()
            true
        }

        // Allow rotation via auto‑orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }

    // Handle configuration changes without restarting activity
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Let the video surface adjust automatically
    }
}