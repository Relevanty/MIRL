package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Задача или элемент утренней рутины.
 *
 * Для утренней рутины (isMorningRoutine = true) ведётся стрик выполнения:
 * при отметке сегодня streak+1 если doneDate==вчера, иначе reset в 1.
 * Визуальный сброс isDone происходит на уровне UI при наступлении нового дня.
 *
 * reminderId — связь с напоминанием (односторонняя). При удалении
 * напоминания ReminderRepository сбрасывает это поле в null.
 */
@Entity(
    tableName = "tasks",
    indices = [Index(value = ["isMorningRoutine"])]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,

    val isDone: Boolean = false,

    /** true — элемент утренней рутины (со стриком), false — обычная задача. */
    val isMorningRoutine: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),

    /** Время последней отметки выполнения (null если задача никогда не отмечалась). */
    val completedAt: Long? = null,

    /**
     * Дата последнего выполнения в формате yyyy-MM-dd.
     * Используется для расчёта стрика и визуального сброса на уровне UI.
     */
    val doneDate: String? = null,

    /**
     * Текущий стрик последовательных дней выполнения (только для утренней рутины).
     * Для обычных задач всегда 0.
     */
    val streakCount: Int = 0,

    /**
     * ID связанного напоминания, если пользователь нажал «создать напоминание»
     * для этой задачи. null — напоминания нет.
     */
    val reminderId: Int? = null
)