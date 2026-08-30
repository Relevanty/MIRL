package com.personal.sleepalarm.service.audio

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** Small, allocation-free PCM source used by the offline fallback and colored noises. */
internal fun interface FocusPcmGenerator {
    /** Returns one normalized mono sample in `[-1, 1]`. */
    fun nextSample(): Float
}

internal object FocusPcmGeneratorFactory {
    fun create(source: FocusSoundSource, sampleRate: Int): FocusPcmGenerator = when (source) {
        FocusSoundSource.Silence -> FocusPcmGenerator { 0f }
        is FocusSoundSource.Noise -> ColoredNoiseGenerator(source.color, source.seed)
        is FocusSoundSource.Bundled -> when (source.role) {
            FocusProceduralRole.AMBIENCE -> ProceduralAmbienceGenerator(
                profileId = source.stableId,
                seed = source.seed,
                sampleRate = sampleRate,
            )
            FocusProceduralRole.MELODY -> GenerativeMelodyGenerator(
                profileId = source.stableId,
                seed = source.seed,
                sampleRate = sampleRate,
            )
        }
        is FocusSoundSource.CustomFile -> FocusPcmGenerator { 0f }
    }
}

/** Deterministic PRNG so a fallback keeps the same character across playback sessions. */
internal class FocusXorShift(seed: Int) {
    private var state = if (seed == 0) 0x6D2B79F5 else seed

    fun nextFloat(): Float {
        var value = state
        value = value xor (value shl 13)
        value = value xor (value ushr 17)
        value = value xor (value shl 5)
        state = value
        return ((value ushr 8) / 8_388_607.5f) - 1f
    }

    fun nextUnit(): Float = (nextFloat() + 1f) * 0.5f
}

internal class ColoredNoiseGenerator(
    private val color: FocusNoiseColor,
    seed: Int,
) : FocusPcmGenerator {
    private val random = FocusXorShift(seed)
    private var brown = 0f
    private var p0 = 0f
    private var p1 = 0f
    private var p2 = 0f
    private var p3 = 0f
    private var p4 = 0f
    private var p5 = 0f
    private var p6 = 0f

    override fun nextSample(): Float {
        val white = random.nextFloat()
        return when (color) {
            FocusNoiseColor.WHITE -> white * 0.42f
            FocusNoiseColor.PINK -> {
                // Paul Kellet's economical pinking filter, scaled away from clipping.
                p0 = 0.99886f * p0 + white * 0.0555179f
                p1 = 0.99332f * p1 + white * 0.0750759f
                p2 = 0.96900f * p2 + white * 0.1538520f
                p3 = 0.86650f * p3 + white * 0.3104856f
                p4 = 0.55000f * p4 + white * 0.5329522f
                p5 = -0.7616f * p5 - white * 0.0168980f
                val pink = p0 + p1 + p2 + p3 + p4 + p5 + p6 + white * 0.5362f
                p6 = white * 0.115926f
                (pink * 0.075f).coerceIn(-0.92f, 0.92f)
            }
            FocusNoiseColor.BROWN -> {
                brown = (brown + white * 0.022f) / 1.022f
                (brown * 3.2f).coerceIn(-0.9f, 0.9f)
            }
        }
    }
}

private enum class AmbienceProfile {
    LIBRARY, WRITING, PAGES, KEYBOARD, CAFE, RAIN, STORM, WATER, WIND, FIRE,
    TRANSPORT, FAN, PURR, AQUARIUM, VINYL, ROOM,
}

/**
 * Lightweight synthesis is intentionally subtle. It is not sold as a replacement for a
 * studio recording; it is a safe, fully-offline fallback when an optional loop is absent.
 */
internal class ProceduralAmbienceGenerator(
    profileId: String,
    seed: Int,
    private val sampleRate: Int,
) : FocusPcmGenerator {
    private val random = FocusXorShift(seed)
    private val pink = ColoredNoiseGenerator(FocusNoiseColor.PINK, seed xor 0x37A6C91)
    private val brown = ColoredNoiseGenerator(FocusNoiseColor.BROWN, seed xor 0x13579B)
    private val profile = profileFor(profileId)
    private var frame = 0L
    private var lowPass = 0f
    private var eventEnvelope = 0f
    private var eventPhase = 0.0
    private var nextEvent = sampleRate.toLong() * (1L + ((seed.toLong() and 0x7fff_ffffL) % 4L))

    override fun nextSample(): Float {
        val time = frame.toDouble() / sampleRate
        val white = random.nextFloat()
        lowPass += 0.018f * (white - lowPass)
        if (frame >= nextEvent) triggerEvent()
        eventEnvelope *= when (profile) {
            AmbienceProfile.PAGES -> 0.99980f
            AmbienceProfile.STORM -> 0.99996f
            else -> 0.9975f
        }
        eventPhase += (2.0 * PI * (180.0 + random.nextUnit() * 90.0)) / sampleRate

        val value = when (profile) {
            AmbienceProfile.LIBRARY ->
                lowPass * 0.24f + pink.nextSample() * 0.09f + eventClick(0.06f)
            AmbienceProfile.WRITING -> {
                val stroke = max(0.0, sin(time * 2.0 * PI * 0.43)).toFloat()
                (white - lowPass) * stroke * 0.17f + pink.nextSample() * 0.055f
            }
            AmbienceProfile.PAGES ->
                pink.nextSample() * 0.055f + white * eventEnvelope * 0.36f
            AmbienceProfile.KEYBOARD ->
                lowPass * 0.08f + eventClick(0.52f)
            AmbienceProfile.CAFE -> {
                val murmur = sin(time * 2.0 * PI * 91.0) + sin(time * 2.0 * PI * 137.0)
                pink.nextSample() * 0.25f + murmur.toFloat() * 0.025f + eventClick(0.04f)
            }
            AmbienceProfile.RAIN ->
                pink.nextSample() * 0.32f + white * 0.07f + eventClick(0.08f)
            AmbienceProfile.STORM -> {
                val thunder = brown.nextSample() * eventEnvelope
                pink.nextSample() * 0.28f + white * 0.055f + thunder * 0.58f
            }
            AmbienceProfile.WATER -> {
                val swell = (0.42 + 0.30 * sin(time * 2.0 * PI * 0.095)).toFloat()
                pink.nextSample() * swell + lowPass * 0.22f + eventClick(0.035f)
            }
            AmbienceProfile.WIND -> {
                val gust = (0.28 + 0.23 * sin(time * 2.0 * PI * 0.071)).toFloat()
                brown.nextSample() * gust + pink.nextSample() * 0.09f
            }
            AmbienceProfile.FIRE ->
                brown.nextSample() * 0.16f + eventClick(0.64f)
            AmbienceProfile.TRANSPORT -> {
                val rumble = sin(time * 2.0 * PI * 43.0).toFloat() * 0.10f
                val rails = max(0.0, sin(time * 2.0 * PI * 1.72)).toFloat() * lowPass * 0.18f
                rumble + rails + brown.nextSample() * 0.18f
            }
            AmbienceProfile.FAN -> {
                val hum = sin(time * 2.0 * PI * 57.0).toFloat() * 0.075f
                hum + pink.nextSample() * 0.19f
            }
            AmbienceProfile.PURR -> {
                val carrier = sin(time * 2.0 * PI * 48.0)
                val breath = 0.55 + 0.30 * sin(time * 2.0 * PI * 0.31)
                (carrier * breath).toFloat() * 0.22f + brown.nextSample() * 0.05f
            }
            AmbienceProfile.AQUARIUM -> {
                val pump = sin(time * 2.0 * PI * 61.0).toFloat() * 0.045f
                pump + pink.nextSample() * 0.10f + eventClick(0.16f)
            }
            AmbienceProfile.VINYL ->
                white * 0.055f + lowPass * 0.05f + eventClick(0.42f)
            AmbienceProfile.ROOM ->
                lowPass * 0.20f + pink.nextSample() * 0.11f + eventClick(0.025f)
        }
        frame++
        return value.coerceIn(-0.95f, 0.95f)
    }

    private fun triggerEvent() {
        eventEnvelope = when (profile) {
            AmbienceProfile.STORM -> 1f
            AmbienceProfile.PAGES -> 0.75f
            else -> 0.4f + random.nextUnit() * 0.5f
        }
        val minSeconds = when (profile) {
            AmbienceProfile.STORM -> 9
            AmbienceProfile.PAGES -> 4
            AmbienceProfile.KEYBOARD, AmbienceProfile.FIRE, AmbienceProfile.VINYL -> 0
            else -> 2
        }
        val spreadSeconds = when (profile) {
            AmbienceProfile.STORM -> 18
            AmbienceProfile.PAGES -> 7
            AmbienceProfile.KEYBOARD, AmbienceProfile.FIRE, AmbienceProfile.VINYL -> 1
            else -> 5
        }
        val delay = if (minSeconds == 0) {
            (sampleRate * (0.045f + random.nextUnit() * 0.50f)).toLong()
        } else {
            sampleRate.toLong() * (minSeconds + (random.nextUnit() * spreadSeconds).toLong())
        }
        nextEvent = frame + delay.coerceAtLeast(1L)
    }

    private fun eventClick(scale: Float): Float =
        sin(eventPhase).toFloat() * eventEnvelope * scale

    private fun profileFor(rawId: String): AmbienceProfile {
        val id = rawId.lowercase()
        return when {
            listOf("pencil", "writing", "pen", "notes").any(id::contains) -> AmbienceProfile.WRITING
            listOf("page", "book", "paper").any(id::contains) -> AmbienceProfile.PAGES
            listOf("keyboard", "typing").any(id::contains) -> AmbienceProfile.KEYBOARD
            listOf("library", "archive", "reading", "museum").any(id::contains) -> AmbienceProfile.LIBRARY
            listOf("cafe", "classroom", "auditorium", "shop").any(id::contains) -> AmbienceProfile.CAFE
            listOf("storm", "thunder").any(id::contains) -> AmbienceProfile.STORM
            listOf("rain", "snow", "blizzard").any(id::contains) -> AmbienceProfile.RAIN
            listOf("ocean", "wave", "lake", "stream", "river", "water").any(id::contains) -> AmbienceProfile.WATER
            listOf("wind", "forest", "pine", "cricket", "night").any(id::contains) -> AmbienceProfile.WIND
            listOf("fire", "fireplace", "campfire").any(id::contains) -> AmbienceProfile.FIRE
            listOf("train", "tram", "car", "plane", "aircraft", "ferry", "cabin").any(id::contains) -> AmbienceProfile.TRANSPORT
            listOf("fan", "vent", "office").any(id::contains) -> AmbienceProfile.FAN
            listOf("purr", "cat").any(id::contains) -> AmbienceProfile.PURR
            listOf("aquarium", "bubble").any(id::contains) -> AmbienceProfile.AQUARIUM
            listOf("vinyl", "record").any(id::contains) -> AmbienceProfile.VINYL
            else -> AmbienceProfile.ROOM
        }
    }
}

/** Original, seed-based chord texture: no downloaded or copyrighted melody is embedded. */
internal class GenerativeMelodyGenerator(
    profileId: String,
    seed: Int,
    private val sampleRate: Int,
) : FocusPcmGenerator {
    private val random = FocusXorShift(seed)
    private val airyNoise = ColoredNoiseGenerator(FocusNoiseColor.PINK, seed xor 0x2468AC)
    private val baseFrequency = when {
        profileId.contains("piano", ignoreCase = true) -> 220.0
        profileId.contains("space", ignoreCase = true) -> 110.0
        profileId.contains("synth", ignoreCase = true) -> 146.83
        else -> 174.61
    }
    private val ratios = doubleArrayOf(1.0, 1.25, 1.5, 2.0)
    private var frame = 0L
    private var chord = ((seed.toLong() and 0x7fff_ffffL) % 4L).toInt()

    override fun nextSample(): Float {
        if (frame > 0L && frame % (sampleRate * 12L) == 0L) {
            chord = (chord + 1 + (random.nextUnit() * 2).toInt()) % ratios.size
        }
        val time = frame.toDouble() / sampleRate
        val slowEnvelope = (0.58 + 0.20 * sin(time * 2.0 * PI * 0.037)).toFloat()
        val root = baseFrequency * ratios[chord]
        val chordWave =
            sin(time * 2.0 * PI * root) * 0.34 +
                sin(time * 2.0 * PI * root * 1.25) * 0.23 +
                sin(time * 2.0 * PI * root * 1.5) * 0.18 +
                sin(time * 2.0 * PI * root * 2.0) * 0.08
        frame++
        return (chordWave.toFloat() * slowEnvelope * 0.34f + airyNoise.nextSample() * 0.035f)
            .coerceIn(-0.9f, 0.9f)
    }
}
