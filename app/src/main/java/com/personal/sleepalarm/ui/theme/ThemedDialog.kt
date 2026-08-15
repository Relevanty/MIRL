package com.personal.sleepalarm.ui.theme

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.sleepalarm.app.App
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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

    MaterialTheme(
        colorScheme = buildColorSchemeForId(themeId)
    ) {
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

    MaterialTheme(
        colorScheme = buildColorSchemeForId(themeId)
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            dragHandle = dragHandle,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
            content = content
        )
    }
}
/**
 * Строит ColorScheme по themeId — вынесено из Theme.kt.
 */
private fun buildColorSchemeForId(themeId: String) =
    buildColorScheme(ThemeCatalog.byId(themeId))