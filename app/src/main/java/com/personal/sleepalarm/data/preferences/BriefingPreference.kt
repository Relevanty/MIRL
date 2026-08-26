package com.personal.sleepalarm.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.briefingDataStore by preferencesDataStore(name = "briefing_prefs")

data class BriefingVoiceSettings(
    val volumePercent: Int = 75,
    val ratePercent: Int = 100,
    val pitchPercent: Int = 100,
    val brevityPercent: Int = 60,
    val personalDataEnabled: Boolean = true,
    val headphonesOnly: Boolean = false,
    val morningEnabled: Boolean = true,
    val focusEnabled: Boolean = true,
    val reminderEnabled: Boolean = true,
    val assistantEnabled: Boolean = true,
    val languageTag: String = "ru-RU",
    val voiceName: String = ""
)

/**
 * Настройки голосового брифинга.
 *
 * Локальные настройки голоса. Текст и параметры не покидают устройство.
 */
class BriefingPreference(
    private val context: Context
) {

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("briefing_enabled")
        private val KEY_VOLUME = floatPreferencesKey("briefing_volume")
        private val KEY_RATE = floatPreferencesKey("briefing_rate")
        private val KEY_PITCH = floatPreferencesKey("briefing_pitch")
        private val KEY_BREVITY = intPreferencesKey("briefing_brevity")
        private val KEY_PERSONAL_DATA = booleanPreferencesKey("briefing_personal_data")
        private val KEY_HEADPHONES_ONLY = booleanPreferencesKey("briefing_headphones_only")
        private val KEY_MORNING = booleanPreferencesKey("briefing_morning")
        private val KEY_FOCUS = booleanPreferencesKey("briefing_focus")
        private val KEY_REMINDER = booleanPreferencesKey("briefing_reminder")
        private val KEY_ASSISTANT = booleanPreferencesKey("briefing_assistant")
        private val KEY_LANGUAGE_TAG = stringPreferencesKey("briefing_language_tag")
        private val KEY_VOICE_NAME = stringPreferencesKey("briefing_voice_name")
    }

    fun observeEnabled(): Flow<Boolean> =
        context.briefingDataStore.data.map { it[KEY_ENABLED] ?: true }

    suspend fun isEnabled(): Boolean = observeEnabled().first()

    fun observeVoiceSettings(): Flow<BriefingVoiceSettings> =
        context.briefingDataStore.data.map { prefs ->
            BriefingVoiceSettings(
                volumePercent = ((prefs[KEY_VOLUME] ?: 0.75f) * 100f).toInt().coerceIn(0, 100),
                ratePercent = ((prefs[KEY_RATE] ?: 1f) * 100f).toInt().coerceIn(50, 150),
                pitchPercent = ((prefs[KEY_PITCH] ?: 1f) * 100f).toInt().coerceIn(50, 150),
                brevityPercent = (prefs[KEY_BREVITY] ?: 60).coerceIn(0, 100),
                personalDataEnabled = prefs[KEY_PERSONAL_DATA] ?: true,
                headphonesOnly = prefs[KEY_HEADPHONES_ONLY] ?: false,
                morningEnabled = prefs[KEY_MORNING] ?: true,
                focusEnabled = prefs[KEY_FOCUS] ?: true,
                reminderEnabled = prefs[KEY_REMINDER] ?: true,
                assistantEnabled = prefs[KEY_ASSISTANT] ?: true,
                languageTag = prefs[KEY_LANGUAGE_TAG] ?: "ru-RU",
                voiceName = prefs[KEY_VOICE_NAME] ?: ""
            )
        }

    suspend fun getVoiceSettings(): BriefingVoiceSettings = observeVoiceSettings().first()

    suspend fun setEnabled(value: Boolean) {
        context.briefingDataStore.edit { it[KEY_ENABLED] = value }
    }

    suspend fun setVoiceSettings(settings: BriefingVoiceSettings) {
        context.briefingDataStore.edit {
            it[KEY_VOLUME] = settings.volumePercent.coerceIn(0, 100) / 100f
            it[KEY_RATE] = settings.ratePercent.coerceIn(50, 150) / 100f
            it[KEY_PITCH] = settings.pitchPercent.coerceIn(50, 150) / 100f
            it[KEY_BREVITY] = settings.brevityPercent.coerceIn(0, 100)
            it[KEY_PERSONAL_DATA] = settings.personalDataEnabled
            it[KEY_HEADPHONES_ONLY] = settings.headphonesOnly
            it[KEY_MORNING] = settings.morningEnabled
            it[KEY_FOCUS] = settings.focusEnabled
            it[KEY_REMINDER] = settings.reminderEnabled
            it[KEY_ASSISTANT] = settings.assistantEnabled
            it[KEY_LANGUAGE_TAG] = settings.languageTag
            it[KEY_VOICE_NAME] = settings.voiceName
        }
    }
}
