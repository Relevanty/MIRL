package com.personal.sleepalarm.ui.tasks

import com.personal.sleepalarm.ui.theme.appAccents
import com.personal.sleepalarm.ui.theme.AppAccentTone

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.db.entity.TaskDemandProfileEntity
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.util.CoverHelper
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.calculator.TaskDeadlinePlanCalculator
import com.personal.sleepalarm.ui.components.DeadlineDateTimeField
import com.personal.sleepalarm.ui.components.TaskDeadlinePlanSummary
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditorScreen(
    initialTask: TaskEntity?,
    initialDemandProfile: TaskDemandProfileEntity?,
    availableDependencyTasks: List<TaskEntity>,
    initialDependencyIds: Set<Int>,
    onBack: () -> Unit,
    onSave: (TaskEntity, TaskDemandProfileEntity, Set<Int>) -> Unit,
    onImportImage: suspend (Uri) -> String?,
    projects: List<ProjectEntity> = emptyList(),
    modifier: Modifier = Modifier
) {
    val base = remember(initialTask) { initialTask ?: TaskEntity(title = "", category = "") }
    var title by remember(base.id, base.createdAt) { mutableStateOf(base.title) }
    var description by remember(base.id, base.createdAt) { mutableStateOf(base.description) }
    var whyImportant by remember(base.id, base.createdAt) { mutableStateOf(base.whyImportant) }
    var definitionOfDone by remember(base.id, base.createdAt) { mutableStateOf(base.definitionOfDone) }
    var nextAction by remember(base.id, base.createdAt) { mutableStateOf(base.nextAction) }
    var imagePath by remember(base.id, base.createdAt) { mutableStateOf(base.imagePath) }
    var dueAtMillis by remember(base.id, base.createdAt) { mutableStateOf(base.dueAtMillis) }
    val deadlineNow by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    var estimatedMinutes by remember(base.id, base.createdAt) { mutableIntStateOf(base.estimatedMinutes) }
    var energy by remember(base.id, base.createdAt) { mutableStateOf(TaskEnergy.fromStorage(base.energyLevel)) }
    var contextTag by remember(base.id, base.createdAt) { mutableStateOf(base.contextTag) }
    var dependencies by remember(base.id, base.createdAt) { mutableStateOf(base.dependencies) }
    var obstacle by remember(base.id, base.createdAt) { mutableStateOf(base.obstacle) }
    var ifThenPlan by remember(base.id, base.createdAt) { mutableStateOf(base.ifThenPlan) }
    var checklist by remember(base.id, base.createdAt) { mutableStateOf(base.checklist) }
    var projectTag by remember(base.id, base.createdAt) { mutableStateOf(base.projectTag) }
    var assignee by remember(base.id, base.createdAt) { mutableStateOf(base.assignee) }
    var workBudgetMinutes by remember(base.id, base.createdAt) { mutableIntStateOf(base.workBudgetMinutes) }
    var plannedFocusMinutes by remember(base.id, base.createdAt) { mutableIntStateOf(base.plannedFocusMinutes) }
    var isDailyRequired by remember(base.id, base.createdAt) { mutableStateOf(base.isDailyRequired) }
    var projectId by remember(base.id, base.createdAt) { mutableStateOf(base.projectId) }
    var category by remember(base.id, base.createdAt) { mutableStateOf(base.category) }
    var tags by remember(base.id, base.createdAt) { mutableStateOf(base.tags) }
    var materials by remember(base.id, base.createdAt) { mutableStateOf(base.materials) }
    var expectedResult by remember(base.id, base.createdAt) { mutableStateOf(base.expectedResult) }
    var repeatRule by remember(base.id, base.createdAt) { mutableStateOf(base.repeatRule) }
    var quadrant by remember(base.id, base.createdAt) { mutableStateOf(TaskQuadrant.fromStorage(base.matrixQuadrant)) }
    val initialPreset = remember(base.id, initialDemandProfile) {
        TaskWorkModePreset.fromStorage(initialDemandProfile?.workMode, base.category)
    }
    var workMode by remember(base.id, initialDemandProfile) { mutableStateOf(initialPreset) }
    var difficulty by remember(base.id, initialDemandProfile) {
        mutableIntStateOf(initialDemandProfile?.difficulty ?: initialPreset.difficulty)
    }
    var concentrationDemand by remember(base.id, initialDemandProfile) {
        mutableIntStateOf(initialDemandProfile?.concentrationDemand ?: initialPreset.concentration)
    }
    var executiveDemand by remember(base.id, initialDemandProfile) {
        mutableIntStateOf(initialDemandProfile?.executiveDemand ?: initialPreset.executive)
    }
    var memoryDemand by remember(base.id, initialDemandProfile) {
        mutableIntStateOf(initialDemandProfile?.memoryDemand ?: initialPreset.memory)
    }
    var creativeDemand by remember(base.id, initialDemandProfile) {
        mutableIntStateOf(initialDemandProfile?.creativeDemand ?: initialPreset.creative)
    }
    var socialDemand by remember(base.id, initialDemandProfile) {
        mutableIntStateOf(initialDemandProfile?.socialDemand ?: initialPreset.social)
    }
    var physicalDemand by remember(base.id, initialDemandProfile) {
        mutableIntStateOf(initialDemandProfile?.physicalDemand ?: initialPreset.physical)
    }
    var emotionalDemand by remember(base.id, initialDemandProfile) {
        mutableIntStateOf(initialDemandProfile?.emotionalDemand ?: initialPreset.emotional)
    }
    var startFriction by remember(base.id, initialDemandProfile) {
        mutableIntStateOf(initialDemandProfile?.startFriction ?: initialPreset.startFriction)
    }
    var placeContext by remember(base.id, initialDemandProfile) {
        mutableStateOf(initialDemandProfile?.placeContext ?: "ANY")
    }
    var internetRequirement by remember(base.id, initialDemandProfile) {
        mutableStateOf(initialDemandProfile?.internetRequirement ?: "ANY")
    }
    var selectedDependencyIds by remember(base.id, initialDependencyIds) {
        mutableStateOf(initialDependencyIds)
    }
    var expandedPlan by remember { mutableStateOf(
        dependencies.isNotBlank() || obstacle.isNotBlank() || ifThenPlan.isNotBlank() || checklist.isNotBlank()
    ) }
    var importingImage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                importingImage = true
                onImportImage(uri)?.let { imagePath = it }
                importingImage = false
            }
        }
    }
    fun applyWorkMode(preset: TaskWorkModePreset) {
        workMode = preset
        difficulty = preset.difficulty
        concentrationDemand = preset.concentration
        executiveDemand = preset.executive
        memoryDemand = preset.memory
        creativeDemand = preset.creative
        socialDemand = preset.social
        physicalDemand = preset.physical
        emotionalDemand = preset.emotional
        startFriction = preset.startFriction
    }

    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (base.id == 0) R.string.task_new_title else R.string.task_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    TextButton(
                        // Новая задача идентифицируется фотографией; старые задачи
                        // без фото остаются редактируемыми для обратной совместимости.
                        enabled = (base.id != 0 || imagePath != null) &&
                            category in setOf("WORK", "STUDY", "OTHER"),
                        onClick = {
                            val savedTask = base.copy(
                                    title = title.trim(),
                                    description = description.trim(),
                                    whyImportant = whyImportant.trim(),
                                    definitionOfDone = definitionOfDone.trim(),
                                    nextAction = nextAction.trim(),
                                    imagePath = imagePath,
                                    dueAtMillis = dueAtMillis,
                                    estimatedMinutes = estimatedMinutes,
                                    energyLevel = energy.storageValue,
                                    contextTag = contextTag.trim(),
                                    dependencies = dependencies.trim(),
                                    obstacle = obstacle.trim(),
                                    ifThenPlan = ifThenPlan.trim(),
                                    checklist = checklist.trim(),
                                    projectTag = projectTag.trim(),
                                    assignee = assignee.trim(),
                                    workBudgetMinutes = workBudgetMinutes,
                                    plannedFocusMinutes = plannedFocusMinutes,
                                    isDailyRequired = isDailyRequired,
                                    projectId = projectId,
                                    category = category,
                                    tags = tags.trim(),
                                    materials = materials.trim(),
                                    expectedResult = expectedResult.trim(),
                                    repeatRule = repeatRule.trim(),
                                    matrixQuadrant = quadrant.storageValue
                                )
                            onSave(
                                savedTask,
                                (initialDemandProfile ?: TaskDemandProfileEntity(taskId = base.id)).copy(
                                    taskId = base.id,
                                    domain = category,
                                    workMode = workMode.storageValue,
                                    difficulty = difficulty,
                                    concentrationDemand = concentrationDemand,
                                    executiveDemand = executiveDemand,
                                    memoryDemand = memoryDemand,
                                    creativeDemand = creativeDemand,
                                    socialDemand = socialDemand,
                                    physicalDemand = physicalDemand,
                                    emotionalDemand = emotionalDemand,
                                    startFriction = startFriction,
                                    placeContext = placeContext,
                                    internetRequirement = internetRequirement,
                                    minimumBlockMinutes = minOf(estimatedMinutes, 10),
                                    preferredBlockMinutes = estimatedMinutes,
                                    provenance = "USER",
                                    confidence = 1f,
                                    updatedAt = System.currentTimeMillis()
                                ),
                                selectedDependencyIds
                            )
                        }
                    ) { Text(stringResource(R.string.task_save)) }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                TaskEditorSection(
                    title = stringResource(R.string.task_section_activity),
                    hint = stringResource(R.string.task_section_activity_hint),
                    tone = MaterialTheme.appAccents.work
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.task_field_title)) },
                        supportingText = { Text(stringResource(R.string.task_field_title_ecosystem_hint)) },
                        singleLine = true
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "STUDY" to R.string.activity_study,
                            "WORK" to R.string.activity_work,
                            "OTHER" to R.string.activity_other
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = category == value,
                                onClick = { category = value },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }
                    if (category.isBlank()) {
                        Text(
                            stringResource(R.string.task_activity_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appAccents.warning.color
                        )
                    }
                }
            }

            item {
                TaskEditorSection(
                    title = stringResource(R.string.task_section_result),
                    hint = stringResource(R.string.task_section_result_hint),
                    tone = MaterialTheme.appAccents.progress
                ) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.task_field_description)) },
                        minLines = 3,
                        maxLines = 6
                    )
                    OutlinedTextField(
                        value = whyImportant,
                        onValueChange = { whyImportant = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.task_field_why)) },
                        supportingText = { Text(stringResource(R.string.task_field_why_hint)) },
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = expectedResult,
                        onValueChange = { expectedResult = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.task_field_expected_result)) },
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = definitionOfDone,
                        onValueChange = { definitionOfDone = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.task_field_done_definition)) },
                        supportingText = { Text(stringResource(R.string.task_field_done_hint)) },
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = nextAction,
                        onValueChange = { nextAction = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.task_field_next_action)) },
                        supportingText = { Text(stringResource(R.string.task_field_next_hint)) },
                        minLines = 2
                    )
                }
            }

            item {
                TaskEditorSection(
                    title = stringResource(R.string.task_section_visual),
                    tone = MaterialTheme.appAccents.creative
                ) {
                    if (imagePath == null) {
                        Text(
                            stringResource(R.string.task_image_required_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (imagePath != null) {
                        Box(Modifier.fillMaxWidth()) {
                            LocalTaskImage(imagePath, Modifier.fillMaxWidth().height(180.dp))
                            IconButton(
                                onClick = { imagePath = null },
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            ) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)) {
                                    Icon(Icons.Default.Close, stringResource(R.string.task_remove_image), Modifier.padding(7.dp), tint = MaterialTheme.colorScheme.surface)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = !importingImage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(if (imagePath == null) Icons.Default.AddPhotoAlternate else Icons.Default.Image, null)
                        Text(
                            stringResource(
                                if (importingImage) R.string.task_image_loading
                                else if (imagePath == null) R.string.task_add_image
                                else R.string.task_replace_image
                            ),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            item {
                TaskEditorSection(
                    title = stringResource(R.string.task_adaptive_profile_title),
                    hint = stringResource(R.string.task_adaptive_profile_hint),
                    tone = MaterialTheme.appAccents.energy
                ) {
                    Text(
                        stringResource(R.string.task_work_mode_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TaskWorkModePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = workMode == preset,
                                onClick = { applyWorkMode(preset) },
                                label = { Text(stringResource(preset.labelRes)) }
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.task_profile_autofill_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(stringResource(R.string.task_place_label), style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "ANY" to R.string.task_place_any,
                            "INDOOR" to R.string.task_place_indoor,
                            "OUTDOOR" to R.string.task_place_outdoor
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = placeContext == value,
                                onClick = { placeContext = value },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }
                    Text(stringResource(R.string.task_internet_label), style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "ANY" to R.string.task_internet_any,
                            "REQUIRED" to R.string.task_internet_required,
                            "OFFLINE" to R.string.task_internet_offline
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = internetRequirement == value,
                                onClick = { internetRequirement = value },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }
                    if (availableDependencyTasks.isNotEmpty()) {
                        Text(
                            stringResource(R.string.task_dependency_structured_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            stringResource(R.string.task_dependency_structured_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableDependencyTasks
                                .filter { it.id != base.id && !it.isDone }
                                .forEach { prerequisite ->
                                    FilterChip(
                                        selected = prerequisite.id in selectedDependencyIds,
                                        onClick = {
                                            selectedDependencyIds = if (prerequisite.id in selectedDependencyIds) {
                                                selectedDependencyIds - prerequisite.id
                                            } else {
                                                selectedDependencyIds + prerequisite.id
                                            }
                                        },
                                        label = { Text(prerequisite.primaryLabel(), maxLines = 1) }
                                    )
                                }
                        }
                    }
                    DemandSlider(R.string.task_demand_difficulty, difficulty) { difficulty = it }
                    DemandSlider(R.string.task_demand_concentration, concentrationDemand) { concentrationDemand = it }
                    DemandSlider(R.string.task_demand_executive, executiveDemand) { executiveDemand = it }
                    DemandSlider(R.string.task_demand_memory, memoryDemand) { memoryDemand = it }
                    DemandSlider(R.string.task_demand_creativity, creativeDemand) { creativeDemand = it }
                    DemandSlider(R.string.task_demand_social, socialDemand) { socialDemand = it }
                    DemandSlider(R.string.task_demand_physical, physicalDemand) { physicalDemand = it }
                    DemandSlider(R.string.task_demand_emotional, emotionalDemand) { emotionalDemand = it }
                    DemandSlider(R.string.task_demand_start_friction, startFriction) { startFriction = it }
                }
            }

            item {
                TaskEditorSection(
                    title = stringResource(R.string.task_deadline_workload_title),
                    hint = stringResource(R.string.task_deadline_workload_hint),
                    tone = MaterialTheme.appAccents.schedule
                ) {
                    DeadlineDateTimeField(
                        value = dueAtMillis,
                        onValueChange = { dueAtMillis = it },
                        tone = MaterialTheme.appAccents.schedule
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.daily_focus_bout_label), style = MaterialTheme.typography.labelLarge)
                            Text(
                                stringResource(R.string.task_estimate_value, estimatedMinutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appAccents.work.color
                            )
                        }
                        Slider(
                            value = estimatedMinutes.toFloat(),
                            onValueChange = { estimatedMinutes = ((it / 5f).roundToInt() * 5).coerceIn(5, 180) },
                            valueRange = 5f..180f,
                            steps = 34,
                            modifier = Modifier.weight(1.4f)
                        )
                    }
                    Text(
                        stringResource(R.string.daily_focus_bout_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.task_field_work_budget), style = MaterialTheme.typography.labelLarge)
                            Text(
                                formatBudget(workBudgetMinutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appAccents.focus.color
                            )
                        }
                        Slider(
                            value = workBudgetMinutes.coerceAtMost(48 * 60).toFloat(),
                            onValueChange = { workBudgetMinutes = ((it / 15f).roundToInt() * 15).coerceIn(0, 48 * 60) },
                            valueRange = 0f..(48 * 60).toFloat(),
                            steps = 191,
                            modifier = Modifier.weight(1.4f)
                        )
                    }
                    Text(
                        stringResource(R.string.task_deadline_total_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.daily_focus_daily_target_label), style = MaterialTheme.typography.labelLarge)
                            Text(
                                stringResource(R.string.daily_focus_daily_target_value, plannedFocusMinutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appAccents.other.color
                            )
                        }
                        Slider(
                            value = plannedFocusMinutes.toFloat(),
                            onValueChange = { plannedFocusMinutes = ((it / 5f).roundToInt() * 5).coerceIn(5, 480) },
                            valueRange = 5f..480f,
                            steps = 94,
                            modifier = Modifier.weight(1.4f)
                        )
                    }
                    Text(
                        stringResource(R.string.daily_focus_daily_target_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.daily_focus_required_label),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                stringResource(R.string.daily_focus_required_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDailyRequired,
                            onCheckedChange = { isDailyRequired = it }
                        )
                    }
                    TaskDeadlinePlanSummary(
                        plan = TaskDeadlinePlanCalculator.calculate(
                            base.copy(
                                dueAtMillis = dueAtMillis,
                                workBudgetMinutes = workBudgetMinutes,
                                plannedFocusMinutes = plannedFocusMinutes,
                                estimatedMinutes = estimatedMinutes
                            ),
                            nowMillis = deadlineNow,
                            zone = ZoneId.systemDefault()
                        )
                    )
                }
            }

            item {
                TaskEditorSection(
                    title = stringResource(R.string.task_section_conditions),
                    hint = stringResource(R.string.task_section_conditions_hint),
                    tone = MaterialTheme.appAccents.schedule
                ) {
                    Text(stringResource(R.string.task_field_quadrant), style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TaskQuadrant.entries.forEach { item ->
                            FilterChip(
                                selected = quadrant == item,
                                onClick = { quadrant = item },
                                label = { Text(item.shortName(), maxLines = 1) }
                            )
                        }
                    }


                    Text(stringResource(R.string.task_field_energy), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TaskEnergy.entries.forEach { item ->
                            FilterChip(
                                selected = energy == item,
                                onClick = { energy = item },
                                label = { Text(item.displayName()) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = contextTag,
                        onValueChange = { contextTag = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.task_field_context)) },
                        placeholder = { Text(stringResource(R.string.task_field_context_hint)) },
                        singleLine = true
                    )

                    if (projects.isNotEmpty()) {
                        Text(stringResource(R.string.task_field_project), style = MaterialTheme.typography.labelLarge)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = projectId == null,
                                onClick = { projectId = null },
                                label = { Text(stringResource(R.string.task_project_none)) }
                            )
                            projects.filterNot { it.isArchived }.forEach { project ->
                                FilterChip(
                                    selected = projectId == project.id,
                                    onClick = { projectId = project.id },
                                    label = { Text(project.title) }
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = projectTag,
                            onValueChange = { projectTag = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.task_field_project)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tags,
                            onValueChange = { tags = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.task_field_tags)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = materials,
                            onValueChange = { materials = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.task_field_materials)) },
                            minLines = 2
                        )
                        OutlinedTextField(
                            value = repeatRule,
                            onValueChange = { repeatRule = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.task_field_repeat)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = assignee,
                            onValueChange = { assignee = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.task_field_assignee)) },
                            singleLine = true
                        )
                    }
                }
            }

            item {
                OutlinedButton(onClick = { expandedPlan = !expandedPlan }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (expandedPlan) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    Text(stringResource(R.string.task_section_plan), Modifier.padding(start = 8.dp))
                }
            }

            if (expandedPlan) {
                item {
                    TaskEditorSection(
                        title = stringResource(R.string.task_section_plan),
                        hint = stringResource(R.string.task_section_plan_hint),
                        tone = MaterialTheme.appAccents.schedule
                    ) {
                        OutlinedTextField(
                            value = checklist,
                            onValueChange = { checklist = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.task_field_checklist)) },
                            supportingText = { Text(stringResource(R.string.task_field_checklist_hint)) },
                            minLines = 3
                        )
                        OutlinedTextField(
                            value = dependencies,
                            onValueChange = { dependencies = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.task_field_dependencies)) },
                            minLines = 2
                        )
                        OutlinedTextField(
                            value = obstacle,
                            onValueChange = { obstacle = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.task_field_obstacle)) },
                            minLines = 2
                        )
                        OutlinedTextField(
                            value = ifThenPlan,
                            onValueChange = { ifThenPlan = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.task_field_if_then)) },
                            supportingText = { Text(stringResource(R.string.task_field_if_then_hint)) },
                            minLines = 2
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun TaskEditorSection(
    title: String,
    hint: String? = null,
    tone: AppAccentTone,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = tone.container,
        contentColor = tone.onContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = tone.onContainer,
                fontWeight = FontWeight.SemiBold
            )
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = tone.onContainer.copy(alpha = 0.78f))
            }
            HorizontalDivider(color = tone.color.copy(alpha = 0.42f))
            content()
        }
    }
}

@Composable
private fun DemandSlider(
    labelRes: Int,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.task_demand_value, value + 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 4)) },
            valueRange = 0f..4f,
            steps = 3,
            modifier = Modifier.weight(1.2f)
        )
    }
}

private enum class TaskWorkModePreset(
    val storageValue: String,
    val labelRes: Int,
    val difficulty: Int,
    val concentration: Int,
    val executive: Int,
    val memory: Int,
    val creative: Int,
    val social: Int,
    val physical: Int,
    val emotional: Int,
    val startFriction: Int
) {
    DEEP("DEEP", R.string.task_mode_deep, 3, 4, 3, 2, 2, 0, 0, 1, 3),
    LEARNING("LEARNING", R.string.task_mode_learning, 2, 3, 2, 4, 1, 0, 0, 1, 2),
    CREATIVE("CREATIVE", R.string.task_mode_creative, 2, 3, 2, 1, 4, 0, 0, 2, 2),
    ADMIN("ADMIN", R.string.task_mode_admin, 1, 1, 3, 1, 0, 1, 0, 0, 1),
    COMMUNICATION("COMMUNICATION", R.string.task_mode_communication, 2, 2, 2, 1, 1, 4, 0, 3, 2),
    PHYSICAL("PHYSICAL", R.string.task_mode_physical, 2, 1, 1, 0, 0, 0, 4, 1, 2),
    RECOVERY("RECOVERY", R.string.task_mode_recovery, 0, 0, 0, 0, 0, 0, 1, 0, 0);

    companion object {
        fun fromStorage(value: String?, category: String): TaskWorkModePreset =
            entries.firstOrNull { it.storageValue == value } ?: when (category) {
                "STUDY" -> LEARNING
                "WORK" -> DEEP
                else -> ADMIN
            }
    }
}

@Composable
private fun TaskQuadrant.shortName(): String = when (this) {
    TaskQuadrant.NOW -> stringResource(R.string.task_quadrant_now_short)
    TaskQuadrant.SCHEDULE -> stringResource(R.string.task_quadrant_schedule_short)
    TaskQuadrant.DELEGATE -> stringResource(R.string.task_quadrant_delegate_short)
    TaskQuadrant.LET_GO -> stringResource(R.string.task_quadrant_let_go_short)
}

@Composable
private fun TaskEnergy.displayName(): String = when (this) {
    TaskEnergy.LOW -> stringResource(R.string.task_energy_low)
    TaskEnergy.MEDIUM -> stringResource(R.string.task_energy_medium)
    TaskEnergy.HIGH -> stringResource(R.string.task_energy_high)
}

private fun formatBudget(minutes: Int): String {
    if (minutes <= 0) return "—"
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest мин"
        rest == 0 -> "$hours ч"
        else -> "$hours ч $rest мин"
    }
}

@Composable
internal fun LocalTaskImage(path: String?, modifier: Modifier = Modifier, circular: Boolean = false) {
    val bitmap = remember(path, circular) { CoverHelper.loadBitmap(path, if (circular) 96 else 900) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.task_image_description),
            modifier = modifier.clip(if (circular) CircleShape else RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop
        )
    }
}
