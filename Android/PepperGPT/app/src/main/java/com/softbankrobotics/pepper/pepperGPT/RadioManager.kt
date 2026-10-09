package com.softbankrobotics.pepper.pepperGPT

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

class RadioManager {
    private val TAG = "RadioManager"
    private var mediaPlayer: MediaPlayer? = null
    interface RadioListener {
        fun onStatusChange(status: String, isError: Boolean)
    }

    private var listener: RadioListener? = null

    fun setListener(listener: RadioListener) {
        this.listener = listener
    }

    fun play(url: String) {
        try {
            stop()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener {
                    it.start()
                    listener?.onStatusChange("Playing! 🎵", false)
                }
                setOnErrorListener { _, what, extra ->
                    listener?.onStatusChange("Error: $what / $extra", true)
                    true
                }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        listener?.onStatusChange("Buffering...", false)
                    } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        listener?.onStatusChange("Playing! 🎵", false)
                    }
                    false
                }
                prepareAsync()
            }
            listener?.onStatusChange("Connecting...", false)
        } catch (e: Exception) {
            listener?.onStatusChange("Init Error: ${e.message}", true)
        }
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
            Log.d(TAG, "🛑 Radio stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop radio: ${e.message}")
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }
}
