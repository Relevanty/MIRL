package com.personal.sleepalarm.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.briefingDataStore by preferencesDataStore(name = "briefing_prefs")

/**
 * Настройки голосового брифинга.
 *
 * Упрощено: только тумблер включения.
 * Движок всегда системный (Silero удалён).
 */
class BriefingPreference(
    private val context: Context
) {

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("briefing_enabled")
    }

    fun observeEnabled(): Flow<Boolean> =
        context.briefingDataStore.data.map { it[KEY_ENABLED] ?: true }

    suspend fun isEnabled(): Boolean = observeEnabled().first()

    suspend fun setEnabled(value: Boolean) {
        context.briefingDataStore.edit { it[KEY_ENABLED] = value }
    }
}