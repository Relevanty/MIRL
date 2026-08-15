package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.sleepalarm.domain.model.CueType
import com.personal.sleepalarm.domain.model.DismissType

/**
 * Сессия сна.
 *
 * Поле cueType устарело и не используется (играет cueRingtoneUri),
 * но остаётся в таблице ради сохранности истории.
 */
@Entity(
    tableName = "sleep_sessions",
    indices = [
        Index(value = ["isActive"]),
        Index(value = ["estimatedWakeTime"]),
        Index(value = ["createdAt"])
    ]
)
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val bedTimePlanned: Long,
    val sleepOnsetLatencyMinutes: Int,
    val estimatedSleepStartTime: Long,
    val cycleLengthMinutes: Int,
    val cyclesPlanned: Int,
    val estimatedWakeTime: Long,
    val actualWakeTime: Long? = null,
    val dismissType: DismissType? = null,

    val cuesEnabled: Boolean,

    @Deprecated("Тип подсказки больше не используется. Играет cueRingtoneUri.")
    val cueType: CueType = CueType.BEEP,

    val cueVolumePercent: Int,
    val cuesScheduledCount: Int,
    val cuesPlayedCount: Int = 0,
    val cuesSkippedCount: Int = 0,

    val isActive: Boolean = true,
    val isSnoozeSession: Boolean = false,
    val parentSessionId: Int? = null,
    val zoneId: String = java.time.ZoneId.systemDefault().id,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Свой звук подсказки, зафиксированный на момент старта.
    val cueRingtoneUri: String? = null,

    // Автоопределение засыпания.
    val detectedSleepOnsetTime: Long? = null,
    val detectedOnsetLatencyMinutes: Int? = null
)