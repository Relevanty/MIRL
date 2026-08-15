package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Обратный отсчёт до события (D-Day).
 *
 * Отображается в бейджах на главном экране и экране Помодоро, а также
 * в голосовом брифинге при подъёме («До X осталось N дней»).
 *
 * Без авто-сдвига подъёма — только отображение и озвучка.
 */
@Entity(
    tableName = "dday_events",
    indices = [Index(value = ["targetDate"])]
)
data class DDayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,

    /** Целевая дата события в формате yyyy-MM-dd. */
    val targetDate: String,

    val createdAt: Long = System.currentTimeMillis()
)