package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Одна помодоро-сессия: фокус или перерыв.
 *
 * startedAt — время начала.
 * completedAt — время завершения (null, если сессия не завершена/прервана).
 * isCompleted — дошла ли сессия до конца таймера.
 * isBreak — это перерыв, а не фокус.
 */
@Entity(
    tableName = "pomodoro_sessions",
    indices = [Index(value = ["startedAt"])]
)
data class PomodoroSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val startedAt: Long,

    val durationMinutes: Int,

    val completedAt: Long? = null,

    val isCompleted: Boolean = false,

    val isBreak: Boolean = false
)