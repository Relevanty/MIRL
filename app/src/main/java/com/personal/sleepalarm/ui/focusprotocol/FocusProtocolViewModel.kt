package com.personal.sleepalarm.ui.focusprotocol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import com.personal.sleepalarm.data.db.entity.FocusProtocolSessionEntity
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import com.personal.sleepalarm.service.focus.FocusProtocolConfig
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

data class EnergyHourPoint(
    val hour: Int,
    val average: Float,
    val sampleCount: Int
)

class FocusProtocolViewModel(application: Application) : AndroidViewModel(application) {
    private val locator = (application as App).serviceLocator
    private val repository = locator.focusProtocolRepository
    private val manager = locator.focusProtocolManager

    val activeSession: StateFlow<FocusProtocolSessionEntity?> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestSession: StateFlow<FocusProtocolSessionEntity?> = repository.observeLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentCompletedBlocks: StateFlow<List<FocusProtocolSessionEntity>> = repository
        .observeRecentCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val energyPattern: StateFlow<List<EnergyHourPoint>> = repository
        .observeEnergyFrom(System.currentTimeMillis() - ENERGY_HISTORY_DAYS * DAY_MS)
        .map { samples ->
            samples.filter { it.context == "BEFORE_FOCUS" }.groupBy { sample ->
                Instant.ofEpochMilli(sample.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .hour
            }.map { (hour, values) ->
                EnergyHourPoint(
                    hour = hour,
                    average = values.map { it.energy }.average().toFloat(),
                    sampleCount = values.size
                )
            }.sortedBy { it.hour }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val profile: StateFlow<AlarmProfileEntity?> = locator.database.alarmProfileDao()
        .observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _remainingMillis = MutableStateFlow(0L)
    val remainingMillis: StateFlow<Long> = _remainingMillis

    init {
        viewModelScope.launch { manager.reconcileActiveSessions() }
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
                        manager.advanceIfDue(session.id)
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
        energyBefore: Int
    ) {
        if (outcome.isBlank() || itemName.isBlank()) return
        viewModelScope.launch {
            manager.start(
                FocusProtocolConfig(
                    activityType = activityType,
                    itemId = itemId,
                    itemName = itemName,
                    outcome = outcome,
                    resetMinutes = resetMinutes,
                    focusMinutes = focusMinutes,
                    recoveryMinutes = recoveryMinutes,
                    energyBefore = energyBefore
                )
            )
        }
    }

    fun skipReset(id: Int) = viewModelScope.launch { manager.skipReset(id) }
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

    companion object {
        private const val MINUTE_MS = 60_000L
        private const val DAY_MS = 24L * 60L * MINUTE_MS
        private const val ENERGY_HISTORY_DAYS = 14L
        private const val WIND_DOWN_BUFFER_MINUTES = 30

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
}
