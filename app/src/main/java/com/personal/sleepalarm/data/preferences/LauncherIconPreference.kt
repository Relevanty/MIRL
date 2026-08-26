package com.personal.sleepalarm.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.sleepalarm.launcher.LauncherIconCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.launcherIconDataStore by preferencesDataStore(name = "launcher_icon_prefs")

class LauncherIconPreference(private val context: Context) {

    private companion object {
        val KEY_SELECTED_ID = stringPreferencesKey("selected_launcher_icon_id")
        val KEY_AUTO_MATCH = booleanPreferencesKey("auto_match_launcher_icon")
    }

    fun observeSelectedId(): Flow<String> = context.launcherIconDataStore.data.map { preferences ->
        preferences[KEY_SELECTED_ID].takeIf(LauncherIconCatalog::isValid)
            ?: LauncherIconCatalog.DEFAULT_ID
    }

    fun observeAutoMatch(): Flow<Boolean> = context.launcherIconDataStore.data.map { preferences ->
        preferences[KEY_AUTO_MATCH] ?: false
    }

    suspend fun setSelectedId(id: String) {
        val safeId = id.takeIf(LauncherIconCatalog::isValid) ?: LauncherIconCatalog.DEFAULT_ID
        context.launcherIconDataStore.edit { it[KEY_SELECTED_ID] = safeId }
    }

    /** A manual choice and disabling auto-match must be observed as one state. */
    suspend fun selectManually(id: String) {
        val safeId = id.takeIf(LauncherIconCatalog::isValid) ?: LauncherIconCatalog.DEFAULT_ID
        context.launcherIconDataStore.edit {
            it[KEY_SELECTED_ID] = safeId
            it[KEY_AUTO_MATCH] = false
        }
    }

    suspend fun setAutoMatch(enabled: Boolean) {
        context.launcherIconDataStore.edit { it[KEY_AUTO_MATCH] = enabled }
    }

    suspend fun sanitizeSelection() {
        val raw = context.launcherIconDataStore.data.first()[KEY_SELECTED_ID]
        if (raw != null && !LauncherIconCatalog.isValid(raw)) {
            setSelectedId(LauncherIconCatalog.DEFAULT_ID)
        }
    }
}
