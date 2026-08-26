package com.personal.sleepalarm.ui.misc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.data.preferences.BriefingPreference
import com.personal.sleepalarm.data.preferences.BriefingVoiceSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

data class OfflineVoiceOption(
    val name: String,
    val languageTag: String,
    val languageLabel: String,
    val voiceLabel: String
)

class BriefingSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val preference: BriefingPreference =
        (application as App).serviceLocator.briefingPreference
    private val coordinator = (application as App).serviceLocator.briefingCoordinator
    private var catalogTts: TextToSpeech? = null
    private val _offlineVoices = MutableStateFlow<List<OfflineVoiceOption>>(emptyList())
    val offlineVoices: StateFlow<List<OfflineVoiceOption>> = _offlineVoices

    init {
        catalogTts = TextToSpeech(application.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _offlineVoices.value = catalogTts?.voices.orEmpty()
                    .filterNot { it.isNetworkConnectionRequired }
                    .map { voice ->
                        OfflineVoiceOption(
                            name = voice.name,
                            languageTag = voice.locale.toLanguageTag(),
                            languageLabel = voice.locale.getDisplayLanguage(Locale("ru"))
                                .replaceFirstChar { it.uppercase() },
                            voiceLabel = voice.name.substringAfterLast('.').replace('_', ' ')
                        )
                    }
                    .distinctBy { it.name }
                    .sortedWith(compareBy(OfflineVoiceOption::languageLabel, OfflineVoiceOption::voiceLabel))
            }
        }
    }

    val enabled: StateFlow<Boolean> = preference.observeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val voiceSettings: StateFlow<BriefingVoiceSettings> = preference.observeVoiceSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BriefingVoiceSettings())

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { preference.setEnabled(value) }
    }

    fun setVolume(value: Int) = update { it.copy(volumePercent = value) }
    fun setRate(value: Int) = update { it.copy(ratePercent = value) }
    fun setPitch(value: Int) = update { it.copy(pitchPercent = value) }
    fun setBrevity(value: Int) = update { it.copy(brevityPercent = value) }
    fun setPersonalData(value: Boolean) = update { it.copy(personalDataEnabled = value) }
    fun setHeadphonesOnly(value: Boolean) = update { it.copy(headphonesOnly = value) }
    fun setMorning(value: Boolean) = update { it.copy(morningEnabled = value) }
    fun setFocus(value: Boolean) = update { it.copy(focusEnabled = value) }
    fun setReminder(value: Boolean) = update { it.copy(reminderEnabled = value) }
    fun setAssistant(value: Boolean) = update { it.copy(assistantEnabled = value) }
    fun setLanguage(tag: String) = update { current ->
        current.copy(languageTag = tag, voiceName = "")
    }
    fun setVoice(name: String, tag: String) = update { it.copy(voiceName = name, languageTag = tag) }

    fun preview() {
        viewModelScope.launch {
            coordinator.speak("Привет. Так будет звучать голос MIRL.") {}
        }
    }

    private fun update(transform: (BriefingVoiceSettings) -> BriefingVoiceSettings) {
        viewModelScope.launch {
            preference.setVoiceSettings(transform(voiceSettings.value))
        }
    }

    override fun onCleared() {
        catalogTts?.shutdown()
        catalogTts = null
        super.onCleared()
    }
}
