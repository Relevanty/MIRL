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
    val reminderId: Int? = null,

    /** Сектор матрицы Эйзенхауэра: 1 — сейчас, 2 — план, 3 — передать, 4 — отпустить. */
    val matrixQuadrant: Int = 2,

    /** Полная карточка результата и следующего действия. */
    val description: String = "",
    val whyImportant: String = "",
    val definitionOfDone: String = "",
    val nextAction: String = "",

    /** Локальная копия изображения в приватном хранилище приложения. */
    val imagePath: String? = null,

    /** Необязательный срок и оценка одного рабочего захода. */
    val dueAtMillis: Long? = null,
    val estimatedMinutes: Int = 25,

    /** Фактически отработанное время по таймеру или ручному событию. */
    val spentMillis: Long = 0L,

    /** Порядок шарика внутри квадранта матрицы. */
    val sortOrder: Int = 0,

    /** Условия выполнения: энергия, контекст, зависимости и план на случай препятствия. */
    val energyLevel: String = "MEDIUM",
    val contextTag: String = "",
    val dependencies: String = "",
    val obstacle: String = "",
    val ifThenPlan: String = "",

    /** Чек-лист хранится построчно; UI отмечает выполненные пункты префиксом [x]. */
    val checklist: String = "",
    val projectTag: String = "",
    val assignee: String = "",

    /** The whole task budget; zero means that no total limit is configured. */
    val workBudgetMinutes: Int = 0,
    val projectId: Int? = null,
    val category: String = "WORK",
    val tags: String = "",
    val materials: String = "",
    val expectedResult: String = "",
    val startAtMillis: Long? = null,
    val repeatRule: String = "",
    /** Daily focus target, reset at local midnight. */
    val plannedFocusMinutes: Int = 25,
    /** Opt-in: urgency/morning layers may require this target today. */
    val isDailyRequired: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
