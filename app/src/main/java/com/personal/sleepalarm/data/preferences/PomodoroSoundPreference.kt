package com.personal.sleepalarm.data.preferences

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pomodoroSoundDataStore by preferencesDataStore(name = "pomodoro_sound_prefs")

/** Persisted sound selection shared by UI timers and background protocol alarms. */
class PomodoroSoundPreference(private val context: Context) {
    private val appContext = context.applicationContext

    fun observeUri(): Flow<Uri?> = appContext.pomodoroSoundDataStore.data.map { preferences ->
        preferences[KEY_URI]?.let { value -> runCatching { Uri.parse(value) }.getOrNull() }
    }

    suspend fun getUri(): Uri? = observeUri().first()

    suspend fun setUri(uri: Uri?) {
        appContext.pomodoroSoundDataStore.edit { preferences ->
            if (uri == null) preferences.remove(KEY_URI)
            else preferences[KEY_URI] = uri.toString()
        }
    }

    private companion object {
        val KEY_URI = stringPreferencesKey("notification_sound_uri")
    }
}
