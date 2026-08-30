package com.personal.sleepalarm.service.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Temporarily opens Android's alarm stream to full scale while MIRL owns audio.
 *
 * Player gain alone cannot exceed the current system alarm level. This scoped,
 * process-wide controller lets the app's own 0..100 setting cover the complete
 * output range without permanently changing the user's alarm volume. Nested
 * sounds share one lease and the original level is restored by the last holder.
 */
object AlarmStreamVolumeController {
    private val lock = Any()
    private var state = AlarmStreamVolumeState()
    private var activeAudioManager: AudioManager? = null

    fun acquire(context: Context): AutoCloseable {
        synchronized(lock) {
            val manager = activeAudioManager ?: (
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ) ?: return NoOpLease
            val currentVolume = runCatching {
                manager.getStreamVolume(AudioManager.STREAM_ALARM)
            }.getOrElse {
                Log.w(TAG, "Unable to read alarm stream volume", it)
                return NoOpLease
            }
            val maxVolume = runCatching {
                manager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            }.getOrElse {
                Log.w(TAG, "Unable to read maximum alarm stream volume", it)
                return NoOpLease
            }

            val transition = AlarmStreamVolumePolicy.acquire(
                state = state,
                currentVolume = currentVolume,
                maxVolume = maxVolume
            )
            val requestedVolume = transition.volumeToSet
            if (requestedVolume != null) {
                val applied = runCatching {
                    manager.setStreamVolume(AudioManager.STREAM_ALARM, requestedVolume, 0)
                    manager.getStreamVolume(AudioManager.STREAM_ALARM)
                }.getOrElse {
                    runCatching {
                        manager.setStreamVolume(AudioManager.STREAM_ALARM, currentVolume, 0)
                    }
                    Log.w(TAG, "Unable to open alarm stream volume", it)
                    return NoOpLease
                }
                if (applied != requestedVolume) {
                    runCatching {
                        manager.setStreamVolume(AudioManager.STREAM_ALARM, currentVolume, 0)
                    }
                    Log.w(TAG, "Alarm stream rejected full volume: requested=$requestedVolume, applied=$applied")
                    return NoOpLease
                }
            }

            state = transition.state
            activeAudioManager = manager
            return ActiveLease(::release)
        }
    }

    private fun release() {
        synchronized(lock) {
            if (state.holderCount <= 0) return
            val manager = activeAudioManager
            if (manager == null) {
                state = AlarmStreamVolumeState()
                return
            }
            val currentVolume = runCatching {
                manager.getStreamVolume(AudioManager.STREAM_ALARM)
            }.getOrElse {
                Log.w(TAG, "Unable to read alarm stream volume while releasing", it)
                state = AlarmStreamVolumeState()
                activeAudioManager = null
                return
            }
            val transition = AlarmStreamVolumePolicy.release(state, currentVolume)
            state = transition.state
            transition.volumeToSet?.let { restoreVolume ->
                runCatching {
                    manager.setStreamVolume(AudioManager.STREAM_ALARM, restoreVolume, 0)
                }.onFailure {
                    Log.w(TAG, "Unable to restore alarm stream volume", it)
                }
            }
            if (state.holderCount == 0) activeAudioManager = null
        }
    }

    private class ActiveLease(
        private val releaseAction: () -> Unit
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) releaseAction()
        }
    }

    private object NoOpLease : AutoCloseable {
        override fun close() = Unit
    }

    private const val TAG = "AlarmStreamVolume"
}

internal data class AlarmStreamVolumeState(
    val holderCount: Int = 0,
    val originalVolume: Int? = null,
    val forcedVolume: Int? = null
)

internal data class AlarmStreamVolumeTransition(
    val state: AlarmStreamVolumeState,
    val volumeToSet: Int? = null
)

/** Pure reference-count and restoration policy, kept Android-free for tests. */
internal object AlarmStreamVolumePolicy {
    fun acquire(
        state: AlarmStreamVolumeState,
        currentVolume: Int,
        maxVolume: Int
    ): AlarmStreamVolumeTransition {
        if (state.holderCount > 0) {
            return AlarmStreamVolumeTransition(
                state = state.copy(holderCount = state.holderCount + 1)
            )
        }
        val safeMax = maxVolume.coerceAtLeast(0)
        return AlarmStreamVolumeTransition(
            state = AlarmStreamVolumeState(
                holderCount = 1,
                originalVolume = currentVolume.coerceAtLeast(0),
                forcedVolume = safeMax
            ),
            volumeToSet = safeMax.takeIf { it != currentVolume }
        )
    }

    fun release(
        state: AlarmStreamVolumeState,
        currentVolume: Int
    ): AlarmStreamVolumeTransition {
        if (state.holderCount <= 0) return AlarmStreamVolumeTransition(AlarmStreamVolumeState())
        if (state.holderCount > 1) {
            return AlarmStreamVolumeTransition(
                state = state.copy(holderCount = state.holderCount - 1)
            )
        }

        val restoreVolume = state.originalVolume?.takeIf { original ->
            state.forcedVolume == currentVolume && original != currentVolume
        }
        return AlarmStreamVolumeTransition(
            state = AlarmStreamVolumeState(),
            volumeToSet = restoreVolume
        )
    }
}
