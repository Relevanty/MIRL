package com.personal.sleepalarm.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Пользовательское дело для категории «Другое» в помодоро. */
@Entity(tableName = "other_activities")
data class OtherActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val color: Int,
    val createdAt: Long = System.currentTimeMillis()
)
