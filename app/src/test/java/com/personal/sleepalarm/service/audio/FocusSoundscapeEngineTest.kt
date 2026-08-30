package com.personal.sleepalarm.service.audio

import kotlin.math.abs
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSoundscapeEngineTest {
    @Test
    fun `service only restores audio for an allowed durable phase`() {
        assertTrue(allowsFocusSoundscapePlayback(FocusProtocolPhase.FOCUS, false))
        assertTrue(allowsFocusSoundscapePlayback(FocusProtocolPhase.FOCUS, true))
        assertFalse(allowsFocusSoundscapePlayback(FocusProtocolPhase.RECOVERY, false))
        assertTrue(allowsFocusSoundscapePlayback(FocusProtocolPhase.RECOVERY, true))

        FocusProtocolPhase.entries
            .filterNot { it == FocusProtocolPhase.FOCUS || it == FocusProtocolPhase.RECOVERY }
            .forEach { phase ->
                assertFalse(
                    "A redelivered service intent must not restore audio in $phase",
                    allowsFocusSoundscapePlayback(phase, true),
                )
            }
    }

    @Test
    fun `default mix is genuinely silent`() {
        val mix = FocusSoundMix()

        assertTrue(mix.isSilent)
        assertEquals(null, mix.primary)
        assertEquals(null, mix.noise)
    }

    @Test
    fun `normalization clamps volumes and rejects a second ambience as noise`() {
        val mix = FocusSoundMix(
            primary = FocusSoundLayerSelection(
                FocusSoundSource.Bundled("large_library"),
                3f,
            ),
            noise = FocusSoundLayerSelection(
                FocusSoundSource.Bundled("rainy_cafe"),
                0.5f,
            ),
            masterVolume = -1f,
        ).normalized()

        assertEquals(1f, mix.primary?.volume)
        assertEquals(null, mix.noise)
        assertEquals(0f, mix.masterVolume)
        assertTrue(mix.isSilent)
    }

    @Test
    fun `reducer retains selection through stop and clears transient flags`() {
        val mix = FocusSoundMix(
            noise = FocusSoundLayerSelection(
                FocusSoundSource.Noise(FocusNoiseColor.BROWN),
                0.25f,
            )
        )
        var state = FocusSoundscapeReducer.reduce(
            FocusSoundscapeState(),
            FocusSoundscapeEvent.Load(mix),
        )
        state = FocusSoundscapeReducer.reduce(state, FocusSoundscapeEvent.Started(false))
        state = FocusSoundscapeReducer.reduce(state, FocusSoundscapeEvent.Ducked)
        state = FocusSoundscapeReducer.reduce(state, FocusSoundscapeEvent.Stopped)

        assertEquals(FocusSoundPlaybackStatus.STOPPED, state.status)
        assertEquals(mix.normalized(), state.mix)
        assertFalse(state.isDucked)
        assertEquals(null, state.pauseReason)
    }

    @Test
    fun `colored noise is deterministic bounded and spectrally distinct`() {
        val whiteA = sample(ColoredNoiseGenerator(FocusNoiseColor.WHITE, 42), 12_000)
        val whiteB = sample(ColoredNoiseGenerator(FocusNoiseColor.WHITE, 42), 12_000)
        val pink = sample(ColoredNoiseGenerator(FocusNoiseColor.PINK, 42), 12_000)
        val brown = sample(ColoredNoiseGenerator(FocusNoiseColor.BROWN, 42), 12_000)

        assertTrue(whiteA.contentEquals(whiteB))
        assertTrue(whiteA.all { it in -1f..1f })
        assertTrue(pink.all { it in -1f..1f })
        assertTrue(brown.all { it in -1f..1f })
        assertTrue(meanStep(whiteA) > meanStep(pink))
        assertTrue(meanStep(pink) > meanStep(brown))
    }

    @Test
    fun `procedural profiles provide non-silent distinct offline fallbacks`() {
        val library = sample(
            FocusPcmGeneratorFactory.create(
                FocusSoundSource.Bundled("large_library", seed = 7),
                24_000,
            ),
            8_000,
        )
        val train = sample(
            FocusPcmGeneratorFactory.create(
                FocusSoundSource.Bundled("night_train", seed = 7),
                24_000,
            ),
            8_000,
        )
        val melody = sample(
            FocusPcmGeneratorFactory.create(
                FocusSoundSource.Bundled(
                    stableId = "minimal_piano",
                    role = FocusProceduralRole.MELODY,
                    seed = 7,
                ),
                24_000,
            ),
            8_000,
        )

        assertTrue(library.any { abs(it) > 0.0001f })
        assertTrue(train.any { abs(it) > 0.0001f })
        assertTrue(melody.any { abs(it) > 0.0001f })
        assertNotEquals(library.contentHashCode(), train.contentHashCode())
        assertNotEquals(library.contentHashCode(), melody.contentHashCode())
    }

    private fun sample(generator: FocusPcmGenerator, count: Int): FloatArray =
        FloatArray(count) { generator.nextSample() }

    private fun meanStep(samples: FloatArray): Float = samples
        .asSequence()
        .zipWithNext { a, b -> abs(b - a) }
        .average()
        .toFloat()
}
