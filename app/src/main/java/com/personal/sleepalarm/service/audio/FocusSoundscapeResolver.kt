package com.personal.sleepalarm.service.audio

import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.domain.focusaudio.CustomFocusSoundFile
import com.personal.sleepalarm.domain.focusaudio.FocusSoundCatalog
import com.personal.sleepalarm.domain.focusaudio.FocusSoundKind
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSelection
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSettings
import com.personal.sleepalarm.domain.focusaudio.FocusSoundscapeSelection

/** Explicit boundary between persistable catalogue selections and playback-only source objects. */
fun FocusSoundscapeSelection.toPlaybackMix(masterVolumePercent: Int): FocusSoundMix {
    val selection = normalized()
    val primarySource = selection.primary.toPlaybackSource()
    val secondarySource = selection.secondaryLayerId
        ?.let(com.personal.sleepalarm.domain.focusaudio.FocusSoundCatalog::find)
        ?.noiseColor
        ?.toPlaybackColor()
        ?.let { FocusSoundSource.Noise(it) }

    val primaryLayer = primarySource
        ?.takeUnless { it is FocusSoundSource.Noise }
        ?.let { FocusSoundLayerSelection(it, 1f) }
    val primaryNoise = (primarySource as? FocusSoundSource.Noise)
        ?.let { FocusSoundLayerSelection(it, 1f) }
    val secondaryNoise = secondarySource?.let {
        FocusSoundLayerSelection(
            source = it,
            volume = selection.secondaryVolumePercent / 100f,
        )
    }
    return FocusSoundMix(
        primary = primaryLayer,
        noise = secondaryNoise ?: primaryNoise,
        masterVolume = masterVolumePercent.coerceIn(0, 100) / 100f,
    ).normalized()
}

fun FocusSoundSettings.lastPlaybackMix(): FocusSoundMix =
    normalized().let { it.lastSelection.toPlaybackMix(it.volumePercent) }

fun FocusProtocolSessionEntity.soundscapeSelection(): FocusSoundscapeSelection {
    val primary = if (
        soundscapeId == FocusSoundCatalog.CUSTOM_FILE_ID &&
        !soundscapeCustomUri.isNullOrBlank()
    ) {
        FocusSoundSelection.custom(
            CustomFocusSoundFile(
                uriString = soundscapeCustomUri,
                displayName = soundscapeCustomName?.takeIf { it.isNotBlank() } ?: "Custom audio",
                persistablePermissionTaken = true
            )
        )
    } else {
        FocusSoundSelection(soundscapeId)
    }
    return FocusSoundscapeSelection(
        primary = primary,
        secondaryLayerId = soundscapeSecondaryId,
        secondaryVolumePercent = soundscapeSecondaryVolume,
        playDuringRecovery = soundscapePlayDuringRecovery
    ).normalized()
}

fun FocusProtocolSessionEntity.soundscapeMix(): FocusSoundMix =
    soundscapeSelection().toPlaybackMix(soundscapeVolume)

private fun FocusSoundSelection.toPlaybackSource(): FocusSoundSource? {
    val safe = normalized()
    val entry = safe.entry()
    return when (entry.kind) {
        FocusSoundKind.SILENCE -> null
        FocusSoundKind.GENERATED_NOISE -> entry.noiseColor
            ?.toPlaybackColor()
            ?.let { FocusSoundSource.Noise(it) }
        FocusSoundKind.AMBIENCE -> FocusSoundSource.Bundled(
            stableId = entry.id,
            assetPath = entry.bundledAssetName,
            role = FocusProceduralRole.AMBIENCE,
        )
        FocusSoundKind.MELODY -> FocusSoundSource.Bundled(
            stableId = entry.id,
            assetPath = entry.bundledAssetName,
            role = FocusProceduralRole.MELODY,
        )
        FocusSoundKind.CUSTOM_FILE -> safe.customFile?.uriString?.let {
            FocusSoundSource.CustomFile(uri = it, stableId = safe.historyKey())
        }
    }
}

private fun com.personal.sleepalarm.domain.focusaudio.FocusNoiseColor.toPlaybackColor():
    FocusNoiseColor = when (this) {
    com.personal.sleepalarm.domain.focusaudio.FocusNoiseColor.WHITE -> FocusNoiseColor.WHITE
    com.personal.sleepalarm.domain.focusaudio.FocusNoiseColor.PINK -> FocusNoiseColor.PINK
    com.personal.sleepalarm.domain.focusaudio.FocusNoiseColor.BROWN -> FocusNoiseColor.BROWN
}
