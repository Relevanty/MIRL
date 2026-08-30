package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.FocusProtocolPhase

/**
 * Один рабочий блок: подготовка один раз, затем любое число циклов
 * «фокус -> восстановление» и итоговая оценка энергии.
 *
 * Время окончания каждой активной фазы сохраняется в БД, поэтому таймер можно
 * восстановить после уничтожения процесса и перезагрузки устройства.
 */
@Entity(
    tableName = "focus_protocol_sessions",
    indices = [Index("phase"), Index("createdAt")]
)
data class FocusProtocolSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val activityType: FocusActivityType,
    val itemId: Int,
    val itemName: String,
    val outcome: String,
    val phase: FocusProtocolPhase,
    val createdAt: Long,
    val phaseStartedAt: Long,
    val phaseEndsAt: Long? = null,
    val resetDurationMinutes: Int,
    val focusDurationMinutes: Int,
    val recoveryDurationMinutes: Int,
    val energyBefore: Int,
    val energyAfter: Int? = null,
    val distractionCount: Int = 0,
    val focusStartedAt: Long? = null,
    val focusElapsedMillis: Long = 0L,
    val pausedRemainingMillis: Long = 0L,
    val completedAt: Long? = null,
    val cancelReason: String? = null,
    val pomodoroRecorded: Boolean = false,
    val completedCycles: Int = 0,
    val totalFocusMillis: Long = 0L,
    /** Immutable soundscape snapshot for this block. */
    val soundscapeId: String = "silence",
    val soundscapeCustomUri: String? = null,
    val soundscapeCustomName: String? = null,
    val soundscapeVolume: Int = 35,
    val soundscapeSecondaryId: String? = null,
    val soundscapeSecondaryVolume: Int = 20,
    val soundscapePlayDuringRecovery: Boolean = false
)
