package com.personal.sleepalarm.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.personal.sleepalarm.data.db.entity.AlarmProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для профиля настроек.
 */
@Dao
interface AlarmProfileDao {

    @Query("DELETE FROM alarm_profiles")
    suspend fun deleteAll()

    /**
     * Наблюдение за профилем.
     * Если профиля ещё нет, вернётся null.
     */
    @Query("SELECT * FROM alarm_profiles WHERE id = 1")
    fun observeProfile(): Flow<AlarmProfileEntity?>

    /**
     * Однократное получение профиля.
     */
    @Query("SELECT * FROM alarm_profiles WHERE id = 1")
    suspend fun getProfile(): AlarmProfileEntity?

    /**
     * Вставка или обновление профиля.
     */
    @Upsert
    suspend fun upsert(profile: AlarmProfileEntity)

    /**
     * Быстрое включение/выключение cues.
     * Используется главным переключателем на домашнем экране.
     */
    @Query("UPDATE alarm_profiles SET cuesEnabled = :enabled, updatedAt = :updatedAt WHERE id = 1")
    suspend fun setCuesEnabled(enabled: Boolean, updatedAt: Long)
}