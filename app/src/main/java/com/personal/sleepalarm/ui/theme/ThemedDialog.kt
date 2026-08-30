package com.personal.sleepalarm.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.sleepalarm.app.App
/**
 * Оборачивает AlertDialog в актуальную тему, читая themeId из DataStore.
 * Это решает проблему, когда AlertDialog рендерится в системном окне
 * и не наследует MaterialTheme из Compose-дерева.
 */
@Composable
fun ThemedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val themeId by (context.applicationContext as App)
        .serviceLocator.themePreference
        .observeThemeId()
        .collectAsStateWithLifecycle(initialValue = ThemeCatalog.DEFAULT_ID)

    ThemedOverlayContent(themeId = themeId) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text
        )
    }
}

/**
 * Оборачивает ModalBottomSheet в актуальную тему.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    dragHandle: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val themeId by (context.applicationContext as App)
        .serviceLocator.themePreference
        .observeThemeId()
        .collectAsStateWithLifecycle(initialValue = ThemeCatalog.DEFAULT_ID)

    ThemedOverlayContent(themeId = themeId) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            dragHandle = dragHandle ?: {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    content()
                }
            }
        )
    }
}
/**
 * Provides the same visual tokens as [SleepAlarmTheme] without touching the
 * activity's system bars. Dialogs and sheets live in their own window, so the
 * complete set of locals has to cross that boundary explicitly.
 */
@Composable
private fun ThemedOverlayContent(
    themeId: String,
    content: @Composable () -> Unit
) {
    val preset = remember(themeId) { ThemeCatalog.byId(themeId) }
    val colorScheme = remember(preset) { buildColorScheme(preset) }
    val appAccents = remember(preset, colorScheme) {
        buildAppAccentPalette(preset, colorScheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SleepAlarmTypography
    ) {
        CompositionLocalProvider(
            LocalAppAccentPalette provides appAccents,
            LocalContentColor provides colorScheme.onBackground
        ) {
            content()
        }
    }
}
