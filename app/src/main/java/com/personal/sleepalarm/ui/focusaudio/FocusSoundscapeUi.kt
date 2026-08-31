package com.personal.sleepalarm.ui.focusaudio

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.domain.focusaudio.FocusSoundCategory
import com.personal.sleepalarm.domain.focusaudio.CustomFocusSoundFile
import com.personal.sleepalarm.ui.theme.AppAccentTone
import com.personal.sleepalarm.ui.theme.ThemedModalBottomSheet
import kotlin.math.roundToInt

/** A presentation-only model so the picker is not coupled to storage or playback. */
@Immutable
data class FocusSoundUiItem(
    /** Stable, unique key for this concrete card. Custom files use their URI-backed history key. */
    val id: String,
    /** Stable catalogue source ID used by playback and persistence. */
    val catalogId: String = id,
    val customFile: CustomFocusSoundFile? = null,
    val title: String,
    val subtitle: String = "",
    val categoryId: String,
    /** A short, recognisable symbol such as “✎”, “☔” or “≋”. */
    val symbol: String = "≋",
    val isFavorite: Boolean = false,
    val isAvailable: Boolean = true,
    val isSilence: Boolean = false,
    val isCustomFile: Boolean = false
)

@Immutable
data class FocusSoundCategoryUi(
    val id: String,
    val title: String
)

enum class FocusSoundLayer { PRIMARY, SECONDARY }

@Immutable
data class FocusSoundscapeUiState(
    val items: List<FocusSoundUiItem>,
    val categories: List<FocusSoundCategoryUi>,
    val selectedCategoryId: String = CATEGORY_ALL,
    val recentItemIds: List<String> = emptyList(),
    val primaryItemId: String? = null,
    val primaryVolume: Float = 0.35f,
    val mixEnabled: Boolean = false,
    val canMix: Boolean = true,
    val secondaryItemId: String? = null,
    val secondaryVolume: Float = 0.20f,
    val editingLayer: FocusSoundLayer = FocusSoundLayer.PRIMARY,
    val isPlaying: Boolean = false,
    val animationsEnabled: Boolean = true,
    val isImportingCustomSounds: Boolean = false,
    val noticeMessage: String? = null,
    val playbackErrorMessage: String? = null
) {
    val primaryItem: FocusSoundUiItem?
        get() = items.firstOrNull { it.id == primaryItemId }

    val secondaryItem: FocusSoundUiItem?
        get() = items.firstOrNull { it.id == secondaryItemId }

    companion object {
        const val CATEGORY_ALL = "all"
        const val CATEGORY_RECENT = "recent"
        const val CATEGORY_FAVORITES = "favorites"
    }
}

/** Categories use the same stable IDs as domain.focusaudio.FocusSoundCategory. */
@Composable
fun rememberDefaultFocusSoundCategories(): List<FocusSoundCategoryUi> {
    val labels = listOf(
        FocusSoundscapeUiState.CATEGORY_ALL to stringResource(R.string.focus_sound_category_all),
        FocusSoundscapeUiState.CATEGORY_RECENT to stringResource(R.string.focus_sound_category_recent),
        FocusSoundscapeUiState.CATEGORY_FAVORITES to stringResource(R.string.focus_sound_category_favorites),
        "noise" to stringResource(R.string.focus_sound_category_noise),
        "study" to stringResource(R.string.focus_sound_category_study),
        "spaces" to stringResource(R.string.focus_sound_category_spaces),
        "weather" to stringResource(R.string.focus_sound_category_weather),
        "nature" to stringResource(R.string.focus_sound_category_nature),
        "cozy" to stringResource(R.string.focus_sound_category_cozy),
        "travel" to stringResource(R.string.focus_sound_category_travel),
        "melody" to stringResource(R.string.focus_sound_category_melody),
        "custom" to stringResource(R.string.focus_sound_category_custom)
    )
    return remember(labels) { labels.map { FocusSoundCategoryUi(it.first, it.second) } }
}

/** Compact entry point for the focus setup sheet. */
@Composable
fun FocusSoundscapeSetupRow(
    state: FocusSoundscapeUiState,
    onOpenPicker: () -> Unit,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val focusTone = MaterialTheme.appAccents.focus
    val primary = state.primaryItem
    val hasAudibleSound = primary != null && !primary.isSilence
    val summary = when {
        !hasAudibleSound -> stringResource(R.string.focus_sound_off)
        state.mixEnabled && state.secondaryItem != null -> stringResource(
            R.string.focus_sound_mix_summary,
            primary.title,
            state.secondaryItem!!.title
        )
        else -> stringResource(
            R.string.focus_sound_summary,
            primary.title,
            volumePercent(state.primaryVolume)
        )
    }

    Surface(
        onClick = onOpenPicker,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                stateDescription = summary
            },
        shape = RoundedCornerShape(20.dp),
        color = focusTone.action,
        contentColor = focusTone.onAction,
        border = BorderStroke(1.dp, focusTone.color.copy(alpha = 0.48f))
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            SoundArtwork(
                item = primary,
                isPlaying = state.isPlaying,
                animationsEnabled = state.animationsEnabled,
                modifier = Modifier.size(44.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.focus_soundscape_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = focusTone.onAction.copy(alpha = 0.76f)
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (hasAudibleSound) {
                IconButton(
                    onClick = onTogglePlayback,
                    enabled = enabled,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.focus_sound_pause
                            else R.string.focus_sound_play
                        ),
                        tint = focusTone.onAction
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = focusTone.onAction.copy(alpha = 0.76f),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

/**
 * Theme-aware sound picker. The mini player and categories stay visible while only
 * the two-column catalogue scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSoundscapePickerSheet(
    state: FocusSoundscapeUiState,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onSoundSelected: (soundId: String, layer: FocusSoundLayer) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onPrimaryVolumeChange: (Float) -> Unit,
    onSecondaryVolumeChange: (Float) -> Unit,
    onMixEnabledChange: (Boolean) -> Unit,
    onEditingLayerChange: (FocusSoundLayer) -> Unit,
    onAddCustomSound: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveCustomSound: (CustomFocusSoundFile) -> Unit = {},
    rememberForTask: Boolean = false,
    onRememberForTaskChange: (Boolean) -> Unit = {},
    showRememberForTask: Boolean = false,
    playDuringRecovery: Boolean = false,
    onPlayDuringRecoveryChange: (Boolean) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sessionOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    val focusTone = MaterialTheme.appAccents.focus
    val selectedCategory = state.selectedCategoryId
    val categoryItems = remember(state.items, state.recentItemIds, selectedCategory) {
        when (selectedCategory) {
            FocusSoundscapeUiState.CATEGORY_ALL -> state.items
            FocusSoundscapeUiState.CATEGORY_RECENT -> state.recentItemIds.mapNotNull { recentId ->
                state.items.firstOrNull { it.id == recentId }
            }
            FocusSoundscapeUiState.CATEGORY_FAVORITES -> state.items.filter { it.isFavorite }
            else -> state.items.filter { it.categoryId == selectedCategory }
        }
    }
    val visibleItems = remember(categoryItems, query) {
        val needle = query.trim()
        if (needle.isEmpty()) categoryItems else categoryItems.filter { item ->
            item.title.contains(needle, ignoreCase = true) ||
                item.subtitle.contains(needle, ignoreCase = true)
        }
    }
    val quickCategoryIds = setOf(
        FocusSoundscapeUiState.CATEGORY_ALL,
        FocusSoundscapeUiState.CATEGORY_RECENT,
        FocusSoundscapeUiState.CATEGORY_FAVORITES,
        "custom"
    )
    val quickCategories = state.categories.filter { it.id in quickCategoryIds }
    val catalogueCategories = state.categories.filterNot { it.id in quickCategoryIds }
    val isCatalogueSection = selectedCategory !in setOf(
        FocusSoundscapeUiState.CATEGORY_RECENT,
        FocusSoundscapeUiState.CATEGORY_FAVORITES,
        "custom"
    )
    val showImportAction = state.editingLayer == FocusSoundLayer.PRIMARY &&
        selectedCategory in setOf(FocusSoundscapeUiState.CATEGORY_ALL, "custom") &&
        query.isBlank()

    ThemedModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.focus_soundscape_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = focusTone.color
                        )
                        Text(
                            text = stringResource(R.string.focus_sound_library_count, state.items.count { !it.isSilence }),
                            style = MaterialTheme.typography.labelMedium,
                            color = focusTone.color.copy(alpha = 0.78f)
                        )
                    }
                    Text(
                        text = stringResource(R.string.focus_sound_offline_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = focusTone.onAction,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(focusTone.action)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.focus_sound_clear_search)
                                )
                            }
                        }
                    } else null,
                    placeholder = { Text(stringResource(R.string.focus_sound_search_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = focusTone.action.copy(alpha = 0.72f),
                        unfocusedContainerColor = focusTone.action.copy(alpha = 0.52f),
                        focusedTextColor = focusTone.onAction,
                        unfocusedTextColor = focusTone.onAction,
                        cursorColor = focusTone.color,
                        focusedBorderColor = focusTone.color,
                        unfocusedBorderColor = focusTone.color.copy(alpha = 0.42f),
                        focusedLeadingIconColor = focusTone.onAction,
                        unfocusedLeadingIconColor = focusTone.onAction.copy(alpha = 0.76f),
                        focusedTrailingIconColor = focusTone.onAction,
                        unfocusedTrailingIconColor = focusTone.onAction.copy(alpha = 0.76f),
                        focusedPlaceholderColor = focusTone.onAction.copy(alpha = 0.64f),
                        unfocusedPlaceholderColor = focusTone.onAction.copy(alpha = 0.64f)
                    )
                )

                if (state.isImportingCustomSounds) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            stringResource(R.string.focus_sound_importing),
                            style = MaterialTheme.typography.labelMedium,
                            color = focusTone.color
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = focusTone.color,
                            trackColor = focusTone.action
                        )
                    }
                }

                state.playbackErrorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                    SoundLibraryNotice(
                        text = error,
                        isError = true
                    )
                }
                state.noticeMessage?.takeIf { it.isNotBlank() }?.let { notice ->
                    SoundLibraryNotice(
                        text = notice,
                        isError = false
                    )
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickCategories, key = { it.id }) { category ->
                    val tone = focusSoundTone(category.id)
                    val selected = if (category.id == FocusSoundscapeUiState.CATEGORY_ALL) {
                        isCatalogueSection
                    } else {
                        category.id == selectedCategory
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onCategorySelected(category.id) },
                        label = {
                            Text(
                                if (category.id == FocusSoundscapeUiState.CATEGORY_ALL) {
                                    stringResource(R.string.focus_sound_section_catalogue)
                                } else {
                                    category.title
                                }
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = tone.action,
                            labelColor = tone.onAction,
                            iconColor = tone.onAction,
                            selectedContainerColor = tone.container,
                            selectedLabelColor = tone.onContainer,
                            selectedLeadingIconColor = tone.onContainer
                        )
                    )
                }
            }

            AnimatedVisibility(visible = isCatalogueSection) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(catalogueCategories, key = { it.id }) { category ->
                        val tone = focusSoundTone(category.id)
                        FilterChip(
                            selected = category.id == selectedCategory,
                            onClick = { onCategorySelected(category.id) },
                            label = { Text(category.title) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = tone.action,
                                labelColor = tone.onAction,
                                selectedContainerColor = tone.container,
                                selectedLabelColor = tone.onContainer
                            )
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 5.dp)
            ) {
                if (visibleItems.isEmpty() && !showImportAction) {
                    EmptyCategory(
                        isFavorites = selectedCategory == FocusSoundscapeUiState.CATEGORY_FAVORITES,
                        searchQuery = query,
                        tone = focusSoundTone(selectedCategory),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 18.dp,
                            end = 18.dp,
                            top = 5.dp,
                            bottom = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        if (showImportAction) {
                            item(key = "add_custom_sound") {
                                AddCustomSoundRow(
                                    hasImportedItems = state.items.any { it.isCustomFile },
                                    onClick = onAddCustomSound
                                )
                            }
                        }
                        items(visibleItems, key = { it.id }) { item ->
                            val selectedLayer = when (item.id) {
                                state.primaryItemId -> FocusSoundLayer.PRIMARY
                                state.secondaryItemId.takeIf { state.mixEnabled } -> FocusSoundLayer.SECONDARY
                                else -> null
                            }
                            FocusSoundLibraryRow(
                                item = item,
                                selectedLayer = selectedLayer,
                                isPlaying = state.isPlaying && selectedLayer != null,
                                animationsEnabled = state.animationsEnabled,
                                onClick = {
                                    onSoundSelected(
                                        item.id,
                                        if (state.mixEnabled) state.editingLayer else FocusSoundLayer.PRIMARY
                                    )
                                },
                                onToggleFavorite = { onToggleFavorite(item.id) },
                                onRemoveCustomSound = item.customFile?.let { file ->
                                    { onRemoveCustomSound(file) }
                                }
                            )
                        }
                    }
                }
            }

            Surface(
                color = focusTone.action,
                contentColor = focusTone.onAction,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(
                    1.dp,
                    focusTone.color.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 5.dp)
                ) {
                    FocusSoundMiniPlayer(
                        state = state,
                        onTogglePlayback = onTogglePlayback,
                        onPrimaryVolumeChange = onPrimaryVolumeChange,
                        onSecondaryVolumeChange = onSecondaryVolumeChange,
                        onMixEnabledChange = onMixEnabledChange,
                        onEditingLayerChange = onEditingLayerChange
                    )
                    TextButton(
                        onClick = { sessionOptionsExpanded = !sessionOptionsExpanded },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.textButtonColors(contentColor = focusTone.onAction)
                    ) {
                        Text(stringResource(R.string.focus_sound_session_options))
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (sessionOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    AnimatedVisibility(visible = sessionOptionsExpanded) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = focusTone.container,
                            contentColor = focusTone.onContainer
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp)) {
                                if (showRememberForTask) {
                                    SoundscapeOptionSwitch(
                                        title = stringResource(R.string.focus_sound_remember_task),
                                        subtitle = stringResource(R.string.focus_sound_remember_task_hint),
                                        checked = rememberForTask,
                                        onCheckedChange = onRememberForTaskChange
                                    )
                                }
                                SoundscapeOptionSwitch(
                                    title = stringResource(R.string.focus_sound_during_recovery),
                                    subtitle = stringResource(R.string.focus_sound_during_recovery_hint),
                                    checked = playDuringRecovery,
                                    onCheckedChange = onPlayDuringRecoveryChange
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundscapeOptionSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val focusTone = MaterialTheme.appAccents.focus
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = focusTone.onContainer.copy(alpha = 0.76f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = focusTone.onColor,
                checkedTrackColor = focusTone.color,
                uncheckedThumbColor = focusTone.onAction,
                uncheckedTrackColor = focusTone.action
            )
        )
    }
}

@Composable
private fun FocusSoundMiniPlayer(
    state: FocusSoundscapeUiState,
    onTogglePlayback: () -> Unit,
    onPrimaryVolumeChange: (Float) -> Unit,
    onSecondaryVolumeChange: (Float) -> Unit,
    onMixEnabledChange: (Boolean) -> Unit,
    onEditingLayerChange: (FocusSoundLayer) -> Unit
) {
    val primary = state.primaryItem
    val hasAudibleSound = primary != null && !primary.isSilence
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.appAccents.focus.container,
        contentColor = MaterialTheme.appAccents.focus.onContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SoundArtwork(
                    item = primary,
                    isPlaying = state.isPlaying,
                    animationsEnabled = state.animationsEnabled,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = primary?.title ?: stringResource(R.string.focus_sound_off),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (state.mixEnabled && state.secondaryItem != null) {
                            stringResource(R.string.focus_sound_two_layers)
                        } else {
                            primary?.subtitle?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.focus_sound_one_layer)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appAccents.focus.onContainer.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onTogglePlayback,
                    enabled = hasAudibleSound,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.focus_sound_pause
                            else R.string.focus_sound_play
                        )
                    )
                }
            }

            if (!state.mixEnabled) {
                CompactVolumeRow(
                    label = stringResource(R.string.focus_sound_volume),
                    volume = state.primaryVolume,
                    onVolumeChange = onPrimaryVolumeChange,
                    enabled = hasAudibleSound
                )
                TextButton(
                    onClick = { onMixEnabledChange(true) },
                    enabled = hasAudibleSound && state.canMix,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.appAccents.focus.onContainer
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.focus_sound_mix_action))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.focus_sound_mix_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onMixEnabledChange(false) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.appAccents.focus.onContainer
                        )
                    ) {
                        Text(stringResource(R.string.focus_sound_single_mode))
                    }
                }
                MixLayerControl(
                    layer = FocusSoundLayer.PRIMARY,
                    item = primary,
                    volume = state.primaryVolume,
                    selected = state.editingLayer == FocusSoundLayer.PRIMARY,
                    onSelect = { onEditingLayerChange(FocusSoundLayer.PRIMARY) },
                    onVolumeChange = onPrimaryVolumeChange
                )
                MixLayerControl(
                    layer = FocusSoundLayer.SECONDARY,
                    item = state.secondaryItem,
                    volume = state.secondaryVolume,
                    selected = state.editingLayer == FocusSoundLayer.SECONDARY,
                    onSelect = { onEditingLayerChange(FocusSoundLayer.SECONDARY) },
                    onVolumeChange = onSecondaryVolumeChange
                )
            }
        }
    }
}

@Composable
private fun CompactVolumeRow(
    label: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (enabled && volume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(68.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Slider(
            value = volume.coerceIn(0f, 1f),
            onValueChange = onVolumeChange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.appAccents.focus.color,
                activeTrackColor = MaterialTheme.appAccents.focus.color,
                inactiveTrackColor = MaterialTheme.appAccents.focus.action
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.focus_sound_volume_percent, volumePercent(volume)),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(38.dp)
        )
    }
}

@Composable
private fun MixLayerControl(
    layer: FocusSoundLayer,
    item: FocusSoundUiItem?,
    volume: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val focusTone = MaterialTheme.appAccents.focus
    val layerNumber = if (layer == FocusSoundLayer.PRIMARY) "1" else "2"
    val label = item?.title ?: stringResource(R.string.focus_sound_add_second_layer)
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            focusTone.action
        } else {
            focusTone.container
        },
        contentColor = if (selected) focusTone.onAction else focusTone.onContainer,
        border = if (selected) BorderStroke(1.dp, focusTone.color) else null,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) focusTone.color else focusTone.action,
                    contentColor = if (selected) focusTone.onColor else focusTone.onAction,
                    modifier = Modifier.size(25.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(layerNumber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.focus_sound_volume_percent, volumePercent(volume)),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Slider(
                value = volume.coerceIn(0f, 1f),
                onValueChange = onVolumeChange,
                enabled = item != null,
                colors = SliderDefaults.colors(
                    thumbColor = focusTone.color,
                    activeTrackColor = focusTone.color,
                    inactiveTrackColor = focusTone.action
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            )
        }
    }
}

@Composable
private fun SoundLibraryNotice(text: String, isError: Boolean) {
    val container = if (isError) {
        MaterialTheme.appAccents.warning.container
    } else {
        MaterialTheme.appAccents.info.container
    }
    val content = if (isError) {
        MaterialTheme.appAccents.warning.onContainer
    } else {
        MaterialTheme.appAccents.info.onContainer
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container.copy(alpha = 0.72f),
        contentColor = content,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FocusSoundLibraryRow(
    item: FocusSoundUiItem,
    selectedLayer: FocusSoundLayer?,
    isPlaying: Boolean,
    animationsEnabled: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveCustomSound: (() -> Unit)?
) {
    val selected = selectedLayer != null
    val tone = focusSoundTone(item.categoryId)
    val rowContentColor = if (selected) tone.onContainer else tone.onAction
    val selectedDescription = when (selectedLayer) {
        FocusSoundLayer.PRIMARY -> stringResource(R.string.focus_sound_primary_selected)
        FocusSoundLayer.SECONDARY -> stringResource(R.string.focus_sound_secondary_selected)
        null -> ""
    }
    Surface(
        onClick = onClick,
        enabled = item.isAvailable,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            tone.container
        } else {
            tone.action
        },
        contentColor = rowContentColor,
        border = if (selected) {
            BorderStroke(1.25.dp, tone.color.copy(alpha = 0.86f))
        } else {
            BorderStroke(1.dp, tone.color.copy(alpha = 0.34f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 70.dp)
            .semantics {
                this.selected = selected
                if (selected) stateDescription = selectedDescription
            }
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SoundArtwork(
                item = item,
                isPlaying = isPlaying,
                animationsEnabled = animationsEnabled,
                modifier = Modifier.size(46.dp)
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = rowContentColor.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selectedLayer != null) {
                Surface(
                    shape = CircleShape,
                    color = tone.color,
                    contentColor = tone.onColor,
                    modifier = Modifier.size(27.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedLayer == FocusSoundLayer.PRIMARY) "1" else "2",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (!item.isSilence) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(
                            if (item.isFavorite) R.string.focus_sound_remove_favorite
                            else R.string.focus_sound_add_favorite
                        ),
                        tint = if (item.isFavorite) MaterialTheme.appAccents.warning.color
                        else rowContentColor.copy(alpha = 0.74f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            if (onRemoveCustomSound != null) {
                IconButton(
                    onClick = onRemoveCustomSound,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.focus_sound_remove_custom),
                        tint = MaterialTheme.appAccents.urgent.color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCustomSoundRow(hasImportedItems: Boolean, onClick: () -> Unit) {
    val focusTone = MaterialTheme.appAccents.focus
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 70.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, focusTone.color.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = focusTone.action,
            contentColor = focusTone.onAction
        )
    ) {
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = focusTone.container,
            contentColor = focusTone.onContainer,
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.focus_sound_add_files),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(
                    if (hasImportedItems) R.string.focus_sound_add_more_files_hint
                    else R.string.focus_sound_add_files_hint
                ),
                style = MaterialTheme.typography.labelSmall,
                color = focusTone.onAction.copy(alpha = 0.76f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun EmptyCategory(
    isFavorites: Boolean,
    searchQuery: String = "",
    tone: AppAccentTone,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (isFavorites) Icons.Default.FavoriteBorder else Icons.Default.VolumeOff,
            contentDescription = null,
            tint = tone.color,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(
                when {
                    searchQuery.isNotBlank() -> R.string.focus_sound_search_empty
                    isFavorites -> R.string.focus_sound_favorites_empty
                    else -> R.string.focus_sound_category_empty
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = tone.color
        )
    }
}

@Composable
private fun SoundArtwork(
    item: FocusSoundUiItem?,
    isPlaying: Boolean,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = focusSoundArtworkAccent(item?.categoryId)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.fill.copy(alpha = 0.30f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    )
                )
            )
            .border(1.dp, accent.foreground.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item?.symbol ?: "≋",
            style = MaterialTheme.typography.titleMedium,
            color = accent.foreground
        )
        if (isPlaying) {
            SoundWave(
                color = accent.foreground,
                animated = animationsEnabled,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(11.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

private data class SoundArtworkAccent(
    /** Unclamped theme identity used only as a translucent decorative fill. */
    val fill: Color,
    /** Contrast-safe counterpart used for symbols, borders and waveform strokes. */
    val foreground: Color
)

private fun AppAccentTone.toSoundArtworkAccent() = SoundArtworkAccent(
    fill = fill,
    foreground = color
)

/** Keeps every sound category expressive while separating artwork fill from foreground ink. */
@Composable
private fun focusSoundArtworkAccent(categoryId: String?): SoundArtworkAccent {
    return focusSoundTone(categoryId).toSoundArtworkAccent()
}

@Composable
private fun focusSoundTone(categoryId: String?): AppAccentTone {
    val accents = MaterialTheme.appAccents
    return when (categoryId) {
        FocusSoundscapeUiState.CATEGORY_ALL -> accents.focus
        FocusSoundscapeUiState.CATEGORY_RECENT -> accents.schedule
        FocusSoundscapeUiState.CATEGORY_FAVORITES -> accents.warning
        FocusSoundCategory.SILENCE.id -> accents.calm
        FocusSoundCategory.NOISE.id -> accents.other
        FocusSoundCategory.STUDY.id -> accents.study
        FocusSoundCategory.SPACES.id -> accents.work
        FocusSoundCategory.WEATHER.id -> accents.info
        FocusSoundCategory.NATURE.id -> accents.leisure
        FocusSoundCategory.COZY.id -> accents.calm
        FocusSoundCategory.TRAVEL.id -> accents.schedule
        FocusSoundCategory.MELODY.id -> accents.creative
        FocusSoundCategory.CUSTOM.id -> accents.creative
        else -> accents.focus
    }
}

@Composable
private fun SoundWave(
    color: Color,
    animated: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "focusSoundWave")
    val first by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "focusSoundWaveFirst"
    )
    val second by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(780), RepeatMode.Reverse),
        label = "focusSoundWaveSecond"
    )
    Canvas(modifier) {
        val values = if (animated) listOf(first, second, (first + second) / 2f, 1.1f - first / 2f)
        else listOf(0.45f, 0.8f, 0.55f, 0.7f)
        val gap = size.width / (values.size * 2f)
        values.forEachIndexed { index, value ->
            val x = gap * (index * 2f + 1f)
            val height = size.height * value.coerceIn(0.18f, 1f)
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, size.height),
                end = androidx.compose.ui.geometry.Offset(x, size.height - height),
                strokeWidth = gap.coerceAtMost(3.dp.toPx()),
                cap = StrokeCap.Round
            )
        }
    }
}

/** Small overlay for an active focus session; it never participates in parent layout size. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveFocusSoundButton(
    state: FocusSoundscapeUiState,
    onOpenPicker: () -> Unit,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusTone = MaterialTheme.appAccents.focus
    val title = state.primaryItem?.title ?: stringResource(R.string.focus_sound_off)
    val action = stringResource(
        if (state.isPlaying) R.string.focus_sound_long_press_pause
        else R.string.focus_sound_long_press_play
    )
    val buttonDescription = stringResource(R.string.focus_sound_active_button, title)
    Surface(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onOpenPicker,
                onLongClick = onTogglePlayback,
                onLongClickLabel = action,
                role = Role.Button
            )
            .semantics {
                contentDescription = buttonDescription
                stateDescription = action
        },
        shape = CircleShape,
        color = focusTone.action,
        contentColor = focusTone.onAction,
        tonalElevation = 5.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, focusTone.color.copy(alpha = 0.72f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (state.isPlaying) {
                SoundWave(
                    color = focusTone.onAction,
                    animated = state.animationsEnabled,
                    modifier = Modifier.size(width = 25.dp, height = 22.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(focusTone.color)
                )
            } else {
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun volumePercent(volume: Float): Int =
    (volume.coerceIn(0f, 1f) * 100f).roundToInt()
