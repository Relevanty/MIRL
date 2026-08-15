package com.personal.sleepalarm.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")

/**
 * Хранит только id выбранной темы.
 *
 * ДОБАВЛЕНО (v5): Material You / dynamicColor и ThemeMode удалены.
 * Старые значения (DARK/LIGHT/SYSTEM) мигрируются в themeId при чтении:
 * LIGHT → "day", DARK/SYSTEM → "night".
 */
class ThemePreference(
    private val context: Context
) {

    companion object {
        const val DEFAULT_THEME_ID = "night"

        private val KEY_THEME_ID = stringPreferencesKey("theme_id")
        // Старые ключи — только для миграции.
        private val KEY_LEGACY_THEME_MODE = stringPreferencesKey("theme_mode")
    }

    fun observeThemeId(): Flow<String> =
        context.themeDataStore.data.map { prefs ->
            prefs[KEY_THEME_ID] ?: migrateLegacy(prefs[KEY_LEGACY_THEME_MODE])
        }

    suspend fun setThemeId(themeId: String) {
        context.themeDataStore.edit { it[KEY_THEME_ID] = themeId }
    }

    private fun migrateLegacy(legacyMode: String?): String = when (legacyMode) {
        "LIGHT" -> "day"
        else -> DEFAULT_THEME_ID
    }
}