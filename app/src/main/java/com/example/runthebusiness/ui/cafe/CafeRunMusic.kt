package com.example.runthebusiness.ui.cafe

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.example.runthebusiness.R

private val cafePlaylist = intArrayOf(R.raw.cafe_ambient, R.raw.cafe_morning_groove, R.raw.cafe_steady_service)

private class PlaylistPosition(var track: Int = 0, var milliseconds: Int = 0)

@Suppress("DEPRECATION")
@Composable
internal fun CafeRunMusic(context: Context, playing: Boolean) {
    val position = remember { PlaylistPosition() }
    DisposableEffect(context, playing) {
        if (!playing) return@DisposableEffect onDispose { }
        val audio = context.getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        var player: MediaPlayer? = null
        var disposed = false
        fun playTrack() {
            val current = runCatching {
                MediaPlayer.create(context, cafePlaylist[position.track], attributes, 0)
            }.getOrNull() ?: return
            player = current
            current.setVolume(0.34f, 0.34f)
            current.setOnCompletionListener {
                if (!disposed && player === current) {
                    current.release()
                    player = null
                    position.track = (position.track + 1) % cafePlaylist.size
                    position.milliseconds = 0
                    playTrack()
                }
            }
            if (position.milliseconds > 0) current.seekTo(position.milliseconds)
            current.start()
        }
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (!disposed) {
                if (change == AudioManager.AUDIOFOCUS_GAIN) {
                    val current = player
                    if (current == null) playTrack() else current.start()
                } else if (player?.isPlaying == true) {
                    player?.pause()
                }
            }
        }
        val releaseFocus: () -> Unit
        val result: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(listener)
                .build()
            result = audio.requestAudioFocus(request)
            releaseFocus = { audio.abandonAudioFocusRequest(request) }
        } else {
            result = audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            releaseFocus = { audio.abandonAudioFocus(listener) }
        }
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED && player == null) playTrack()
        onDispose {
            disposed = true
            releaseFocus()
            player?.let { current ->
                position.milliseconds = runCatching { current.currentPosition }.getOrDefault(0)
                current.release()
            }
        }
    }
}
