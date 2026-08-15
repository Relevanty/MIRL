package com.personal.sleepalarm.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.ui.theme.ThemeCatalog
import com.personal.sleepalarm.ui.theme.ThemePreset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel выбора темы.
 */
class ThemesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val themePreference =
        (application as App).serviceLocator.themePreference

    val selectedId: StateFlow<String> = themePreference.observeThemeId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeCatalog.DEFAULT_ID)

    val dayPresets: List<ThemePreset> = ThemeCatalog.day
    val nightPresets: List<ThemePreset> = ThemeCatalog.night

    fun select(themeId: String) {
        viewModelScope.launch { themePreference.setThemeId(themeId) }
    }
}
