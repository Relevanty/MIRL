package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Напоминание с периодичностью.
 *
 * Каждое напоминание планирует два AlarmManager-события:
 * - pre (на nextTriggerTime - 5 минут): беззвучное уведомление с обратным отсчётом
 * - fire (на nextTriggerTime): звуковое уведомление с действиями
 *
 * requestCode для pre = reminderId * 2
 * requestCode для fire = reminderId * 2 + 1
 */
@Entity(
    tableName = "reminders",
    indices = [Index(value = ["nextTriggerTime"])]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,

    /** Час срабатывания (0..23). */
    val timeHour: Int,

    /** Минута срабатывания (0..59). */
    val timeMinute: Int,

    val repeatMode: RepeatMode,

    /**
     * Битовая маска выбранных дней недели (только для WEEKLY).
     * Пн=1, Вт=2, Ср=4, Чт=8, Пт=16, Сб=32, Вс=64.
     * Для остальных режимов = 0.
     */
    val daysOfWeek: Int = 0,

    /**
     * Интервал в днях (только для INTERVAL).
     * Для остальных режимов = 1.
     */
    val intervalDays: Int = 1,

    /**
     * Рассчитанное время следующего срабатывания (epoch millis).
     * Пересчитывается после каждого срабатывания для повторяющихся режимов.
     */
    val nextTriggerTime: Long,

    val isEnabled: Boolean = true,

    val createdAt: Long = System.currentTimeMillis()
)