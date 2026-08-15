package com.personal.sleepalarm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.ui.theme.ThemeCatalog
import com.personal.sleepalarm.ui.theme.ThemePreset
import androidx.compose.foundation.clickable

private const val DAY_TAB = 0
private const val NIGHT_TAB = 1

/**
 * Меню выбора готовых тем (вместо «Внешний вид»).
 */
@Composable
fun ThemesScreen(
    onBack: () -> Unit,
    viewModel: ThemesViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val dayListState = rememberLazyListState()
    val nightListState = rememberLazyListState()
    var selectedTab by rememberSaveable {
        mutableIntStateOf(
            if (ThemeCatalog.byId(selectedId).isDark) NIGHT_TAB else DAY_TAB
        )
    }

    LaunchedEffect(selectedId) {
        selectedTab = if (ThemeCatalog.byId(selectedId).isDark) NIGHT_TAB else DAY_TAB
    }

    val visiblePresets = remember(selectedTab) {
        if (selectedTab == DAY_TAB) viewModel.dayPresets else viewModel.nightPresets
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
            Text(
                text = stringResource(R.string.themes_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == DAY_TAB,
                onClick = { selectedTab = DAY_TAB },
                text = { Text(stringResource(R.string.themes_tab_day)) }
            )
            Tab(
                selected = selectedTab == NIGHT_TAB,
                onClick = { selectedTab = NIGHT_TAB },
                text = { Text(stringResource(R.string.themes_tab_night)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            state = if (selectedTab == DAY_TAB) dayListState else nightListState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(visiblePresets, key = { it.id }) { preset ->
                ThemeRow(
                    preset = preset,
                    isSelected = preset.id == selectedId,
                    onSelect = { viewModel.select(preset.id) }
                )
            }
        }
    }
}

@Composable
private fun ThemeRow(
    preset: ThemePreset,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                } else Modifier
            )
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Превью цветов.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ColorDot(Color(preset.background))
            ColorDot(Color(preset.primary))
            ColorDot(Color(preset.secondary))
        }

        Spacer(modifier = Modifier.size(12.dp))

        Text(
            text = stringResource(preset.nameRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ColorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}
