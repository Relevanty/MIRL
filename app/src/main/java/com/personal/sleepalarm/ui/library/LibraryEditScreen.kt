package com.personal.sleepalarm.ui.library

import com.personal.sleepalarm.ui.theme.appAccents

import com.personal.sleepalarm.ui.theme.ThemedAlertDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.LibraryItemType
import com.personal.sleepalarm.data.db.entity.LibraryResourceKind
import com.personal.sleepalarm.ui.components.CatText
import com.personal.sleepalarm.util.CoverHelper

@Composable
fun LibraryEditScreen(
    editItemId: Int?,
    onBack: () -> Unit,
    viewModel: LibraryEditViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(editItemId) {
        if (editItemId == null) {
            viewModel.resetForCreate()
        } else {
            viewModel.load(editItemId)
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.pickCover(uri)
    }

    val resourcePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.pickResource(uri)
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    val discardAndBack = {
        viewModel.discardChanges()
        onBack()
    }
    BackHandler(onBack = discardAndBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // === Заголовок с котом ===
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = discardAndBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.library_edit_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            CatText(
                text = "=^..^=",
            color = MaterialTheme.appAccents.study.color,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === Секция 1: Основное ===
        Text(
            text = stringResource(R.string.library_section_main),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.appAccents.study.color,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryItemType.values().forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            label = { Text(typeLabel(type)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.library_field_title)) },
                    placeholder = { Text(stringResource(R.string.library_title_placeholder)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.appAccents.study.color
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = state.author,
                    onValueChange = viewModel::setAuthor,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.library_field_author)) },
                    placeholder = { Text(stringResource(R.string.library_author_placeholder)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.appAccents.study.color
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Рабочий материал",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.appAccents.study.color,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryResourceKind.values().forEach { kind ->
                    FilterChip(
                        selected = state.resourceKind == kind,
                        onClick = { viewModel.setResourceKind(kind) },
                        label = {
                            Text(
                                when (kind) {
                                    LibraryResourceKind.NOTE -> "Заметка"
                                    LibraryResourceKind.DOCUMENT -> "Файл"
                                    LibraryResourceKind.LINK -> "Ссылка"
                                }
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.resourceKind == LibraryResourceKind.DOCUMENT || state.localFilePath != null) {
                OutlinedButton(onClick = { resourcePicker.launch(arrayOf("*/*")) }) {
                    Text(if (state.originalFileName.isBlank()) "Выбрать файл" else state.originalFileName)
                }
                if (state.localFilePath != null) {
                    TextButton(onClick = viewModel::removeResource) { Text("Убрать файл") }
                }
            }
            if (state.resourceKind == LibraryResourceKind.LINK) {
                TextField(
                    value = state.referenceUrl,
                    onValueChange = viewModel::setReferenceUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ссылка") },
                    placeholder = { Text("https://…") },
                    singleLine = true
                )
            }
            Text(
                text = "Файлы копируются в MIRL и доступны без интернета. Ресурс можно прикрепить к задаче.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === Секция 2: Обложка ===
        Text(
            text = stringResource(R.string.library_section_cover),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.appAccents.other.color,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val bitmap = remember(state.coverPath) { CoverHelper.loadBitmap(state.coverPath) }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                Column {
                    OutlinedButton(onClick = { coverPicker.launch(arrayOf("image/*")) }) {
                        Text(stringResource(R.string.library_pick_cover))
                    }
                    if (state.coverPath != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = { viewModel.removeCover() }) {
                            Text(stringResource(R.string.library_remove_cover))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === Секция 3: Заметки ===
        Text(
            text = stringResource(R.string.library_section_notes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.appAccents.calm.color,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            Column {
                TextField(
                    value = state.shortDescription,
                    onValueChange = viewModel::setShortDescription,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.library_field_desc)) },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.appAccents.study.color
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = state.impression,
                    onValueChange = viewModel::setImpression,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.library_field_impression)) },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.appAccents.study.color
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = state.thoughts,
                    onValueChange = viewModel::setThoughts,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.library_field_thoughts)) },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.appAccents.study.color
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === Секция 4: Оценка и теги ===
        Text(
            text = stringResource(R.string.library_section_rating_tags),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.appAccents.study.color,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            Column {
                // Рейтинг
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.library_field_rating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    repeat(5) { index ->
                        IconButton(onClick = { viewModel.setRating(index + 1) }) {
                            Text(
                                text = if (index < state.rating) "★" else "☆",
                color = MaterialTheme.appAccents.study.color,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Теги
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = state.tagInput,
                        onValueChange = viewModel::setTagInput,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.library_field_tag)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.appAccents.study.color
                        )
                    )
                    IconButton(onClick = { viewModel.addTag() }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }

                if (state.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.tags.forEach { tag ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.removeTag(tag) },
                                label = { Text("#$tag") }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // === Кнопки ===
        androidx.compose.material3.Button(
            onClick = {
                if (viewModel.save()) onBack()
            },
            enabled = state.title.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Text(
                text = stringResource(R.string.library_save),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (editItemId != null) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.library_delete),
                    color = MaterialTheme.appAccents.urgent.color
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showDeleteDialog) {
        ThemedAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(stringResource(R.string.library_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteCurrent()
                        onBack()
                    }
                ) {
                    Text(
                        stringResource(R.string.library_delete),
                        color = MaterialTheme.appAccents.urgent.color
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun typeLabel(type: LibraryItemType): String = when (type) {
    LibraryItemType.BOOK -> stringResource(R.string.library_filter_books)
    LibraryItemType.MOVIE -> stringResource(R.string.library_filter_movies)
    LibraryItemType.MUSIC -> stringResource(R.string.library_filter_music)
}
