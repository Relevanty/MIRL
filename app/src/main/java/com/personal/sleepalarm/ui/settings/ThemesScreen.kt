package com.personal.sleepalarm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.ui.theme.AppAccentPalette
import com.personal.sleepalarm.ui.theme.ThemeCatalog
import com.personal.sleepalarm.ui.theme.ThemeCategory
import com.personal.sleepalarm.ui.theme.ThemePreset
import com.personal.sleepalarm.ui.theme.appAccents
import com.personal.sleepalarm.ui.theme.buildAppAccentPalette
import com.personal.sleepalarm.ui.theme.buildColorScheme

private const val DAY_TAB = 0
private const val NIGHT_TAB = 1

/** Searchable, categorised picker designed for hundreds of presets. */
@Composable
fun ThemesScreen(
    onBack: () -> Unit,
    viewModel: ThemesViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    val dayGridState = rememberLazyGridState()
    val nightGridState = rememberLazyGridState()
    var selectedTab by rememberSaveable {
        mutableIntStateOf(if (ThemeCatalog.byId(selectedId).isDark) NIGHT_TAB else DAY_TAB)
    }
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(selectedId) {
        selectedTab = if (ThemeCatalog.byId(selectedId).isDark) NIGHT_TAB else DAY_TAB
    }
    LaunchedEffect(selectedTab) {
        selectedCategoryName = null
    }

    val visiblePresets = if (selectedTab == DAY_TAB) viewModel.dayPresets else viewModel.nightPresets
    val availableCategories = remember(visiblePresets) {
        ThemeCategory.entries
            .filter { category -> visiblePresets.any { it.category == category } }
            .sortedBy(ThemeCategory::sortOrder)
    }
    val selectedCategory = selectedCategoryName?.let { saved ->
        availableCategories.firstOrNull { it.name == saved }
    }
    val filteredPresets = remember(visiblePresets, selectedCategory, query, resources) {
        val needle = query.trim()
        visiblePresets
            .asSequence()
            .filter { selectedCategory == null || it.category == selectedCategory }
            .filter { needle.isBlank() || it.localizedName(resources).contains(needle, ignoreCase = true) }
            .sortedBy { it.localizedName(resources).lowercase() }
            .toList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                text = {
                    Text(
                        stringResource(
                            R.string.themes_tab_with_count,
                            stringResource(R.string.themes_tab_day),
                            viewModel.dayPresets.size
                        )
                    )
                }
            )
            Tab(
                selected = selectedTab == NIGHT_TAB,
                onClick = { selectedTab = NIGHT_TAB },
                text = {
                    Text(
                        stringResource(
                            R.string.themes_tab_with_count,
                            stringResource(R.string.themes_tab_night),
                            viewModel.nightPresets.size
                        )
                    )
                }
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text(stringResource(R.string.themes_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.themes_clear_search)
                        )
                    }
                }
            } else null
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "all") {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategoryName = null },
                    label = {
                        Text(
                            stringResource(
                                R.string.themes_filter_with_count,
                                stringResource(R.string.themes_filter_all),
                                visiblePresets.size
                            )
                        )
                    }
                )
            }
            items(
                count = availableCategories.size,
                key = { availableCategories[it].name }
            ) { index ->
                val category = availableCategories[index]
                val count = visiblePresets.count { it.category == category }
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategoryName = category.name },
                    label = {
                        Text(
                            stringResource(
                                R.string.themes_filter_with_count,
                                stringResource(category.titleRes),
                                count
                            )
                        )
                    }
                )
            }
        }

        if (filteredPresets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.themes_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                state = if (selectedTab == DAY_TAB) dayGridState else nightGridState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedCategory == null && query.isBlank()) {
                    availableCategories.forEach { category ->
                        val categoryPresets = filteredPresets.filter { it.category == category }
                        item(
                            key = "header_${category.name}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            CategoryHeader(category = category, count = categoryPresets.size)
                        }
                        items(categoryPresets, key = ThemePreset::id) { preset ->
                            ThemeCard(
                                preset = preset,
                                isSelected = preset.id == selectedId,
                                onSelect = { viewModel.select(preset.id) }
                            )
                        }
                    }
                } else {
                    item(key = "filtered_header", span = { GridItemSpan(maxLineSpan) }) {
                        val title = selectedCategory?.let { stringResource(it.titleRes) }
                            ?: stringResource(R.string.themes_filter_all)
                        CategoryHeader(title = title, count = filteredPresets.size)
                    }
                    items(filteredPresets, key = ThemePreset::id) { preset ->
                        ThemeCard(
                            preset = preset,
                            isSelected = preset.id == selectedId,
                            onSelect = { viewModel.select(preset.id) }
                        )
                    }
                }

                item(key = "bottom_space", span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: ThemeCategory, count: Int) {
    CategoryHeader(title = stringResource(category.titleRes), count = count)
}

@Composable
private fun CategoryHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.appAccents.creative.color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Surface(
            color = MaterialTheme.appAccents.creative.action,
            contentColor = MaterialTheme.appAccents.creative.onAction,
            shape = CircleShape
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ThemeCard(
    preset: ThemePreset,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val resources = LocalContext.current.resources
    val shape = RoundedCornerShape(16.dp)
    val previewColors = remember(preset) { buildColorScheme(preset) }
    val previewAccents = remember(preset, previewColors) {
        buildAppAccentPalette(preset, previewColors)
    }
    val shellTone = MaterialTheme.appAccents.leisure
    val borderColor = if (isSelected) {
        previewColors.primary
    } else {
        shellTone.color.copy(alpha = 0.72f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 146.dp)
            .clip(shape)
            .background(shellTone.container)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onSelect)
    ) {
        CandidateThemePreview(
            colors = previewColors,
            accents = previewAccents,
            isSelected = isSelected
        )

        Text(
            text = preset.localizedName(resources),
            style = MaterialTheme.typography.bodyMedium,
            color = shellTone.onContainer,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun CandidateThemePreview(
    colors: ColorScheme,
    accents: AppAccentPalette,
    isSelected: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.background,
                        colors.surfaceContainerLow,
                        colors.primaryContainer
                    )
                )
            )
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.surfaceContainerHigh)
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(9.dp))
                        .padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.primary)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(colors.onSurface.copy(alpha = 0.82f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(colors.onSurfaceVariant.copy(alpha = 0.58f))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MiniThemeTile(
                            container = colors.primaryContainer,
                            accent = accents.focus.fill,
                            outline = colors.outlineVariant,
                            modifier = Modifier.weight(1f)
                        )
                        MiniThemeTile(
                            container = colors.secondaryContainer,
                            accent = accents.sleep.fill,
                            outline = colors.outlineVariant,
                            modifier = Modifier.weight(1f)
                        )
                        MiniThemeTile(
                            container = colors.tertiaryContainer,
                            accent = accents.study.fill,
                            outline = colors.outlineVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .width(30.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    accents.all.chunked(3).forEach { rowTones ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            rowTones.forEach { tone ->
                                PaletteDot(
                                    tone.fill,
                                    colors.outline,
                                    Modifier.size(7.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(15.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accents.chrome.navigation)
                    .padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                )
                PaletteDot(colors.secondary, colors.outline, Modifier.size(7.dp))
                PaletteDot(colors.tertiary, colors.outline, Modifier.size(7.dp))
                Spacer(modifier = Modifier.weight(1f))
                PaletteDot(accents.calm.fill, colors.outline, Modifier.size(8.dp))
            }
        }

        if (isSelected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(27.dp),
                shape = CircleShape,
                color = colors.primary
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.themes_selected),
                    tint = colors.onPrimary,
                    modifier = Modifier.padding(5.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniThemeTile(
    container: Color,
    accent: Color,
    outline: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(container)
            .border(1.dp, outline.copy(alpha = 0.62f), RoundedCornerShape(5.dp))
            .padding(4.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(3.dp)
                .clip(CircleShape)
                .background(accent)
        )
    }
}

@Composable
private fun PaletteDot(
    color: Color,
    borderColor: Color,
    modifier: Modifier = Modifier.size(11.dp)
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .border(1.dp, borderColor, CircleShape)
    )
}
