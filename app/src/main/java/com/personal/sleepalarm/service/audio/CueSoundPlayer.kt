package com.personal.sleepalarm.service.audio

import android.content.Context
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
        volumePercent: Int,
        maxPlayMs: Long
    ): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val playerGain = AppVolumeScale.gainForPercent(volumePercent)
        val streamVolumeLease = if (playerGain > 0f) {
            AlarmStreamVolumeController.acquire(context)
        } else {
            null
        }
        var player: MediaPlayer? = null
        val failed = AtomicBoolean(false)
        val finished = CountDownLatch(1)

        return try {
            player = MediaPlayer().apply {
                setAudioAttributes(AppAudioAttributes.sonification)
                setDataSource(context.applicationContext, uri)
                setVolume(playerGain, playerGain)
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
            streamVolumeLease?.close()
        }
    }

    private const val TAG = "CueSoundPlayer"
}
