package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Субъективная энергия пользователя в конкретный момент, от 1 до 10. */
@Entity(
    tableName = "energy_samples",
    indices = [Index("timestamp"), Index("protocolSessionId")]
)
data class EnergySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val energy: Int,
    /** BEFORE_FOCUS или AFTER_FOCUS. Строка оставляет формат расширяемым. */
    val context: String,
    val protocolSessionId: Int? = null
)
