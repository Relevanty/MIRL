package com.personal.sleepalarm.ui.library

import com.personal.sleepalarm.ui.theme.appAccents

import com.personal.sleepalarm.ui.theme.ThemedAlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.LibraryItemEntity
import com.personal.sleepalarm.data.db.entity.LibraryItemType
import com.personal.sleepalarm.data.db.entity.LibraryTagEntity
import com.personal.sleepalarm.ui.components.CatText
import com.personal.sleepalarm.util.CoverHelper
import com.personal.sleepalarm.util.ResourceFileHelper
import kotlinx.coroutines.flow.Flow

@Composable
fun LibraryScreen(
    onBack: (() -> Unit)? = null,
    viewModel: LibraryViewModel = viewModel(),
    openItemId: Int? = null,
    onOpenItemConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var itemToDelete by remember { mutableStateOf<LibraryItemEntity?>(null) }
    var actionItem by remember { mutableStateOf<LibraryItemEntity?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Int?>(null) }
    var showGraph by remember { mutableStateOf(false) }

    LaunchedEffect(openItemId) {
        openItemId?.let { itemId ->
            editTarget = itemId
            showEdit = true
            onOpenItemConsumed()
        }
    }

    if (showGraph) {
        LibraryGraphScreen(
            onBack = { showGraph = false },
            onOpenItem = { id ->
                showGraph = false
                editTarget = id
                showEdit = true
            }
        )
        return
    }

    if (showEdit) {
        key(editTarget ?: -1) {
            LibraryEditScreen(
                editItemId = editTarget,
                onBack = { showEdit = false }
            )
        }
        return
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editTarget = null
                    showEdit = true
                },
                containerColor = MaterialTheme.appAccents.study.color,
                contentColor = MaterialTheme.appAccents.study.onColor
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // === Заголовок с котом ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showGraph = true }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.library_graph)
                    )
                }
                CatText(
                    text = "=^..^=",
                    color = MaterialTheme.appAccents.study.color,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // === Поиск в карточке ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                TextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.library_search)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // === Фильтры ===
            FilterTypeRow(
                selected = state.filterType,
                onSelect = viewModel::setFilter
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (state.items.isEmpty()) {
                // === Красивый пустой state со спящим котом ===
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = " /\\_/\\\n( -.- ) zZ\n > ^ <",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 40.sp,
                            lineHeight = 46.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.appAccents.other.color
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.library_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { _, item ->
                        LibraryCard(
                            item = item,
                            tagsFlow = viewModel.tagsForItem(item.id),
                            linkedTaskLabels = state.linkedTaskLabels[item.id].orEmpty(),
                            onClick = {
                                editTarget = item.id
                                showEdit = true
                            },
                            onLongClick = { actionItem = item }
                        )
                    }
                }
            }
        }
    }

    actionItem?.let { item ->
        val index = state.items.indexOfFirst { it.id == item.id }
        LibraryActionsDialog(
            item = item,
            canMoveUp = index > 0,
            canMoveDown = index >= 0 && index < state.items.lastIndex,
            onDismiss = { actionItem = null },
            onEdit = {
                editTarget = item.id
                showEdit = true
                actionItem = null
            },
            onOpen = {
                ResourceFileHelper.open(context, item.localFilePath, item.referenceUrl)
                actionItem = null
            },
            onMoveUp = {
                if (index > 0) viewModel.swapItems(item, state.items[index - 1])
                actionItem = null
            },
            onMoveDown = {
                if (index >= 0 && index < state.items.lastIndex) {
                    viewModel.swapItems(item, state.items[index + 1])
                }
                actionItem = null
            },
            onDelete = {
                actionItem = null
                itemToDelete = item
            }
        )
    }

    itemToDelete?.let { item ->
        ThemedAlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = {
                Text(stringResource(R.string.library_delete_item_text, item.title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(item)
                        itemToDelete = null
                    }
                ) {
                    Text(
                        stringResource(R.string.library_delete),
                        color = MaterialTheme.appAccents.urgent.color
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun FilterTypeRow(
    selected: LibraryItemType?,
    onSelect: (LibraryItemType?) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.library_filter_all)) }
        )
        FilterChip(
            selected = selected == LibraryItemType.BOOK,
            onClick = { onSelect(LibraryItemType.BOOK) },
            label = { Text(stringResource(R.string.library_filter_books)) }
        )
        FilterChip(
            selected = selected == LibraryItemType.MOVIE,
            onClick = { onSelect(LibraryItemType.MOVIE) },
            label = { Text(stringResource(R.string.library_filter_movies)) }
        )
        FilterChip(
            selected = selected == LibraryItemType.MUSIC,
            onClick = { onSelect(LibraryItemType.MUSIC) },
            label = { Text(stringResource(R.string.library_filter_music)) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    item: LibraryItemEntity,
    tagsFlow: Flow<List<LibraryTagEntity>>,
    linkedTaskLabels: List<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val tags by tagsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val bitmap = remember(item.coverUri) { CoverHelper.loadBitmap(item.coverUri) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "☰", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (item.author.isNotBlank()) {
                Text(
                    text = item.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (tags.isNotEmpty()) {
                Text(
                    text = tags.joinToString(" · ") { "#${it.name}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appAccents.study.color
                )
            }
            if (linkedTaskLabels.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.library_linked_tasks,
                        linkedTaskLabels.take(2).joinToString(" · ")
                    ),
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appAccents.calm.color
                )
            }
            val resourceLabel = when {
                item.localFilePath != null -> item.originalFileName.ifBlank { "Локальный файл" }
                item.referenceUrl.isNotBlank() -> "Ссылка · ${item.referenceUrl}"
                else -> null
            }
            if (resourceLabel != null) {
                Text(
                    text = resourceLabel,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appAccents.other.color
                )
            }
        }

        if (item.rating > 0) {
            Text(
                text = "★".repeat(item.rating),
                color = MaterialTheme.appAccents.warning.color,
                style = MaterialTheme.typography.bodySmall
            )
        }

    }
}

@Composable
private fun LibraryActionsDialog(
    item: LibraryItemEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    ThemedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column {
                if (item.localFilePath != null || item.referenceUrl.isNotBlank()) {
                    LibraryActionRow(Icons.Default.OpenInNew, "Открыть материал", true, onOpen)
                }
                LibraryActionRow(Icons.Default.Edit, stringResource(R.string.library_action_edit), true, onEdit)
                LibraryActionRow(Icons.Default.ArrowUpward, stringResource(R.string.library_action_move_up), canMoveUp, onMoveUp)
                LibraryActionRow(Icons.Default.ArrowDownward, stringResource(R.string.library_action_move_down), canMoveDown, onMoveDown)
                LibraryActionRow(
                    Icons.Default.Delete,
                    stringResource(R.string.library_delete),
                    true,
                    onDelete,
                    MaterialTheme.appAccents.urgent.color
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun LibraryActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentColor = color.copy(alpha = if (enabled) 1f else 0.35f)
        Icon(icon, contentDescription = null, tint = contentColor)
        Spacer(Modifier.width(12.dp))
        Text(label, color = contentColor, style = MaterialTheme.typography.bodyLarge)
    }
}
