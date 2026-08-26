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
    /** PLANNED events never reduce task budget; ACTUAL work lives in activity_records. */
    val eventKind: String = "PLANNED",
    val taskId: Int? = null,
    val projectId: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
