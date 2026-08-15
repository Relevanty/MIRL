package com.personal.sleepalarm.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Одноразовое, ограниченное по времени воспроизведение ночной подсказки. */
object CueSoundPlayer {

    fun play(
        context: Context,
        uriString: String,
        volumeFraction: Float,
        maxPlayMs: Long
    ): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        var player: MediaPlayer? = null
        val failed = AtomicBoolean(false)
        val finished = CountDownLatch(1)

        return try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context.applicationContext, uri)
                val safeVolume = volumeFraction.coerceIn(0f, 1f)
                setVolume(safeVolume, safeVolume)
                setOnCompletionListener { finished.countDown() }
                setOnErrorListener { _, what, extra ->
                    failed.set(true)
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    finished.countDown()
                    true
                }
                prepare()
                start()
            }

            finished.await(maxPlayMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            !failed.get()
        } catch (throwable: Throwable) {
            Log.e(TAG, "Unable to play cue uri=$uri", throwable)
            false
        } finally {
            player?.let { mediaPlayer ->
                runCatching { if (mediaPlayer.isPlaying) mediaPlayer.stop() }
                runCatching { mediaPlayer.reset() }
                runCatching { mediaPlayer.release() }
            }
        }
    }

    private const val TAG = "CueSoundPlayer"
}
