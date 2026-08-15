package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Событие календаря. */
@Entity(tableName = "events", indices = [Index("startMillis")])
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val repeatRule: String,
    val reminderMinutes: Int?,
    val createdAt: Long = System.currentTimeMillis()
)