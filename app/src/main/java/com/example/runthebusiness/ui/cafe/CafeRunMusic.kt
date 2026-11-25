package com.example.runthebusiness.ui.cafe

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.example.runthebusiness.R

@Suppress("DEPRECATION")
@Composable
internal fun CafeRunMusic(context: Context, playing: Boolean) {
    DisposableEffect(context, playing) {
        if (!playing) return@DisposableEffect onDispose { }
        val audio = context.getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val player = runCatching { MediaPlayer.create(context, R.raw.cafe_ambient, attributes, 0) }.getOrNull()
            ?: return@DisposableEffect onDispose { }
        player.isLooping = true
        player.setVolume(0.34f, 0.34f)
        var disposed = false
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (!disposed) {
                if (change == AudioManager.AUDIOFOCUS_GAIN) player.start()
                else if (player.isPlaying) player.pause()
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
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) player.start()
        onDispose {
            disposed = true
            releaseFocus()
            player.release()
        }
    }
}
