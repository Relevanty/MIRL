package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Одна запись настроения в день.
 *
 * Сохраняется при успешном dismiss будильника. Одна запись на дату
 * (UNIQUE INDEX на date) — последняя за день перезаписывает предыдущую.
 *
 * Используется для корреляционной аналитики (сон ↔ настроение ↔ задачи)
 * и как входная фича для локальной нейронки.
 */
@Entity(
    tableName = "mood_entries",
    indices = [Index(value = ["date"], unique = true)]
)
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** Дата в формате yyyy-MM-dd. */
    val date: String,

    /** Оценка настроения: 1 (плохо) .. 5 (отлично). */
    val mood: Int,

    val createdAt: Long = System.currentTimeMillis()
)