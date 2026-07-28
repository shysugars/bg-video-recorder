package com.example.bgvideo

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

/** 单视频播放页：播放应用私有目录下的 mp4。 */
class VideoPlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run {
            finish(); return
        }
        val videoView = findViewById<VideoView>(R.id.videoView)
        videoView.setVideoURI(Uri.parse(path))
        MediaController(this).apply {
            setAnchorView(videoView)
            setMediaPlayer(videoView)
        }
        videoView.requestFocus()
        videoView.start()
    }

    companion object {
        const val EXTRA_PATH = "video_path"
    }
}
