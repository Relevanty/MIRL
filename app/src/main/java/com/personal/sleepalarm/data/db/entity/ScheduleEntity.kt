package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Расписание. Одна запись на всё приложение (id = 1),
 * по аналогии с профилем будильника.
 */
@Entity(tableName = "schedule")
data class ScheduleEntity(
    @PrimaryKey
    val id: Int = 1,

    val content: String = "",

    val updatedAt: Long = System.currentTimeMillis()
)