package com.personal.sleepalarm.service.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The sole owner of continuous focus audio in this process.
 *
 * It owns one AudioManager focus request and one noisy-output receiver even when a mix contains
 * two layers. Generated layers share one AudioTrack; an actual local file is only split into a
 * second backend when decoding it into the generated PCM stream would add unacceptable battery
 * and codec complexity.
 */
class FocusSoundscapeController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(FocusSoundscapeState())
    val state: StateFlow<FocusSoundscapeState> = _state.asStateFlow()

    private var backend: FocusMixBackend? = null
    private val playbackGeneration = AtomicLong(0L)
    private var audioFocusGranted = false
    private var resumeAfterTransientLoss = false
    private var outputReceiverRegistered = false
    private var alarmStreamLease: AutoCloseable? = null
    private var focusSessionId: Int? = null
    private var duckMultiplier = 1f

    private val continuousAttributes = AudioAttributes.Builder()
        // MIRL's user-requested routing policy keeps every app sound on the alarm route.
        .setUsage(AppAudioAttributes.USAGE)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(continuousAttributes)
        .setAcceptsDelayedFocusGain(false)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(::onAudioFocusChanged, mainHandler)
        .build()

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pauseInternal(FocusSoundPauseReason.OUTPUT_DISCONNECTED, abandonFocus = true)
                FocusSoundscapeService.detach(appContext)
            }
        }
    }

    /**
     * Starts or smoothly replaces the complete sound environment.
     *
     * Pass the active focus session id for real concentration playback. The media-playback FGS
     * then reuses that session's existing notification id. Omitting it is intended for a short
     * foreground preview only.
     */
    @JvmOverloads
    fun play(
        mix: FocusSoundMix,
        focusSessionId: Int? = null,
        fadeMs: Long = DEFAULT_CROSSFADE_MS,
    ) {
        val normalized = mix.normalized()
        if (normalized.isSilent) {
            stop()
            return
        }
        val sessionId = focusSessionId?.takeIf { it > 0 } ?: this.focusSessionId
        if (sessionId != null) {
            this.focusSessionId = sessionId
            FocusSoundscapeService.play(appContext, sessionId, normalized, fadeMs)
        } else {
            playInProcess(normalized, fadeMs)
        }
    }

    /** Alias used when a picker changes the environment during an active block. */
    fun switchTo(mix: FocusSoundMix, fadeMs: Long = DEFAULT_CROSSFADE_MS) {
        play(mix = mix, focusSessionId = focusSessionId, fadeMs = fadeMs)
    }

    fun setMasterVolume(volume: Float) {
        updateMix(_state.value.mix.copy(masterVolume = volume.coerceIn(0f, 1f)))
    }

    fun setLayerVolume(layer: FocusSoundLayer, volume: Float) {
        val current = _state.value.mix
        val updated = when (layer) {
            FocusSoundLayer.PRIMARY -> current.copy(
                primary = current.primary?.copy(volume = volume.coerceIn(0f, 1f))
            )
            FocusSoundLayer.NOISE -> current.copy(
                noise = current.noise?.copy(volume = volume.coerceIn(0f, 1f))
            )
        }
        updateMix(updated)
    }

    fun pause() {
        pauseInternal(FocusSoundPauseReason.USER, abandonFocus = true)
        FocusSoundscapeService.detach(appContext)
    }

    fun resume() {
        val current = _state.value
        if (current.status != FocusSoundPlaybackStatus.PAUSED || current.mix.isSilent) return
        val sessionId = focusSessionId
        if (sessionId != null) {
            FocusSoundscapeService.play(appContext, sessionId, current.mix, RESUME_FADE_MS)
        } else {
            resumeInProcess()
        }
    }

    fun stop() {
        stopInProcess(clearSession = true)
        FocusSoundscapeService.stop(appContext)
    }

    internal fun playInProcess(mix: FocusSoundMix, fadeMs: Long) {
        val normalized = mix.normalized()
        val generation = playbackGeneration.incrementAndGet()
        scope.launch {
            _state.value = FocusSoundscapeReducer.reduce(
                _state.value,
                FocusSoundscapeEvent.Load(normalized),
            )
            if (!requestAudioFocus()) {
                val oldBackend = backend
                backend = null
                silenceAndRelease(oldBackend)
                unregisterOutputReceiver()
                abandonAudioFocus()
                releaseAlarmStreamLease()
                _state.value = FocusSoundscapeReducer.reduce(
                    _state.value,
                    FocusSoundscapeEvent.Failed("audio_focus_unavailable"),
                )
                FocusSoundscapeService.detach(appContext)
                return@launch
            }

            var prepared: PreparedMixBackend? = null
            var candidateBackend: FocusMixBackend? = null
            var candidateInstalled = false
            var replacedBackend: FocusMixBackend? = null
            try {
                prepared = withContext(Dispatchers.IO) {
                    FocusMixBackendFactory.create(appContext, normalized, continuousAttributes)
                }
                val newBackend = prepared.backend
                candidateBackend = newBackend
                val usedFallback = prepared.usesProceduralFallback
                prepared = null
                if (generation != playbackGeneration.get()) {
                    silenceAndRelease(newBackend)
                    return@launch
                }
                val oldBackend = backend
                replacedBackend = oldBackend
                backend = newBackend
                candidateInstalled = true

                // A slider can change while an optional file is preparing. Its latest gain is
                // applied without reopening the source.
                newBackend.setMix(_state.value.mix)
                newBackend.setOutputGain(0f)
                if (!_state.value.mix.isSilent) ensureAlarmStreamLease()
                newBackend.start()
                registerOutputReceiver()
                _state.value = FocusSoundscapeReducer.reduce(
                    _state.value,
                    FocusSoundscapeEvent.Started(usedFallback),
                )

                crossfade(
                    oldBackend,
                    newBackend,
                    fadeMs.coerceIn(0L, MAX_CROSSFADE_MS),
                    generation,
                )
                silenceAndRelease(oldBackend)
                replacedBackend = null
                candidateBackend = null
                if (backend === newBackend) applyOutputGain()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (generation == playbackGeneration.get()) {
                    val currentBackend = backend
                    backend = null
                    silenceAndRelease(
                        prepared?.backend,
                        candidateBackend,
                        replacedBackend,
                        currentBackend,
                    )
                    unregisterOutputReceiver()
                    abandonAudioFocus()
                    releaseAlarmStreamLease()
                } else {
                    silenceAndRelease(
                        prepared?.backend,
                        replacedBackend,
                        candidateBackend?.takeUnless { candidateInstalled },
                    )
                }
                throw cancelled
            } catch (error: Throwable) {
                if (generation == playbackGeneration.get()) {
                    // Preparation and startup failures are terminal for the requested switch.
                    // Silence both the candidate and the previously audible backend before the
                    // FGS is detached; otherwise an inaccessible custom URI can leave the old
                    // ambience playing with no foreground owner.
                    val currentBackend = backend
                    backend = null
                    silenceAndRelease(
                        prepared?.backend,
                        candidateBackend,
                        replacedBackend,
                        currentBackend,
                    )
                    unregisterOutputReceiver()
                    abandonAudioFocus()
                    releaseAlarmStreamLease()
                    duckMultiplier = 1f
                    resumeAfterTransientLoss = false
                    _state.value = FocusSoundscapeReducer.reduce(
                        _state.value,
                        FocusSoundscapeEvent.Failed(
                            error.javaClass.simpleName.ifBlank { "playback_error" }
                        ),
                    )
                    FocusSoundscapeService.detach(appContext)
                } else {
                    // A newer generation owns [backend]. Retire only objects that this failed
                    // generation no longer shares with it.
                    silenceAndRelease(
                        prepared?.backend,
                        replacedBackend,
                        candidateBackend?.takeUnless { candidateInstalled },
                    )
                }
            }
        }
    }

    internal fun bindFocusSession(sessionId: Int) {
        focusSessionId = sessionId.takeIf { it > 0 }
    }

    internal fun boundFocusSessionId(): Int? = focusSessionId

    internal fun pauseFromService(reason: FocusSoundPauseReason) {
        pauseInternal(reason, abandonFocus = true)
    }

    /**
     * Stops output synchronously from the service lifecycle without asking Context to stop the
     * service again. Playback is paused before resources are retired, so no fade can outlive FGS
     * ownership. Resource release itself stays off the main thread.
     */
    internal fun stopImmediatelyFromService(clearSession: Boolean) {
        playbackGeneration.incrementAndGet()
        val old = backend
        backend = null
        silenceAndRelease(old)
        unregisterOutputReceiver()
        abandonAudioFocus()
        releaseAlarmStreamLease()
        duckMultiplier = 1f
        resumeAfterTransientLoss = false
        if (clearSession) focusSessionId = null
        _state.value = FocusSoundscapeReducer.reduce(_state.value, FocusSoundscapeEvent.Stopped)
    }

    private fun updateMix(mix: FocusSoundMix) {
        val normalized = mix.normalized()
        val playbackStatus = _state.value.status
        _state.value = _state.value.copy(mix = normalized)
        if (normalized.isSilent) {
            releaseAlarmStreamLease()
        } else if (playbackStatus == FocusSoundPlaybackStatus.PLAYING) {
            ensureAlarmStreamLease()
        }
        backend?.setMix(normalized)
        applyOutputGain()
        // Slider moves are intentionally in-process only. The UI persists the final value in
        // Room; starting the service for every drag frame would enqueue redundant DB validation
        // work and the durable session remains the only process-restoration source of truth.
    }

    private fun pauseInternal(reason: FocusSoundPauseReason, abandonFocus: Boolean) {
        playbackGeneration.incrementAndGet()
        runCatching { backend?.pause() }
        unregisterOutputReceiver()
        duckMultiplier = 1f
        if (abandonFocus) abandonAudioFocus()
        releaseAlarmStreamLease()
        _state.value = FocusSoundscapeReducer.reduce(
            _state.value,
            FocusSoundscapeEvent.Paused(reason),
        )
    }

    private fun resumeInProcess() {
        scope.launch {
            if (!requestAudioFocus()) {
                _state.value = FocusSoundscapeReducer.reduce(
                    _state.value,
                    FocusSoundscapeEvent.Failed("audio_focus_unavailable"),
                )
                return@launch
            }
            val currentBackend = backend
            if (currentBackend == null) {
                abandonAudioFocus()
                _state.value = FocusSoundscapeReducer.reduce(
                    _state.value,
                    FocusSoundscapeEvent.Failed("playback_backend_unavailable"),
                )
                return@launch
            }
            ensureAlarmStreamLease()
            try {
                currentBackend.resume()
            } catch (error: Throwable) {
                releaseAlarmStreamLease()
                abandonAudioFocus()
                _state.value = FocusSoundscapeReducer.reduce(
                    _state.value,
                    FocusSoundscapeEvent.Failed(
                        error.javaClass.simpleName.ifBlank { "playback_error" }
                    ),
                )
                FocusSoundscapeService.detach(appContext)
                return@launch
            }
            registerOutputReceiver()
            _state.value = FocusSoundscapeReducer.reduce(_state.value, FocusSoundscapeEvent.Resumed)
            fadeCurrentFromSilence(RESUME_FADE_MS)
        }
    }

    private fun stopInProcess(clearSession: Boolean) {
        playbackGeneration.incrementAndGet()
        val old = backend
        backend = null
        val streamLease = takeAlarmStreamLease()
        scope.launch {
            try {
                fadeOutAndRelease(old, STOP_FADE_MS)
            } finally {
                streamLease?.close()
            }
        }
        unregisterOutputReceiver()
        abandonAudioFocus()
        duckMultiplier = 1f
        resumeAfterTransientLoss = false
        if (clearSession) focusSessionId = null
        _state.value = FocusSoundscapeReducer.reduce(_state.value, FocusSoundscapeEvent.Stopped)
    }

    private fun silenceAndRelease(vararg candidates: FocusMixBackend?) {
        val unique = buildList {
            candidates.filterNotNull().forEach { candidate ->
                if (none { it === candidate }) add(candidate)
            }
        }
        unique.forEach { candidate ->
            runCatching { candidate.setOutputGain(0f) }
            runCatching { candidate.pause() }
        }
        if (unique.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            unique.forEach { candidate -> runCatching { candidate.release() } }
        }
    }

    private suspend fun crossfade(
        oldBackend: FocusMixBackend?,
        newBackend: FocusMixBackend,
        durationMs: Long,
        generation: Long,
    ) {
        if (durationMs <= 0L) {
            oldBackend?.setOutputGain(0f)
            applyOutputGain(newBackend, 1f)
            return
        }
        val steps = max(1, (durationMs / FADE_FRAME_MS).toInt())
        repeat(steps + 1) { step ->
            if (generation != playbackGeneration.get()) return
            val progress = step.toFloat() / steps
            oldBackend?.let { applyOutputGain(it, 1f - progress) }
            applyOutputGain(newBackend, progress)
            if (step < steps) delay(FADE_FRAME_MS)
        }
    }

    private suspend fun fadeCurrentFromSilence(durationMs: Long) {
        val current = backend ?: return
        current.setOutputGain(0f)
        val steps = max(1, (durationMs / FADE_FRAME_MS).toInt())
        repeat(steps + 1) { step ->
            applyOutputGain(current, step.toFloat() / steps)
            if (step < steps) delay(FADE_FRAME_MS)
        }
    }

    private suspend fun fadeOutAndRelease(old: FocusMixBackend?, durationMs: Long) {
        if (old == null) return
        val steps = max(1, (durationMs / FADE_FRAME_MS).toInt())
        repeat(steps + 1) { step ->
            applyOutputGain(old, 1f - step.toFloat() / steps)
            if (step < steps) delay(FADE_FRAME_MS)
        }
        old.release()
    }

    private fun requestAudioFocus(): Boolean {
        if (audioFocusGranted) return true
        audioFocusGranted = audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return audioFocusGranted
    }

    private fun abandonAudioFocus() {
        if (!audioFocusGranted) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        audioFocusGranted = false
    }

    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocusGranted = true
                duckMultiplier = 1f
                _state.value = FocusSoundscapeReducer.reduce(
                    _state.value,
                    FocusSoundscapeEvent.FocusGained,
                )
                if (resumeAfterTransientLoss) {
                    resumeAfterTransientLoss = false
                    val currentBackend = backend
                    if (currentBackend != null && !_state.value.mix.isSilent) {
                        ensureAlarmStreamLease()
                        val resumed = runCatching { currentBackend.resume() }.isSuccess
                        if (resumed) {
                            registerOutputReceiver()
                            _state.value = FocusSoundscapeReducer.reduce(
                                _state.value,
                                FocusSoundscapeEvent.Resumed,
                            )
                        } else {
                            releaseAlarmStreamLease()
                            _state.value = FocusSoundscapeReducer.reduce(
                                _state.value,
                                FocusSoundscapeEvent.Failed("playback_resume_error"),
                            )
                            FocusSoundscapeService.detach(appContext)
                        }
                    }
                }
                applyOutputGain()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duckMultiplier = DUCK_GAIN
                _state.value = FocusSoundscapeReducer.reduce(
                    _state.value,
                    FocusSoundscapeEvent.Ducked,
                )
                applyOutputGain()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeAfterTransientLoss = _state.value.status == FocusSoundPlaybackStatus.PLAYING
                pauseInternal(FocusSoundPauseReason.AUDIO_FOCUS, abandonFocus = false)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                audioFocusGranted = false
                resumeAfterTransientLoss = false
                pauseInternal(FocusSoundPauseReason.AUDIO_FOCUS, abandonFocus = false)
                FocusSoundscapeService.detach(appContext)
            }
        }
    }

    private fun applyOutputGain(target: FocusMixBackend? = backend, fade: Float = 1f) {
        val mix = _state.value.mix
        target?.setOutputGain(
            FocusSoundGainPolicy.outputGain(
                masterVolume = mix.masterVolume,
                duckMultiplier = duckMultiplier,
                fade = fade,
            )
        )
    }

    private fun ensureAlarmStreamLease() {
        if (alarmStreamLease == null) {
            alarmStreamLease = AlarmStreamVolumeController.acquire(appContext)
        }
    }

    private fun takeAlarmStreamLease(): AutoCloseable? = alarmStreamLease.also {
        alarmStreamLease = null
    }

    private fun releaseAlarmStreamLease() {
        takeAlarmStreamLease()?.close()
    }

    private fun registerOutputReceiver() {
        if (outputReceiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        outputReceiverRegistered = true
    }

    private fun unregisterOutputReceiver() {
        if (!outputReceiverRegistered) return
        runCatching { appContext.unregisterReceiver(becomingNoisyReceiver) }
        outputReceiverRegistered = false
    }

    companion object {
        private const val DEFAULT_CROSSFADE_MS = 700L
        private const val MAX_CROSSFADE_MS = 2_000L
        private const val RESUME_FADE_MS = 350L
        private const val STOP_FADE_MS = 220L
        private const val FADE_FRAME_MS = 28L
        private const val DUCK_GAIN = 0.18f

        @Volatile
        private var instance: FocusSoundscapeController? = null

        fun get(context: Context): FocusSoundscapeController = instance ?: synchronized(this) {
            instance ?: FocusSoundscapeController(context).also { instance = it }
        }
    }
}

/** Applies the perceptual curve exactly once to the user-facing master slider. */
internal object FocusSoundGainPolicy {
    fun outputGain(
        masterVolume: Float,
        duckMultiplier: Float = 1f,
        fade: Float = 1f,
    ): Float = (
        AppVolumeScale.gainForFraction(masterVolume) *
            duckMultiplier.coerceIn(0f, 1f) *
            fade.coerceIn(0f, 1f)
        ).coerceIn(0f, 1f)

    /** Leaves ordinary samples untouched and limits only genuine mixed peaks. */
    fun limitMixedSample(sample: Float): Float = sample.coerceIn(-1f, 1f)
}

private data class PreparedMixBackend(
    val backend: FocusMixBackend,
    val usesProceduralFallback: Boolean,
)

private interface FocusMixBackend {
    fun start()
    fun pause()
    fun resume()
    fun setMix(mix: FocusSoundMix)
    fun setOutputGain(gain: Float)
    fun release()
}

private object FocusMixBackendFactory {
    private const val SAMPLE_RATE = 24_000

    fun create(
        context: Context,
        mix: FocusSoundMix,
        attributes: AudioAttributes,
    ): PreparedMixBackend {
        val layers = listOfNotNull(mix.primary, mix.noise)
        val canUseOneGeneratedOutput = layers.all { selection ->
            when (selection.source) {
                FocusSoundSource.Silence, is FocusSoundSource.Noise -> true
                is FocusSoundSource.Bundled -> false
                is FocusSoundSource.CustomFile -> false
            }
        }
        if (canUseOneGeneratedOutput) {
            val output = GeneratedMixBackend(
                primary = mix.primary?.source?.let {
                    FocusPcmGeneratorFactory.create(it, SAMPLE_RATE)
                },
                noise = mix.noise?.source?.let {
                    FocusPcmGeneratorFactory.create(it, SAMPLE_RATE)
                },
                attributes = attributes,
                sampleRate = SAMPLE_RATE,
            )
            return PreparedMixBackend(
                backend = output,
                usesProceduralFallback = false,
            )
        }

        val primary = mix.primary?.let { selection ->
            createLayer(context, selection.source, attributes)
        }
        val noise = mix.noise?.let { selection ->
            createLayer(context, selection.source, attributes)
        }
        return PreparedMixBackend(
            backend = CompositeMixBackend(primary?.backend, noise?.backend),
            usesProceduralFallback = primary?.usesFallback == true || noise?.usesFallback == true,
        )
    }

    private fun createLayer(
        context: Context,
        source: FocusSoundSource,
        attributes: AudioAttributes,
    ): PreparedLayerBackend = when (source) {
        FocusSoundSource.Silence -> PreparedLayerBackend(null, false)
        is FocusSoundSource.Noise -> PreparedLayerBackend(
            GeneratedMixBackend(
                primary = FocusPcmGeneratorFactory.create(source, SAMPLE_RATE),
                noise = null,
                attributes = attributes,
                sampleRate = SAMPLE_RATE,
            ),
            false,
        )
        is FocusSoundSource.CustomFile -> PreparedLayerBackend(
            MediaLayerBackend.forUri(context, source.uri, attributes),
            false,
        )
        is FocusSoundSource.Bundled -> {
            PreparedLayerBackend(
                MediaLayerBackend.forAsset(context, source.assetPath.orEmpty(), attributes),
                false,
            )
        }
    }

    private data class PreparedLayerBackend(
        val backend: FocusMixBackend?,
        val usesFallback: Boolean,
    )
}

private class GeneratedMixBackend(
    private val primary: FocusPcmGenerator?,
    private val noise: FocusPcmGenerator?,
    attributes: AudioAttributes,
    sampleRate: Int,
) : FocusMixBackend {
    private val released = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val waitLock = Object()
    private val minBuffer = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(sampleRate / 5 * 2)
    private val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(minBuffer)
        .build()
        .also { track ->
            check(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack initialization failed" }
        }
    private val samples = ShortArray(960)
    @Volatile private var primaryGain = 0f
    @Volatile private var noiseGain = 0f
    @Volatile private var outputGain = 0f

    private val writer = Thread({ writeLoop() }, "MIRL-focus-audio").apply {
        priority = Thread.NORM_PRIORITY + 1
        isDaemon = true
        start()
    }

    override fun start() {
        if (released.get()) return
        running.set(true)
        audioTrack.play()
        synchronized(waitLock) { waitLock.notifyAll() }
    }

    override fun pause() {
        if (released.get()) return
        running.set(false)
        runCatching { audioTrack.pause() }
    }

    override fun resume() = start()

    override fun setMix(mix: FocusSoundMix) {
        primaryGain = mix.primary?.volume?.coerceIn(0f, 1f) ?: 0f
        noiseGain = mix.noise?.volume?.coerceIn(0f, 1f) ?: 0f
    }

    override fun setOutputGain(gain: Float) {
        outputGain = gain.coerceIn(0f, 1f)
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        running.set(false)
        synchronized(waitLock) { waitLock.notifyAll() }
        writer.interrupt()
        runCatching { audioTrack.pause() }
        runCatching { audioTrack.flush() }
        runCatching { writer.join(350L) }
        runCatching { audioTrack.release() }
    }

    private fun writeLoop() {
        while (!released.get()) {
            if (!running.get()) {
                synchronized(waitLock) {
                    if (!running.get() && !released.get()) {
                        runCatching { waitLock.wait(500L) }
                    }
                }
                continue
            }
            val pGain = primaryGain
            val nGain = noiseGain
            val master = outputGain
            samples.indices.forEach { index ->
                val mixed =
                    (primary?.nextSample() ?: 0f) * pGain +
                        (noise?.nextSample() ?: 0f) * nGain
                val limited = FocusSoundGainPolicy.limitMixedSample(mixed)
                samples[index] = (limited * master * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            val written = try {
                audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            } catch (_: Throwable) {
                break
            }
            if (written < 0) break
        }
    }
}

private class CompositeMixBackend(
    private val primary: FocusMixBackend?,
    private val noise: FocusMixBackend?,
) : FocusMixBackend {
    private var mix = FocusSoundMix()
    private var outputGain = 0f

    override fun start() {
        primary?.start()
        noise?.start()
    }

    override fun pause() {
        primary?.pause()
        noise?.pause()
    }

    override fun resume() {
        primary?.resume()
        noise?.resume()
    }

    override fun setMix(mix: FocusSoundMix) {
        this.mix = mix
        primary?.setMix(
            FocusSoundMix(
                primary = mix.primary,
                masterVolume = 1f,
            )
        )
        noise?.setMix(
            FocusSoundMix(
                primary = mix.noise,
                masterVolume = 1f,
            )
        )
        setOutputGain(outputGain)
    }

    override fun setOutputGain(gain: Float) {
        outputGain = gain.coerceIn(0f, 1f)
        primary?.setOutputGain(outputGain)
        noise?.setOutputGain(outputGain)
    }

    override fun release() {
        primary?.release()
        noise?.release()
    }
}

private class MediaLayerBackend private constructor(
    private val player: MediaPlayer,
) : FocusMixBackend {
    private var layerGain = 1f
    private var outputGain = 0f

    override fun start() = player.start()
    override fun pause() {
        if (player.isPlaying) player.pause()
    }
    override fun resume() = player.start()

    override fun setMix(mix: FocusSoundMix) {
        layerGain = mix.primary?.volume?.coerceIn(0f, 1f) ?: 0f
        applyVolume()
    }

    override fun setOutputGain(gain: Float) {
        outputGain = gain.coerceIn(0f, 1f)
        applyVolume()
    }

    override fun release() {
        runCatching { player.stop() }
        runCatching { player.release() }
    }

    private fun applyVolume() {
        val value = layerGain * outputGain
        runCatching { player.setVolume(value, value) }
    }

    companion object {
        fun forUri(
            context: Context,
            uriString: String,
            attributes: AudioAttributes,
        ): MediaLayerBackend {
            require(uriString.isNotBlank()) { "Empty custom sound URI" }
            return build(attributes) { player ->
                player.setDataSource(context, Uri.parse(uriString))
            }
        }

        fun forAsset(
            context: Context,
            rawPath: String,
            attributes: AudioAttributes,
        ): MediaLayerBackend {
            require(rawPath.isNotBlank()) { "Empty bundled asset path" }
            return build(attributes) { player ->
                val descriptor: AssetFileDescriptor = context.assets.openFd(rawPath)
                descriptor.use {
                    player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
        }

        private inline fun build(
            attributes: AudioAttributes,
            setSource: (MediaPlayer) -> Unit,
        ): MediaLayerBackend {
            val player = MediaPlayer()
            try {
                player.setAudioAttributes(attributes)
                setSource(player)
                player.isLooping = true
                player.prepare()
                return MediaLayerBackend(player)
            } catch (error: Throwable) {
                runCatching { player.release() }
                throw error
            }
        }
    }
}
