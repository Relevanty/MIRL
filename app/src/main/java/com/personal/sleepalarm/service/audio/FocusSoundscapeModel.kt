package com.personal.sleepalarm.service.audio

/**
 * A source understood by the process-wide focus sound player.
 *
 * The catalog deliberately lives outside the player. This keeps playback independent from
 * localized names and DataStore, and lets the UI resolve a catalog entry into one of these
 * stable, serializable descriptions.
 */
sealed interface FocusSoundSource {
    val stableId: String

    data object Silence : FocusSoundSource {
        override val stableId: String = "silence"
    }

    data class Noise(
        val color: FocusNoiseColor,
        val seed: Int = color.name.hashCode(),
    ) : FocusSoundSource {
        override val stableId: String = "noise:${color.name.lowercase()}"
    }

    /**
     * [assetPath] points below `assets/`. Missing or unreadable recordings are reported as a
     * playback error; MIRL never substitutes an unrealistic generated ambience.
     */
    data class Bundled(
        override val stableId: String,
        val assetPath: String? = null,
        val role: FocusProceduralRole = FocusProceduralRole.AMBIENCE,
        val seed: Int = stableId.hashCode(),
    ) : FocusSoundSource

    /** The URI permission is owned/persisted by the picker layer, not by the audio engine. */
    data class CustomFile(
        val uri: String,
        override val stableId: String = "custom",
    ) : FocusSoundSource
}

enum class FocusNoiseColor { WHITE, PINK, BROWN }

enum class FocusProceduralRole { AMBIENCE, MELODY }

enum class FocusSoundLayer { PRIMARY, NOISE }

data class FocusSoundLayerSelection(
    val source: FocusSoundSource,
    val volume: Float,
) {
    fun normalized(): FocusSoundLayerSelection = copy(volume = volume.coerceIn(0f, 1f))
}

/**
 * At most two simultaneous layers: one atmosphere/melody/custom file and one colored noise.
 * Empty layers are true silence; starting a default [FocusSoundMix] never emits audio.
 */
data class FocusSoundMix(
    val primary: FocusSoundLayerSelection? = null,
    val noise: FocusSoundLayerSelection? = null,
    val masterVolume: Float = DEFAULT_MASTER_VOLUME,
) {
    fun normalized(): FocusSoundMix {
        val normalizedPrimary = primary
            ?.takeUnless { it.source == FocusSoundSource.Silence }
            ?.normalized()
        val normalizedNoise = noise
            ?.takeUnless { it.source == FocusSoundSource.Silence }
            ?.takeIf { it.source is FocusSoundSource.Noise }
            ?.normalized()
        return copy(
            primary = normalizedPrimary,
            noise = normalizedNoise,
            masterVolume = masterVolume.coerceIn(0f, 1f),
        )
    }

    val isSilent: Boolean
        get() = normalized().let { mix ->
            mix.masterVolume <= 0f ||
                ((mix.primary == null || mix.primary.volume <= 0f) &&
                    (mix.noise == null || mix.noise.volume <= 0f))
        }

    companion object {
        const val DEFAULT_MASTER_VOLUME = 0.35f
    }
}

enum class FocusSoundPlaybackStatus { STOPPED, LOADING, PLAYING, PAUSED, ERROR }

enum class FocusSoundPauseReason { USER, AUDIO_FOCUS, OUTPUT_DISCONNECTED }

data class FocusSoundscapeState(
    val mix: FocusSoundMix = FocusSoundMix(),
    val status: FocusSoundPlaybackStatus = FocusSoundPlaybackStatus.STOPPED,
    val pauseReason: FocusSoundPauseReason? = null,
    val isDucked: Boolean = false,
    val usesProceduralFallback: Boolean = false,
    val errorMessage: String? = null,
)

/** Pure state transitions; Android playback side effects are performed by the controller. */
internal sealed interface FocusSoundscapeEvent {
    data class Load(val mix: FocusSoundMix) : FocusSoundscapeEvent
    data class Started(val usesProceduralFallback: Boolean) : FocusSoundscapeEvent
    data class Paused(val reason: FocusSoundPauseReason) : FocusSoundscapeEvent
    data object Resumed : FocusSoundscapeEvent
    data object Ducked : FocusSoundscapeEvent
    data object FocusGained : FocusSoundscapeEvent
    data object Stopped : FocusSoundscapeEvent
    data class Failed(val message: String) : FocusSoundscapeEvent
}

internal object FocusSoundscapeReducer {
    fun reduce(
        state: FocusSoundscapeState,
        event: FocusSoundscapeEvent,
    ): FocusSoundscapeState = when (event) {
        is FocusSoundscapeEvent.Load -> state.copy(
            mix = event.mix.normalized(),
            status = FocusSoundPlaybackStatus.LOADING,
            pauseReason = null,
            isDucked = false,
            errorMessage = null,
        )
        is FocusSoundscapeEvent.Started -> state.copy(
            status = FocusSoundPlaybackStatus.PLAYING,
            pauseReason = null,
            usesProceduralFallback = event.usesProceduralFallback,
            errorMessage = null,
        )
        is FocusSoundscapeEvent.Paused -> state.copy(
            status = FocusSoundPlaybackStatus.PAUSED,
            pauseReason = event.reason,
            isDucked = false,
        )
        FocusSoundscapeEvent.Resumed -> state.copy(
            status = FocusSoundPlaybackStatus.PLAYING,
            pauseReason = null,
            isDucked = false,
            errorMessage = null,
        )
        FocusSoundscapeEvent.Ducked -> state.copy(isDucked = true)
        FocusSoundscapeEvent.FocusGained -> state.copy(isDucked = false)
        FocusSoundscapeEvent.Stopped -> FocusSoundscapeState(mix = state.mix)
        is FocusSoundscapeEvent.Failed -> state.copy(
            status = FocusSoundPlaybackStatus.ERROR,
            pauseReason = null,
            isDucked = false,
            errorMessage = event.message,
        )
    }
}
