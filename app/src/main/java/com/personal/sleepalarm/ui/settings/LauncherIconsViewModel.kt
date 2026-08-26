package com.personal.sleepalarm.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.launcher.LauncherIconCatalog
import com.personal.sleepalarm.launcher.LauncherIconManager
import com.personal.sleepalarm.launcher.LauncherIconSpec
import com.personal.sleepalarm.ui.theme.ThemeCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherIconsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val iconPreference = app.serviceLocator.launcherIconPreference
    private val themePreference = app.serviceLocator.themePreference
    private val iconManager = LauncherIconManager(application)

    val icons: List<LauncherIconSpec> = LauncherIconCatalog.all

    val autoMatch: StateFlow<Boolean> = iconPreference.observeAutoMatch()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val selectedId: StateFlow<String> = combine(
        iconPreference.observeSelectedId(),
        iconPreference.observeAutoMatch(),
        themePreference.observeThemeId()
    ) { selectedId, auto, themeId ->
        if (auto) {
            LauncherIconCatalog.forTheme(themeId, ThemeCatalog.byId(themeId).isDark).id
        } else {
            selectedId
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LauncherIconCatalog.DEFAULT_ID
    )

    fun select(iconId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                iconPreference.selectManually(iconId)
                iconManager.activate(iconId)
            }
        }
    }

    fun setAutoMatch(enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                iconPreference.setAutoMatch(enabled)
            }
        }
    }
}
