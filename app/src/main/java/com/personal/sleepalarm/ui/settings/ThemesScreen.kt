package com.personal.sleepalarm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.ui.theme.ThemeCatalog
import com.personal.sleepalarm.ui.theme.ThemeCategory
import com.personal.sleepalarm.ui.theme.ThemePreset

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
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
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
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 122.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onSelect)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .background(Color(preset.background))
                .padding(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.66f)
                    .height(34.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(preset.surface))
            )
            Row(
                modifier = Modifier.align(Alignment.BottomStart),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                PaletteDot(Color(preset.primary))
                PaletteDot(Color(preset.secondary))
            }
            if (isSelected) {
                val selectedBackground = Color(preset.primary)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(27.dp),
                    shape = CircleShape,
                    color = selectedBackground
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.themes_selected),
                        tint = if (selectedBackground.luminance() > 0.35f) Color.Black else Color.White,
                        modifier = Modifier.padding(5.dp)
                    )
                }
            }
        }

        Text(
            text = preset.localizedName(resources),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun PaletteDot(color: Color) {
    Box(
        modifier = Modifier
            .size(13.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
    )
}
