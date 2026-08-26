package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.sleepalarm.domain.model.FocusActivityType

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

    val isBreak: Boolean = false,

    /** Категория фокуса. Для старых записей миграция устанавливает STUDY. */
    val activityType: FocusActivityType = FocusActivityType.STUDY,

    /** Связь с конкретным элементом выбранной категории. */
    val subjectId: Int? = null,
    val taskId: Int? = null,
    val otherActivityId: Int? = null,

    /** Снимок названия: история остаётся читаемой после переименования/удаления. */
    val itemName: String = "",

    /** Фактически отработанное время, включая неполные фокус-сессии. */
    val actualDurationMillis: Long = 0L,

    /** TIMER — таймер, MANUAL — внесённое задним числом событие. */
    val recordSource: String = "TIMER"
)
