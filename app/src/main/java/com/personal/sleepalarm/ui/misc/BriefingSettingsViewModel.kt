package com.personal.sleepalarm.ui.misc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.data.preferences.BriefingPreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BriefingSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val preference: BriefingPreference =
        (application as App).serviceLocator.briefingPreference

    val enabled: StateFlow<Boolean> = preference.observeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { preference.setEnabled(value) }
    }
}