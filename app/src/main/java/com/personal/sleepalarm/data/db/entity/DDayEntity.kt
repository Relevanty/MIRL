package com.personal.sleepalarm.data.db.entity

import androidx.room.ColumnInfo
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
    indices = [Index(value = ["targetDate"]), Index(value = ["taskId"], unique = true)]
)
data class DDayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,

    /** Standalone date, or a display cache of the linked task's canonical dueAtMillis. */
    val targetDate: String,

    val projectId: Int? = null,
    val taskId: Int? = null,
    val notes: String = "",

    /** JSON array of validated HTTP(S) links. Existing deadlines start without links. */
    @ColumnInfo(defaultValue = "'[]'")
    val linksJson: String = "[]",

    val createdAt: Long = System.currentTimeMillis()
)
