package com.personal.sleepalarm.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.quickNotesDataStore by preferencesDataStore(name = "quick_notes_prefs")

/** Persistent free-form Markdown notes shown on the sleep screen. */
class QuickNotesPreference(
    private val context: Context
) {
    private companion object {
        val KEY_TEXT = stringPreferencesKey("quick_notes_text")
    }

    fun observeText(): Flow<String> =
        context.quickNotesDataStore.data.map { preferences -> preferences[KEY_TEXT].orEmpty() }

    suspend fun setText(value: String) {
        context.quickNotesDataStore.edit { preferences -> preferences[KEY_TEXT] = value }
    }
}
