package com.personal.sleepalarm.ui.focusprotocol

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import com.personal.sleepalarm.data.preferences.FocusSoundPreferences
import com.personal.sleepalarm.domain.focusaudio.CustomFocusSoundFile
import com.personal.sleepalarm.domain.focusaudio.FocusSoundRules
import com.personal.sleepalarm.domain.focusaudio.FocusSoundKind
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSettings
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSelection
import com.personal.sleepalarm.domain.focusaudio.FocusSoundscapeSelection
import com.personal.sleepalarm.service.focus.FocusProtocolConfig
import com.personal.sleepalarm.service.audio.VoiceScenario
import com.personal.sleepalarm.service.audio.FocusSoundPlaybackStatus
import com.personal.sleepalarm.service.audio.FocusSoundscapeController
import com.personal.sleepalarm.service.audio.soundscapeMix
import com.personal.sleepalarm.service.audio.soundscapeSelection
import com.personal.sleepalarm.service.audio.toPlaybackMix
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job

data class EnergyHourPoint(
    val hour: Int,
    val averageBefore: Float,
    val averageAfter: Float?,
    val sampleCount: Int
)

internal fun buildCompletedEnergyPattern(
    completedBlocks: List<FocusProtocolSessionEntity>,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<EnergyHourPoint> = completedBlocks
    .filter { it.phase == FocusProtocolPhase.COMPLETE && it.completedAt != null }
    .groupBy { block ->
        Instant.ofEpochMilli(block.createdAt).atZone(zoneId).hour
    }
    .map { (hour, blocks) ->
        EnergyHourPoint(
            hour = hour,
            averageBefore = blocks.map { it.energyBefore }.average().toFloat(),
            averageAfter = blocks.mapNotNull { it.energyAfter }.let { values ->
                values.takeIf { it.isNotEmpty() }?.average()?.toFloat()
            },
            sampleCount = blocks.size
        )
    }
    .sortedBy(EnergyHourPoint::hour)

data class FocusSoundDraft(
    val selection: FocusSoundscapeSelection = FocusSoundscapeSelection(),
    val primaryVolumePercent: Int = FocusSoundRules.DEFAULT_VOLUME_PERCENT,
    val rememberForTask: Boolean = false
)

class FocusProtocolViewModel(application: Application) : AndroidViewModel(application) {
    private val locator = (application as App).serviceLocator
    private val repository = locator.focusProtocolRepository
    private val manager = locator.focusProtocolManager
    private val voice = locator.briefingCoordinator
    private val soundPreferences = FocusSoundPreferences(application)
    private val soundscapeController = FocusSoundscapeController.get(application)
    private var soundPreviewJob: Job? = null
    private var soundDraftLoadJob: Job? = null
    private var soundPreviewActive = false

    val focusSoundSettings: StateFlow<FocusSoundSettings> = soundPreferences.observeSettings()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FocusSoundSettings()
        )
    val soundscapePlayback = soundscapeController.state

    val activeSession: StateFlow<FocusProtocolSessionEntity?> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestSession: StateFlow<FocusProtocolSessionEntity?> = repository.observeLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentCompletedBlocks: StateFlow<List<FocusProtocolSessionEntity>> = repository
        .observeRecentCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val energyPattern: StateFlow<List<EnergyHourPoint>> = repository
        .observeCompletedFrom(System.currentTimeMillis() - ENERGY_HISTORY_DAYS * DAY_MS)
        .map(::buildCompletedEnergyPattern)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val profile: StateFlow<AlarmProfileEntity?> = locator.database.alarmProfileDao()
        .observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _remainingMillis = MutableStateFlow(0L)
    val remainingMillis: StateFlow<Long> = _remainingMillis

    init {
        viewModelScope.launch { manager.reconcileActiveSessions(resumeSoundscape = true) }
        viewModelScope.launch {
            activeSession.collectLatest { session ->
                if (session == null) {
                    _remainingMillis.value = 0L
                    return@collectLatest
                }
                if (session.phase == FocusProtocolPhase.FOCUS_PAUSED) {
                    _remainingMillis.value = session.pausedRemainingMillis
                    return@collectLatest
                }
                while (session.phase.hasCountdown) {
                    val remaining = ((session.phaseEndsAt ?: 0L) - System.currentTimeMillis())
                        .coerceAtLeast(0L)
                    _remainingMillis.value = remaining
                    if (remaining == 0L) {
                        // The AlarmManager receiver is the single transition owner.
                        // UI only renders the countdown, so process restarts and
                        // notification taps cannot create duplicate phase records.
                        break
                    }
                    delay(500L)
                }
            }
        }
    }

    fun start(
        activityType: FocusActivityType,
        itemId: Int,
        itemName: String,
        outcome: String,
        resetMinutes: Int,
        focusMinutes: Int,
        recoveryMinutes: Int,
        energyBefore: Int,
        soundscape: FocusSoundscapeSelection = FocusSoundscapeSelection(),
        soundscapeVolumePercent: Int = FocusSoundRules.DEFAULT_VOLUME_PERCENT,
        rememberSoundscapeForTask: Boolean = false,
        persistedTaskId: Int? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (outcome.isBlank() || itemName.isBlank()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val startedId = runCatching {
                manager.start(
                    FocusProtocolConfig(
                        activityType = activityType,
                        itemId = itemId,
                        itemName = itemName,
                        outcome = outcome,
                        resetMinutes = resetMinutes,
                        focusMinutes = focusMinutes,
                        recoveryMinutes = recoveryMinutes,
                        energyBefore = energyBefore,
                        soundscapeId = soundscape.normalized().primary.catalogId,
                        soundscapeCustomUri = soundscape.normalized().primary.customFile?.uriString,
                        soundscapeCustomName = soundscape.normalized().primary.customFile?.displayName,
                        soundscapeVolume = soundscapeVolumePercent,
                        soundscapeSecondaryId = soundscape.normalized().secondaryLayerId,
                        soundscapeSecondaryVolume = soundscape.normalized().secondaryVolumePercent,
                        soundscapePlayDuringRecovery = soundscape.normalized().playDuringRecovery
                    )
                )
            }.getOrElse {
                onResult(false)
                return@launch
            }
            onResult(startedId > 0)
            if (startedId > 0) {
                runCatching {
                    soundPreferences.setVolume(soundscapeVolumePercent)
                    if (persistedTaskId != null && persistedTaskId > 0) {
                        soundPreferences.selectForTask(
                            taskId = persistedTaskId,
                            selection = soundscape,
                            rememberForTask = rememberSoundscapeForTask
                        )
                    } else {
                        soundPreferences.select(soundscape, setAsDefault = true)
                    }
                }
                runCatching {
                    voice.speak("Подготовка к фокусу. Цель: «$outcome».", VoiceScenario.FOCUS) {}
                }
            }
        }
    }

    fun skipReset(id: Int) = viewModelScope.launch { manager.skipReset(id) }
    // Phase cues are owned by FocusProtocolManager. Extra TTS here would overlap the
    // continuous soundscape and duplicate the same transition for the user.
    fun startFocus(id: Int) = viewModelScope.launch { manager.startFocus(id) }
    fun pauseFocus(id: Int) = viewModelScope.launch { manager.pauseFocus(id) }
    fun resumeFocus(id: Int) = viewModelScope.launch { manager.resumeFocus(id) }
    fun finishFocus(id: Int) = viewModelScope.launch { manager.finishFocus(id) }
    fun finishRecovery(id: Int) = viewModelScope.launch { manager.finishRecovery(id) }
    fun repeatCycle(id: Int) = viewModelScope.launch { manager.startNextCycle(id) }
    fun switchTargetAndRepeat(
        id: Int,
        activityType: FocusActivityType,
        itemId: Int,
        itemName: String,
        outcome: String
    ) = viewModelScope.launch {
        manager.startNextCycle(id, activityType, itemId, itemName, outcome)
    }
    fun finishBlock(id: Int) = viewModelScope.launch { manager.finishBlock(id) }
    fun markDistraction(id: Int) = viewModelScope.launch { manager.incrementDistraction(id) }
    fun cancel(id: Int, reason: String) = viewModelScope.launch { manager.cancel(id, reason) }
    fun completeReview(id: Int, energyAfter: Int) =
        viewModelScope.launch { manager.completeReview(id, energyAfter) }

    fun loadSoundDraft(
        taskId: Int?,
        fallbackSelection: FocusSoundscapeSelection? = null,
        fallbackVolumePercent: Int? = null,
        onLoaded: (FocusSoundDraft) -> Unit
    ): Job {
        soundDraftLoadJob?.cancel()
        return viewModelScope.launch {
            val settings = soundPreferences.getSettings()
            val taskOverride = taskId?.takeIf { it > 0 }?.let {
                soundPreferences.getTaskOverride(it)
            }
            val baseSelection = taskOverride ?: fallbackSelection ?: settings.defaultSelection
            val selection = baseSelection.withCustomMetadataFrom(
                buildList {
                    taskOverride?.let { add(it.primary) }
                    add(settings.defaultSelection.primary)
                    add(settings.lastSelection.primary)
                    addAll(settings.recentSelections)
                }
            )
            onLoaded(
                FocusSoundDraft(
                    selection = selection.normalized(),
                    primaryVolumePercent = FocusSoundRules.normalizeVolume(
                        fallbackVolumePercent ?: settings.volumePercent
                    ),
                    rememberForTask = taskOverride != null
                )
            )
        }.also { soundDraftLoadJob = it }
    }

    fun cancelSoundDraftLoad() {
        soundDraftLoadJob?.cancel()
        soundDraftLoadJob = null
    }

    fun previewSoundscape(
        selection: FocusSoundscapeSelection,
        primaryVolumePercent: Int,
        previewSessionId: Int? = null
    ) {
        soundPreviewJob?.cancel()
        soundPreviewActive = true
        soundscapeController.play(
            selection.toPlaybackMix(primaryVolumePercent),
            focusSessionId = previewSessionId
        )
        soundPreviewJob = viewModelScope.launch {
            delay(SOUND_PREVIEW_MILLIS)
            val active = activeSession.value
            val phaseNowOwnsPlayback = previewSessionId != null &&
                active?.id == previewSessionId &&
                active.soundscapePhaseAllowsPlayback()
            if (!phaseNowOwnsPlayback) soundscapeController.stop()
            soundPreviewActive = false
            soundPreviewJob = null
        }
    }

    fun stopSoundscapePreview() {
        val ownedPreview = soundPreviewActive
        soundPreviewJob?.cancel()
        soundPreviewJob = null
        soundPreviewActive = false
        if (ownedPreview || activeSession.value == null) soundscapeController.stop()
    }

    fun adjustSoundscapeVolumes(
        selection: FocusSoundscapeSelection,
        primaryPercent: Int,
        secondaryPercent: Int
    ) {
        val safe = selection.normalized()
        soundscapeController.setMasterVolume(primaryPercent.coerceIn(0, 100) / 100f)
        val noiseLayerVolume = soundscapeNoiseLayerVolume(safe, secondaryPercent)
        soundscapeController.setLayerVolume(
            com.personal.sleepalarm.service.audio.FocusSoundLayer.NOISE,
            noiseLayerVolume
        )
    }

    fun updateActiveSoundscape(
        sessionId: Int,
        selection: FocusSoundscapeSelection,
        primaryVolumePercent: Int,
        taskId: Int? = null,
        rememberForTask: Boolean = false,
        previewIfInactive: Boolean = false
    ) = viewModelScope.launch {
        val updated = manager.updateSoundscape(sessionId, selection, primaryVolumePercent)
        soundPreferences.setVolume(primaryVolumePercent)
        if (taskId != null && taskId > 0) {
            soundPreferences.selectForTask(taskId, selection, rememberForTask)
        } else {
            soundPreferences.select(selection, setAsDefault = true)
        }
        if (previewIfInactive && updated != null && !updated.soundscapePhaseAllowsPlayback()) {
            previewSoundscape(
                selection = updated.soundscapeSelection(),
                primaryVolumePercent = updated.soundscapeVolume,
                previewSessionId = updated.id
            )
        }
    }

    fun toggleSoundscapePlayback(session: FocusProtocolSessionEntity? = activeSession.value) {
        val current = session ?: return
        val status = soundscapeController.state.value.status
        if (status == FocusSoundPlaybackStatus.PLAYING || status == FocusSoundPlaybackStatus.LOADING) {
            if (soundPreviewActive) stopSoundscapePreview() else soundscapeController.pause()
        } else if (current.soundscapePhaseAllowsPlayback()) {
            soundscapeController.play(current.soundscapeMix(), current.id)
        } else {
            previewSoundscape(
                selection = current.soundscapeSelection(),
                primaryVolumePercent = current.soundscapeVolume,
                previewSessionId = current.id
            )
        }
    }

    fun toggleSoundFavorite(id: String) = viewModelScope.launch {
        soundPreferences.toggleFavorite(id)
    }

    fun createCustomSound(
        uri: Uri,
        persistablePermissionTaken: Boolean,
        onReady: (FocusSoundSelection?) -> Unit
    ) {
        viewModelScope.launch {
            if (!persistablePermissionTaken) {
                onReady(null)
                return@launch
            }
            val selection = withContext(Dispatchers.IO) {
                customSoundSelection(uri, persistablePermissionTaken)
            }
            if (selection == null) {
                releaseCustomSoundPermission(uri)
            } else {
                selection.customFile?.let { soundPreferences.addCustomFiles(listOf(it)) }
            }
            onReady(selection)
        }
    }

    fun importCustomSounds(
        documents: List<Pair<Uri, Boolean>>,
        onReady: (imported: List<FocusSoundSelection>, failedCount: Int) -> Unit
    ) {
        viewModelScope.launch {
            val uniqueDocuments = documents.distinctBy { it.first.toString() }
            val imported = withContext(Dispatchers.IO) {
                uniqueDocuments.mapNotNull { (uri, permissionTaken) ->
                    if (!permissionTaken) return@mapNotNull null
                    val selection = customSoundSelection(uri, true)
                    if (selection == null) releaseCustomSoundPermission(uri)
                    selection
                }
            }
            soundPreferences.addCustomFiles(imported.mapNotNull(FocusSoundSelection::customFile))
            val storedUris = soundPreferences.getSettings().customLibrary
                .mapTo(hashSetOf(), CustomFocusSoundFile::uriString)
            val stored = imported.filter { it.customFile?.uriString in storedUris }
            imported.asSequence()
                .filterNot { it.customFile?.uriString in storedUris }
                .mapNotNull(FocusSoundSelection::customFile)
                .filter(CustomFocusSoundFile::persistablePermissionTaken)
                .forEach { releaseCustomSoundPermission(Uri.parse(it.uriString)) }
            onReady(stored, uniqueDocuments.size - stored.size)
        }
    }

    fun removeCustomSound(file: CustomFocusSoundFile) = viewModelScope.launch {
        soundPreferences.removeCustomFile(file.uriString)
    }

    fun isBedtimeRisk(totalMinutes: Int, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val currentProfile = profile.value ?: return false
        val bedtime = calculateNextBedtime(
            nowMillis = nowMillis,
            wakeHour = currentProfile.preferredWakeHour,
            wakeMinute = currentProfile.preferredWakeMinute,
            sleepMinutes = currentProfile.cycles * currentProfile.cycleLengthMinutes +
                currentProfile.onsetLatencyMinutes,
            zoneId = ZoneId.systemDefault()
        )
        return bedtime <= nowMillis ||
            nowMillis + (totalMinutes + WIND_DOWN_BUFFER_MINUTES) * MINUTE_MS >= bedtime
    }

    override fun onCleared() {
        cancelSoundDraftLoad()
        soundPreviewJob?.cancel()
        soundPreviewJob = null
        if (soundPreviewActive && activeSession.value?.soundscapePhaseAllowsPlayback() != true) {
            soundscapeController.stop()
        }
        soundPreviewActive = false
        super.onCleared()
    }

    private fun FocusSoundscapeSelection.withCustomMetadataFrom(
        candidates: List<FocusSoundSelection>
    ): FocusSoundscapeSelection {
        val safe = normalized()
        val uri = safe.primary.customFile?.uriString ?: return safe
        val metadata = candidates.asSequence()
            .map(FocusSoundSelection::normalized)
            .filter { it.catalogId == com.personal.sleepalarm.domain.focusaudio.FocusSoundCatalog.CUSTOM_FILE_ID }
            .mapNotNull { it.customFile }
            .firstOrNull { it.uriString == uri }
            ?: return safe
        return safe.copy(primary = FocusSoundSelection.custom(metadata)).normalized()
    }

    private fun FocusProtocolSessionEntity.soundscapePhaseAllowsPlayback(): Boolean =
        phase == FocusProtocolPhase.FOCUS ||
            (phase == FocusProtocolPhase.RECOVERY && soundscapePlayDuringRecovery)

    companion object {
        private const val MINUTE_MS = 60_000L
        private const val DAY_MS = 24L * 60L * MINUTE_MS
        private const val ENERGY_HISTORY_DAYS = 14L
        private const val WIND_DOWN_BUFFER_MINUTES = 30
        private const val SOUND_PREVIEW_MILLIS = 8_000L

        internal fun calculateNextBedtime(
            nowMillis: Long,
            wakeHour: Int,
            wakeMinute: Int,
            sleepMinutes: Int,
            zoneId: ZoneId
        ): Long {
            val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
            val todayWake = LocalDate.of(now.year, now.month, now.dayOfMonth)
                .atTime(wakeHour.coerceIn(0, 23), wakeMinute.coerceIn(0, 59))
                .atZone(zoneId)
            val nextWake = if (todayWake.toInstant().toEpochMilli() > nowMillis) {
                todayWake
            } else {
                todayWake.plusDays(1)
            }
            return nextWake.minusMinutes(sleepMinutes.coerceAtLeast(0).toLong())
                .toInstant()
                .toEpochMilli()
        }
    }

    private fun customSoundSelection(
        uri: Uri,
        persistablePermissionTaken: Boolean
    ): FocusSoundSelection? {
        val resolver = getApplication<Application>().contentResolver
        val readable = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
        if (!readable) return null
        var displayName = "Мой аудиофайл"
        var sizeBytes: Long? = null
        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it >= 0 }
                        ?.let { displayName = cursor.getString(it).orEmpty().ifBlank { displayName } }
                    cursor.getColumnIndex(OpenableColumns.SIZE)
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { sizeBytes = cursor.getLong(it) }
                }
            }
        }
        var durationMillis: Long? = null
        var artist: String? = null
        var album: String? = null
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(getApplication(), uri)
                durationMillis = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { displayName = it }
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
            } finally {
                retriever.release()
            }
        }
        return FocusSoundSelection.custom(
            CustomFocusSoundFile(
                uriString = uri.toString(),
                displayName = displayName,
                mimeType = resolver.getType(uri),
                durationMillis = durationMillis,
                sizeBytes = sizeBytes,
                persistablePermissionTaken = persistablePermissionTaken,
                artist = artist,
                album = album,
                addedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private fun releaseCustomSoundPermission(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }
}

internal fun soundscapeNoiseLayerVolume(
    selection: FocusSoundscapeSelection,
    secondaryPercent: Int
): Float {
    val safe = selection.normalized()
    return if (
        safe.primary.entry().kind == FocusSoundKind.GENERATED_NOISE &&
        safe.secondaryLayerId == null
    ) {
        1f
    } else {
        secondaryPercent.coerceIn(0, 100) / 100f
    }
}
