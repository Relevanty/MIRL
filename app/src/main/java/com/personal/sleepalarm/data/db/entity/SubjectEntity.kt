package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Предмет для помодоро/учёбы (Математика, Физика и т.д.). */
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val color: Int,
    val createdAt: Long = System.currentTimeMillis()
)