package com.personal.sleepalarm.ui.diary

import com.personal.sleepalarm.ui.theme.appAccents

import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.clickable                    // ← ДОБАВЛЕНО
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import android.app.Application
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.DiaryEntryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application.applicationContext)
    private val diaryDao = database.diaryDao()

    val entries: StateFlow<List<DiaryEntryEntity>> = diaryDao
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(entry: DiaryEntryEntity) {
        viewModelScope.launch {
            if (entry.id == 0) diaryDao.insert(entry)
            else diaryDao.update(entry.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    /** Вставка или обновление с возвратом id (для автосохранения). */
    suspend fun upsert(entry: DiaryEntryEntity): Int {
        return if (entry.id == 0) {
            diaryDao.insert(entry).toInt()
        } else {
            diaryDao.update(entry.copy(updatedAt = System.currentTimeMillis()))
            entry.id
        }
    }

    fun delete(id: Int) {
        viewModelScope.launch { diaryDao.deleteById(id) }
    }

    fun deleteEntries(ids: List<Int>) {
        viewModelScope.launch {
            ids.forEach { diaryDao.deleteById(it) }   // ← ИСПРАВЛЕНО: было deleteById(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel = viewModel(),
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var editingEntry by remember { mutableStateOf<DiaryEntryEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedEntries by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showSelectionMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val diaryEntries = entries.filter { !it.dateKey.startsWith("dream_") && !it.dateKey.startsWith("idea_") }
    val dreamEntries = entries.filter { it.dateKey.startsWith("dream_") }
    val ideaEntries = entries.filter { it.dateKey.startsWith("idea_") }

    val currentEntries = when (selectedTab) {
        0 -> diaryEntries
        1 -> dreamEntries
        else -> ideaEntries
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedTab) {
                            0 -> stringResource(R.string.diary_title)
                            1 -> stringResource(R.string.diary_tab_dreams)
                            else -> stringResource(R.string.diary_tab_ideas)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    if (selectedEntries.isNotEmpty()) {
                        IconButton(onClick = { showSelectionMenu = true }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.appAccents.urgent.color)
                        }
                        IconButton(onClick = {
                            val text = currentEntries
                                .filter { it.id in selectedEntries }
                                .joinToString("\n\n---\n\n") { it.text }
                            clipboardManager.setText(AnnotatedString(text))
                            selectedEntries = emptySet()
                        }) {
                            Icon(Icons.Default.ContentCopy, null)
                        }
                        IconButton(onClick = {
                            val text = currentEntries
                                .filter { it.id in selectedEntries }
                                .joinToString("\n\n---\n\n") { it.text }
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                            selectedEntries = emptySet()
                        }) {
                            Icon(Icons.Default.Share, null)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when (selectedTab) {
                    0 -> {
                        // Дата выбирается АВТОМАТИЧЕСКИ — сегодня
                        editingEntry = DiaryEntryEntity(
                            dateKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                            text = ""
                        )
                        showEditor = true
                    }
                    1 -> {
                        editingEntry = DiaryEntryEntity(
                            dateKey = "dream_${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}",
                            text = ""
                        )
                        showEditor = true
                    }
                    else -> {
                        editingEntry = DiaryEntryEntity(
                            dateKey = "idea_${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}",
                            text = ""
                        )
                        showEditor = true
                    }
                }
            }) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; selectedEntries = emptySet() },
                    text = { Text(stringResource(R.string.diary_title)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; selectedEntries = emptySet() },
                    text = { Text(stringResource(R.string.diary_tab_dreams)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2; selectedEntries = emptySet() },
                    text = { Text(stringResource(R.string.diary_tab_ideas)) }
                )
            }

            if (currentEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.diary_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentEntries, key = { it.id }) { entry ->
                        DiaryRow(
                            entry = entry,
                            isSelected = entry.id in selectedEntries,
                            onClick = {
                                if (selectedEntries.isNotEmpty()) {
                                    selectedEntries = if (entry.id in selectedEntries) {
                                        selectedEntries - entry.id
                                    } else {
                                        selectedEntries + entry.id
                                    }
                                } else {
                                    editingEntry = entry
                                    showEditor = true
                                }
                            },
                            onLongClick = {
                                selectedEntries = setOf(entry.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSelectionMenu) {
        ModalBottomSheet(
            onDismissRequest = { showSelectionMenu = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    stringResource(R.string.diary_selection_actions, selectedEntries.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.appAccents.urgent.container.copy(alpha = 0.72f))
                        .clickable {
                            viewModel.deleteEntries(selectedEntries.toList())
                            selectedEntries = emptySet()
                            showSelectionMenu = false
                        }
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        null,
                        tint = MaterialTheme.appAccents.urgent.onContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.library_delete),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.appAccents.urgent.onContainer
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }


    if (showEditor) {
        DiaryEditor(
            initial = editingEntry,
            viewModel = viewModel,
            onBack = { showEditor = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiaryRow(
    entry: DiaryEntryEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isDream = entry.dateKey.startsWith("dream_")
    val isIdea = entry.dateKey.startsWith("idea_")
    val dateKey = when {
        isDream -> entry.dateKey.removePrefix("dream_")
        isIdea -> entry.dateKey.removePrefix("idea_")
        else -> entry.dateKey
    }
    val date = LocalDate.parse(dateKey)
    val dateText = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEE", Locale.getDefault()))
    val emptyPreview = stringResource(R.string.diary_empty_short)
    val preview = entry.text.take(100).ifEmpty { emptyPreview }
    val tone = when {
        isSelected || isIdea -> MaterialTheme.appAccents.creative
        isDream -> MaterialTheme.appAccents.sleep
        else -> MaterialTheme.appAccents.leisure
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                tone.container
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDream) {
                    Text("💤 ", style = MaterialTheme.typography.titleSmall)
                } else if (isIdea) {
                    Text("💡 ", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    dateText,
                    style = MaterialTheme.typography.titleSmall,
                    color = tone.onContainer
                )
            }
            Spacer(Modifier.height(4.dp))

            ThemedMarkdownText(
                markdown = preview,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryEditor(
    initial: DiaryEntryEntity?,
    viewModel: DiaryViewModel,
    onBack: () -> Unit
) {
    val isDream = initial?.dateKey?.startsWith("dream_") == true
    val isIdea = initial?.dateKey?.startsWith("idea_") == true

    // Дата — изменяемое состояние: по умолчанию сегодня или дата существующей записи
    var dateKey by remember {
        mutableStateOf(
            when {
                isDream -> initial!!.dateKey.removePrefix("dream_")
                isIdea -> initial!!.dateKey.removePrefix("idea_")
                else -> initial?.dateKey
                    ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            }
        )
    }
    val date = LocalDate.parse(dateKey)
    val finalDateKey = when {
        isDream -> "dream_$dateKey"
        isIdea -> "idea_$dateKey"
        else -> dateKey
    }
    val initialText = initial?.text ?: ""

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = initialText))
    }

    var isPreviewMode by remember { mutableStateOf(initial != null) }
    var showDatePicker by remember { mutableStateOf(false) }

    var savedId by remember { mutableStateOf(initial?.id ?: 0) }
    val scope = rememberCoroutineScope()

    val colorScheme = MaterialTheme.colorScheme
    val accents = MaterialTheme.appAccents

    val markdownTransformation = remember(colorScheme, accents) {
        MarkdownVisualTransformation(
            headingColor = accents.creative.color,
            quoteColor = accents.info.color,
            listColor = accents.leisure.color,
            codeColor = accents.urgent.color,
            codeBackground = accents.info.container,
            boldColor = colorScheme.onBackground,
            italicColor = colorScheme.onSurfaceVariant,
            mathColor = accents.study.color
        )
    }

    fun persist() {
        scope.launch {
            savedId = viewModel.upsert(
                DiaryEntryEntity(
                    id = savedId,
                    dateKey = finalDateKey,
                    text = textFieldValue.text
                )
            )
        }
    }

    // Автосохранение через 1 секунду после последней правки
    LaunchedEffect(textFieldValue.text) {
        delay(1000)
        if (textFieldValue.text != initialText || savedId != 0) {
            persist()
        }
    }

    // Выход = сохранить + назад
    val handleBack: () -> Unit = {
        scope.launch {
            if (savedId != 0 || textFieldValue.text.isNotBlank()) {
                savedId = viewModel.upsert(
                    DiaryEntryEntity(
                        id = savedId,
                        dateKey = finalDateKey,
                        text = textFieldValue.text
                    )
                )
            }
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Тап по дате — открыть календарь для смены даты
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showDatePicker = true }
                    ) {
                        val prefix = when {
                            isDream -> stringResource(R.string.diary_dream_prefix)
                            isIdea -> stringResource(R.string.diary_idea_prefix)
                            else -> ""
                        }
                        Text(
                            "$prefix${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))}"
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.diary_change_date),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                        Icon(
                            imageVector = if (isPreviewMode) Icons.Default.Edit else Icons.Default.Visibility,
                            contentDescription = stringResource(
                                if (isPreviewMode) R.string.diary_edit else R.string.diary_preview
                            ),
                            tint = MaterialTheme.appAccents.creative.color
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (isPreviewMode) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                item {
                    if (textFieldValue.text.isBlank()) {
                        Text(
                            text = stringResource(R.string.diary_empty_entry),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        ThemedMarkdownText(
                            markdown = textFieldValue.text,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.appAccents.creative.color),
                visualTransformation = markdownTransformation,
                decorationBox = { innerTextField ->
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = when {
                                isDream -> stringResource(R.string.diary_dream_placeholder)
                                isIdea -> stringResource(R.string.diary_idea_placeholder)
                                else -> stringResource(R.string.diary_entry_placeholder)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )
        }
    }

    // Календарь — только если тапнули по дате в шапке
    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = dateState.selectedDateMillis
                    if (ms != null) {
                        val ld = Instant.ofEpochMilli(ms)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        dateKey = ld.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}
