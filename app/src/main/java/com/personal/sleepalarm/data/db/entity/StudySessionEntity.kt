package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Одна завершённая фокус-сессия учёбы. */
@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectId: Int,
    val startMillis: Long,
    val endMillis: Long,
    val durationMillis: Long,
    val dateKey: String,
    val createdAt: Long = System.currentTimeMillis()
)