package com.personal.sleepalarm.ui

import com.personal.sleepalarm.ui.diary.DiaryScreen
import com.personal.sleepalarm.ui.calendar.CalendarScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sleepalarm.R
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.ui.assistant.AssistantScreen
import com.personal.sleepalarm.ui.components.RequiredPermissionsScreen
import com.personal.sleepalarm.ui.english.EnglishVocabularyRoute
import com.personal.sleepalarm.ui.home.HomeScreen
import com.personal.sleepalarm.ui.home.HomeViewModel
import com.personal.sleepalarm.ui.library.LibraryScreen
import com.personal.sleepalarm.ui.library.LibraryViewModel
import com.personal.sleepalarm.ui.mathpractice.MathPracticeScreen
import com.personal.sleepalarm.ui.pomodoro.PomodoroScreen
import com.personal.sleepalarm.ui.pomodoro.PomodoroViewModel
import com.personal.sleepalarm.ui.reminders.ReminderEditScreen
import com.personal.sleepalarm.ui.reminders.RemindersScreen
import com.personal.sleepalarm.ui.settings.SettingsScreen
import com.personal.sleepalarm.ui.settings.SettingsViewModel
import com.personal.sleepalarm.ui.tasks.TasksScreen
import com.personal.sleepalarm.ui.tasks.TasksViewModel
import com.personal.sleepalarm.ui.stats.StatsScreen
import com.personal.sleepalarm.ui.stats.StatsViewModel
import com.personal.sleepalarm.ui.theme.SleepAlarmTheme
import com.personal.sleepalarm.ui.theme.appAccents
import com.personal.sleepalarm.ui.theme.ThemeCatalog
import com.personal.sleepalarm.service.SleepNotificationBuilder
import com.personal.sleepalarm.service.DndBypassCoordinator
import com.personal.sleepalarm.util.PermissionChecker
import com.personal.sleepalarm.util.PermissionState
import com.personal.sleepalarm.util.AppLanguageManager
import androidx.compose.runtime.remember
/**
 * Единственная Activity основного UI.
 *
 * Пять корневых вкладок: Сегодня / План / Фокус / Календарь / Настройки.
 * Дополнительные модули открываются как дочерние экраны из своего контекста:
 * библиотека из Плана, напоминания из Календаря, D-Day и ассистент из Сегодня.
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    private var permissionState by mutableStateOf(PermissionState())
    private var navigationDestination by mutableStateOf<String?>(null)
    private var navigationTaskId by mutableStateOf<Int?>(null)
    private var navigationEventId by mutableStateOf<Int?>(null)
    private var navigationEventStart by mutableStateOf<Long?>(null)
    private var navigationRequestToken by mutableIntStateOf(0)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleNavigationIntent(intent)
        refreshPermissionState()

        setContent {
            val app = LocalContext.current.applicationContext as App
            val themeId by app.serviceLocator.themePreference
                .observeThemeId()
                .collectAsStateWithLifecycle(initialValue = ThemeCatalog.DEFAULT_ID)

            SleepAlarmTheme(themeId = themeId) {
                if (permissionState.allRequiredGranted) {
                    SleepAlarmRoot(
                        navigationDestination = navigationDestination,
                        navigationTaskId = navigationTaskId,
                        navigationEventId = navigationEventId,
                        navigationEventStart = navigationEventStart,
                        navigationRequestToken = navigationRequestToken
                    )
                } else {
                    RequiredPermissionsScreen(
                        state = permissionState,
                        onRequestNotifications = ::requestNotificationPermission,
                        onOpenExactAlarms = {
                            openSettings(PermissionChecker.exactAlarmsIntent(this))
                        },
                        onOpenBatteryOptimization = {
                            openSettings(PermissionChecker.batteryOptimizationIntent(this))
                        },
                        onOpenFullScreenIntent = {
                            openSettings(PermissionChecker.fullScreenIntentSettings(this))
                        },
                        onOpenNotificationPolicy = {
                            openSettings(PermissionChecker.notificationPolicyIntent(this))
                        },
                        onRefresh = ::refreshPermissionState
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        navigationRequestToken += 1
        navigationDestination = intent?.getStringExtra(EXTRA_DESTINATION)
            ?.takeIf {
                it == DESTINATION_FOCUS_PROTOCOL ||
                    it == DESTINATION_TASKS ||
                    it == DESTINATION_CALENDAR ||
                    it == DESTINATION_REMINDERS
            }
        navigationTaskId = intent?.getIntExtra(EXTRA_TASK_ID, 0)?.takeIf { it > 0 }
        navigationEventId = intent?.getIntExtra(EXTRA_EVENT_ID, 0)?.takeIf { it > 0 }
        navigationEventStart = intent?.getLongExtra(EXTRA_EVENT_START, 0L)?.takeIf { it > 0L }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            openSettings(PermissionChecker.notificationsIntent(this))
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            openSettings(PermissionChecker.notificationsIntent(this))
            return
        }

        val preferences = getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
        val alreadyRequested = preferences.getBoolean(KEY_NOTIFICATIONS_REQUESTED, false)
        val canAskAgain = shouldShowRequestPermissionRationale(
            Manifest.permission.POST_NOTIFICATIONS
        )

        if (alreadyRequested && !canAskAgain) {
            openSettings(PermissionChecker.notificationsIntent(this))
        } else {
            preferences.edit().putBoolean(KEY_NOTIFICATIONS_REQUESTED, true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshPermissionState() {
        val state = PermissionChecker.state(this)
        if (state.notificationPolicyAccessGranted) {
            DndBypassCoordinator.reconcile(this)
        }
        permissionState = PermissionChecker.state(this)
    }

    private fun openSettings(intent: Intent?) {
        val target = intent?.takeIf { it.resolveActivity(packageManager) != null }
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        runCatching { startActivity(target) }
    }

    companion object {
        const val EXTRA_DESTINATION = "extra_navigation_destination"
        const val EXTRA_TASK_ID = "extra_navigation_task_id"
        const val EXTRA_EVENT_ID = "extra_navigation_event_id"
        const val EXTRA_EVENT_START = "extra_navigation_event_start"
        const val DESTINATION_FOCUS_PROTOCOL = "focus_protocol"
        const val DESTINATION_TASKS = "tasks"
        const val DESTINATION_CALENDAR = "calendar"
        const val DESTINATION_REMINDERS = "reminders"
        private const val PERMISSION_PREFS = "required_permissions"
        private const val KEY_NOTIFICATIONS_REQUESTED = "notifications_requested"
    }
}

private const val TAB_SLEEP = 0
private const val TAB_TASKS = 1
private const val TAB_POMODORO = 2
private const val TAB_CALENDAR = 3
private const val TAB_SETTINGS = 4

private enum class SecondaryScreen {
    LIBRARY,
    REMINDERS,
    ASSISTANT
}

@Composable
private fun SleepAlarmRoot(
    navigationDestination: String? = null,
    navigationTaskId: Int? = null,
    navigationEventId: Int? = null,
    navigationEventStart: Long? = null,
    navigationRequestToken: Int = 0
) {
    var selectedTab by rememberSaveable {
        mutableIntStateOf(
            if (navigationDestination == MainActivity.DESTINATION_FOCUS_PROTOCOL) {
                TAB_POMODORO
            } else if (navigationDestination == MainActivity.DESTINATION_TASKS) {
                TAB_TASKS
            } else {
                TAB_SLEEP
            }
        )
    }
    var secondaryScreen by remember { mutableStateOf<SecondaryScreen?>(null) }
    var linkedTaskForReminder by rememberSaveable { mutableStateOf<Int?>(null) }
    var showDiary by rememberSaveable { mutableStateOf(false) }
    var showAnalytics by rememberSaveable { mutableStateOf(false) }
    var showMathPractice by rememberSaveable { mutableStateOf(false) }
    var showEnglishLearning by rememberSaveable { mutableStateOf(false) }
    // Opening a task is an event, not persistent screen state. Keeping the id here
    // after the sheet was dismissed made every task-list update open it again.
    var requestedTaskId by rememberSaveable { mutableStateOf<Int?>(null) }
    var requestedNewTaskCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedLibraryItemId by rememberSaveable { mutableStateOf<Int?>(null) }
    var requestedDeadlines by rememberSaveable { mutableStateOf(false) }
    var deadlineEditorActive by remember { mutableStateOf(false) }
    var handledNavigationRequestToken by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(navigationDestination, navigationTaskId, navigationRequestToken) {
        if (handledNavigationRequestToken == navigationRequestToken) return@LaunchedEffect
        handledNavigationRequestToken = navigationRequestToken
        when (navigationDestination) {
            MainActivity.DESTINATION_FOCUS_PROTOCOL -> selectedTab = TAB_POMODORO
            MainActivity.DESTINATION_TASKS -> {
                requestedTaskId = navigationTaskId
                selectedTab = TAB_TASKS
            }
            MainActivity.DESTINATION_CALENDAR -> selectedTab = TAB_CALENDAR
            MainActivity.DESTINATION_REMINDERS -> {
                selectedTab = TAB_CALENDAR
                secondaryScreen = SecondaryScreen.REMINDERS
            }
        }
    }

    val homeViewModel: HomeViewModel = viewModel()
    val app = LocalContext.current.applicationContext as App
    val tasksViewModel: TasksViewModel = viewModel()
    val pomodoroViewModel: PomodoroViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val statsViewModel: StatsViewModel = viewModel()
    val tasksState by tasksViewModel.uiState.collectAsStateWithLifecycle()
    var preparedNavigationTaskId by rememberSaveable { mutableStateOf<Int?>(null) }
    var preparedNavigationRequestToken by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(navigationDestination, navigationTaskId, navigationRequestToken, tasksState.generalTasks) {
        val taskId = navigationTaskId
        if (
            navigationDestination == MainActivity.DESTINATION_FOCUS_PROTOCOL &&
            taskId != null &&
            (preparedNavigationTaskId != taskId || preparedNavigationRequestToken != navigationRequestToken)
        ) {
            tasksState.generalTasks.firstOrNull { it.id == taskId }?.let { task ->
                pomodoroViewModel.prepareWorkTask(task)
                preparedNavigationTaskId = taskId
                preparedNavigationRequestToken = navigationRequestToken
                selectedTab = TAB_POMODORO
            }
        }
    }

    if (showDiary) {
        BackHandler { showDiary = false }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            DiaryScreen(onBack = { showDiary = false })
        }
        return
    }

    if (showAnalytics) {
        BackHandler { showAnalytics = false }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            StatsScreen(viewModel = statsViewModel, onBack = { showAnalytics = false })
        }
        return
    }

    if (showMathPractice) {
        BackHandler { showMathPractice = false }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            MathPracticeScreen(onBack = { showMathPractice = false })
        }
        return
    }

    if (showEnglishLearning) {
        BackHandler { showEnglishLearning = false }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            EnglishVocabularyRoute(
                repository = app.serviceLocator.englishVocabularyRepository,
                onBack = { showEnglishLearning = false }
            )
        }
        return
    }

    // === Создание напоминания из задачи (поверх всего) ===
    if (linkedTaskForReminder != null) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            ReminderEditScreen(
                editReminderId = null,
                linkedTaskId = linkedTaskForReminder,
                onBack = { linkedTaskForReminder = null }
            )
        }
        return
    }

    // === Профильный дочерний экран поверх корневой вкладки ===
    if (secondaryScreen != null) {
        BackHandler { secondaryScreen = null }

        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            when (secondaryScreen) {
                SecondaryScreen.LIBRARY -> LibraryScreen(
                    onBack = { secondaryScreen = null },
                    viewModel = libraryViewModel,
                    openItemId = requestedLibraryItemId,
                    onOpenItemConsumed = { requestedLibraryItemId = null }
                )
                SecondaryScreen.REMINDERS -> RemindersScreen(onBack = { secondaryScreen = null })
                SecondaryScreen.ASSISTANT -> AssistantScreen(
                    onBack = { secondaryScreen = null },
                    onStartTaskFocus = { taskId ->
                        tasksState.generalTasks.firstOrNull { it.id == taskId }?.let { task ->
                            if (pomodoroViewModel.prepareWorkTask(task)) {
                                secondaryScreen = null
                                selectedTab = TAB_POMODORO
                            }
                        }
                    }
                )
                null -> { /* недостижимо */ }
            }
        }
        return
    }

    // === Основной контент с нижней панелью ===
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = navigation@{
            if (deadlineEditorActive) return@navigation
            val navigationLabels = listOf(
                stringResource(R.string.tab_today),
                stringResource(R.string.tab_plan),
                stringResource(R.string.tab_focus),
                stringResource(R.string.tab_calendar),
                stringResource(R.string.tab_settings)
            )
            BoxWithConstraints {
                val labelFontSize = rememberNavigationLabelFontSize(
                    labels = navigationLabels,
                    navigationWidth = maxWidth
                )
                val navigationColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.appAccents.focus.onContainer,
                    selectedTextColor = MaterialTheme.appAccents.chrome.onNavigation,
                    indicatorColor = MaterialTheme.appAccents.focus.container,
                    unselectedIconColor = MaterialTheme.appAccents.chrome.onNavigationMuted,
                    unselectedTextColor = MaterialTheme.appAccents.chrome.onNavigationMuted
                )
                NavigationBar(containerColor = MaterialTheme.appAccents.chrome.navigation) {
                    NavigationBarItem(
                        selected = selectedTab == TAB_SLEEP,
                        onClick = { selectedTab = TAB_SLEEP },
                        icon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                        label = { NavigationLabel(navigationLabels[0], labelFontSize) },
                        colors = navigationColors
                    )
                    NavigationBarItem(
                        selected = selectedTab == TAB_TASKS,
                        onClick = { selectedTab = TAB_TASKS },
                        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                        label = { NavigationLabel(navigationLabels[1], labelFontSize) },
                        colors = navigationColors
                    )
                    NavigationBarItem(
                        selected = selectedTab == TAB_POMODORO,
                        onClick = { selectedTab = TAB_POMODORO },
                        icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        label = { NavigationLabel(navigationLabels[2], labelFontSize) },
                        colors = navigationColors
                    )
                    NavigationBarItem(
                        selected = selectedTab == TAB_CALENDAR,
                        onClick = { selectedTab = TAB_CALENDAR },
                        icon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        label = { NavigationLabel(navigationLabels[3], labelFontSize) },
                        colors = navigationColors
                    )
                    NavigationBarItem(
                        selected = selectedTab == TAB_SETTINGS,
                        onClick = { selectedTab = TAB_SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { NavigationLabel(navigationLabels[4], labelFontSize) },
                        colors = navigationColors
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                TAB_SLEEP -> HomeScreen(
                    viewModel = homeViewModel,
                    onOpenDiary = { showDiary = true },
                    onOpenTasks = { selectedTab = TAB_TASKS },
                    onOpenStats = { showAnalytics = true },
                    onOpenDDay = { requestedDeadlines = true; selectedTab = TAB_CALENDAR },
                    onOpenAssistant = { secondaryScreen = SecondaryScreen.ASSISTANT },
                    onOpenMathPractice = { showMathPractice = true },
                    onOpenEnglishLearning = { showEnglishLearning = true },
                    openTaskCount = tasksState.activeMatrixTasks.size,
                    upcomingTasks = tasksState.activeMatrixTasks,
                    onStartTaskFocus = { task, focusMinutes ->
                        homeViewModel.recordRecommendationAccepted(task)
                        if (pomodoroViewModel.prepareWorkTask(task, focusMinutes)) {
                            selectedTab = TAB_POMODORO
                        }
                    }
                )
                TAB_TASKS -> TasksScreen(
                    viewModel = tasksViewModel,
                    openTaskId = requestedTaskId,
                    onOpenTaskRequestConsumed = { openedTaskId ->
                        if (requestedTaskId == openedTaskId) requestedTaskId = null
                    },
                    createTaskCategory = requestedNewTaskCategory,
                    onCreateTaskRequestConsumed = { requestedNewTaskCategory = null },
                    onAddReminder = { linkedTaskForReminder = it },
                    onOpenLibrary = { secondaryScreen = SecondaryScreen.LIBRARY },
                    onOpenLibraryItem = { itemId ->
                        requestedLibraryItemId = itemId
                        secondaryScreen = SecondaryScreen.LIBRARY
                    },
                    onStartFocus = { task ->
                        if (pomodoroViewModel.prepareWorkTask(task)) selectedTab = TAB_POMODORO
                    }
                )
                TAB_POMODORO -> PomodoroScreen(
                    viewModel = pomodoroViewModel,
                    onOpenTask = { taskId ->
                        requestedTaskId = taskId
                        selectedTab = TAB_TASKS
                    },
                    onCreateTask = { activityType ->
                        requestedNewTaskCategory = activityType.name
                        selectedTab = TAB_TASKS
                    }
                )
                TAB_CALENDAR -> CalendarScreen(
                    openDeadlines = requestedDeadlines,
                    onOpenDeadlinesConsumed = { requestedDeadlines = false },
                    onEditorActive = { deadlineEditorActive = it },
                    openEventId = navigationEventId,
                    openOccurrenceStart = navigationEventStart,
                    openRequestToken = navigationRequestToken,
                    onOpenReminders = { secondaryScreen = SecondaryScreen.REMINDERS },
                    onOpenTask = { taskId ->
                        requestedTaskId = taskId
                        selectedTab = TAB_TASKS
                    },
                    onStartFocus = { taskId ->
                        tasksState.generalTasks.firstOrNull { it.id == taskId }?.let { task ->
                            if (pomodoroViewModel.prepareWorkTask(task)) selectedTab = TAB_POMODORO
                        }
                    }
                )
                TAB_SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
                else -> HomeScreen(
                    viewModel = homeViewModel,
                    onOpenDiary = { showDiary = true },
                    onOpenTasks = { selectedTab = TAB_TASKS },
                    onOpenStats = { showAnalytics = true },
                    onOpenDDay = { requestedDeadlines = true; selectedTab = TAB_CALENDAR },
                    onOpenAssistant = { secondaryScreen = SecondaryScreen.ASSISTANT },
                    onOpenMathPractice = { showMathPractice = true },
                    onOpenEnglishLearning = { showEnglishLearning = true },
                    openTaskCount = tasksState.activeMatrixTasks.size,
                    upcomingTasks = tasksState.activeMatrixTasks,
                    onStartTaskFocus = { task, focusMinutes ->
                        homeViewModel.recordRecommendationAccepted(task)
                        if (pomodoroViewModel.prepareWorkTask(task, focusMinutes)) {
                            selectedTab = TAB_POMODORO
                        }
                    }
                )
            }
        }
    }

}

@Composable
private fun rememberNavigationLabelFontSize(
    labels: List<String>,
    navigationWidth: Dp
): TextUnit {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val maximumFontSize = 12.sp
    val maximumStyle = MaterialTheme.typography.labelMedium.copy(fontSize = maximumFontSize)
    val widestLabelPx = remember(
        labels,
        maximumStyle,
        textMeasurer,
        density.density,
        density.fontScale
    ) {
        labels.maxOf { label ->
            textMeasurer.measure(
                text = AnnotatedString(label),
                style = maximumStyle,
                softWrap = false,
                maxLines = 1
            ).size.width.toFloat()
        }
    }
    val totalWidthPx = with(density) { navigationWidth.toPx() }
    val horizontalReservePx = with(density) { 10.dp.toPx() }
    val availableLabelWidthPx =
        (totalWidthPx / labels.size - horizontalReservePx).coerceAtLeast(1f)
    val maximumFontSizePx = with(density) { maximumFontSize.toPx() }
    val fittedFontSizePx = calculateNavigationLabelFontSizePx(
        maximumFontSizePx = maximumFontSizePx,
        widestLabelWidthPx = widestLabelPx,
        availableLabelWidthPx = availableLabelWidthPx
    )
    return with(density) { fittedFontSizePx.toSp() }
}

@Composable
private fun NavigationLabel(text: String, fontSize: TextUnit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}

internal fun calculateNavigationLabelFontSizePx(
    maximumFontSizePx: Float,
    widestLabelWidthPx: Float,
    availableLabelWidthPx: Float
): Float {
    if (maximumFontSizePx <= 0f || widestLabelWidthPx <= 0f || availableLabelWidthPx <= 0f) {
        return maximumFontSizePx.coerceAtLeast(0f)
    }
    val scale = (availableLabelWidthPx / widestLabelWidthPx).coerceAtMost(1f)
    return maximumFontSizePx * scale.coerceAtLeast(MIN_NAVIGATION_LABEL_SCALE)
}

private const val MIN_NAVIGATION_LABEL_SCALE = 0.5f
