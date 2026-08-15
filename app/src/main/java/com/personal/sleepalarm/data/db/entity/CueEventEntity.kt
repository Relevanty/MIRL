package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.sleepalarm.domain.model.CueEventState

/**
 * Одна lucid-подсказка внутри сессии.
 *
 * sessionId + cueIndex уникальны.
 * Это нужно для защиты от дублей и для unique PendingIntent request code.
 */
@Entity(
    tableName = "cue_events",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["scheduledTime"]),
        Index(value = ["sessionId", "cueIndex"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CueEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /**
     * ID сессии сна.
     */
    val sessionId: Int,

    /**
     * Порядковый номер подсказки внутри сессии.
     * Начинается с 0.
     */
    val cueIndex: Int,

    /**
     * Запланированное время подсказки, epoch millis.
     */
    val scheduledTime: Long,

    /**
     * Состояние подсказки.
     */
    val state: CueEventState = CueEventState.SCHEDULED,

    /**
     * Когда подсказка была проиграна.
     */
    val playedAt: Long? = null,

    /**
     * Кто проиграл подсказку:
     * например "SERVICE" или "RECEIVER".
     */
    val playedBy: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)