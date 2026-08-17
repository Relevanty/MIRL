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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.personal.sleepalarm.ui.dday.DDayScreen
import com.personal.sleepalarm.ui.home.HomeScreen
import com.personal.sleepalarm.ui.home.HomeViewModel
import com.personal.sleepalarm.ui.library.LibraryScreen
import com.personal.sleepalarm.ui.library.LibraryViewModel
import com.personal.sleepalarm.ui.misc.BriefingSettingsScreen
import com.personal.sleepalarm.ui.misc.MiscBottomSheet
import com.personal.sleepalarm.ui.misc.MiscScreen
import com.personal.sleepalarm.ui.pomodoro.PomodoroScreen
import com.personal.sleepalarm.ui.pomodoro.PomodoroViewModel
import com.personal.sleepalarm.ui.reminders.ReminderEditScreen
import com.personal.sleepalarm.ui.reminders.RemindersScreen
import com.personal.sleepalarm.ui.settings.SettingsScreen
import com.personal.sleepalarm.ui.settings.SettingsViewModel
import com.personal.sleepalarm.ui.tasks.TasksScreen
import com.personal.sleepalarm.ui.tasks.TasksViewModel
import com.personal.sleepalarm.ui.theme.SleepAlarmTheme
import com.personal.sleepalarm.ui.theme.ThemeCatalog
import com.personal.sleepalarm.service.SleepNotificationBuilder
import com.personal.sleepalarm.util.PermissionChecker
import com.personal.sleepalarm.util.PermissionState
import com.personal.sleepalarm.util.AppLanguageManager
import androidx.compose.runtime.remember
/**
 * Единственная Activity основного UI.
 *
 * ДОБАВЛЕНО (v5): 5 вкладок — Сон / Задачи / Помодоро / Разное / Настройки.
 * «Разное» открывает popup со списком модулей (Библиотека, Напоминания,
 * D-Day, Ассистент, Брифинг). Убраны вкладки «Расписание» и «Библиотека».
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    private var permissionState by mutableStateOf(PermissionState())

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        refreshPermissionState()

        setContent {
            val app = LocalContext.current.applicationContext as App
            val themeId by app.serviceLocator.themePreference
                .observeThemeId()
                .collectAsStateWithLifecycle(initialValue = ThemeCatalog.DEFAULT_ID)

            SleepAlarmTheme(themeId = themeId) {
                if (permissionState.allRequiredGranted) {
                    SleepAlarmRoot()
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
            val preferences = getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
            if (!preferences.getBoolean(KEY_DND_CHANNEL_CONFIGURED, false)) {
                SleepNotificationBuilder(this).ensureAlarmChannelCanBypassDnd()
                preferences.edit().putBoolean(KEY_DND_CHANNEL_CONFIGURED, true).apply()
            }
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
        private const val PERMISSION_PREFS = "required_permissions"
        private const val KEY_NOTIFICATIONS_REQUESTED = "notifications_requested"
        private const val KEY_DND_CHANNEL_CONFIGURED = "dnd_channel_configured"
    }
}

private const val TAB_SLEEP = 0
private const val TAB_TASKS = 1
private const val TAB_POMODORO = 2
private const val TAB_SETTINGS = 4

@Composable
private fun SleepAlarmRoot() {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_SLEEP) }
    var showMiscSheet by rememberSaveable { mutableStateOf(false) }
    var miscScreen by remember { mutableStateOf<MiscScreen?>(null) }
    var linkedTaskForReminder by rememberSaveable { mutableStateOf<Int?>(null) }
    var showDiary by rememberSaveable { mutableStateOf(false) }

    val homeViewModel: HomeViewModel = viewModel()
    val tasksViewModel: TasksViewModel = viewModel()
    val pomodoroViewModel: PomodoroViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    if (showDiary) {
        BackHandler { showDiary = false }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            DiaryScreen(onBack = { showDiary = false })
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

    // === Экран из «Разное» (поверх вкладок) ===
    if (miscScreen != null) {
        BackHandler { miscScreen = null }

        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            when (miscScreen) {
                MiscScreen.Library -> LibraryScreen(
                    onBack = { miscScreen = null },
                    viewModel = libraryViewModel
                )
                MiscScreen.Reminders -> RemindersScreen(onBack = { miscScreen = null })
                MiscScreen.DDay -> DDayScreen(onBack = { miscScreen = null })
                MiscScreen.Assistant -> AssistantScreen(onBack = { miscScreen = null })
                MiscScreen.Briefing -> BriefingSettingsScreen(onBack = { miscScreen = null })
                null -> { /* недостижимо */ }
            }
        }
        return
    }

    // === Основной контент с нижней панелью ===
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val navigationLabels = listOf(
                stringResource(R.string.tab_home),
                stringResource(R.string.tab_tasks),
                stringResource(R.string.tab_pomodoro),
                stringResource(R.string.tab_misc),
                stringResource(R.string.tab_settings)
            )
            BoxWithConstraints {
                val labelFontSize = rememberNavigationLabelFontSize(
                    labels = navigationLabels,
                    navigationWidth = maxWidth
                )
                val navigationColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
                        icon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
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
                        selected = showMiscSheet || miscScreen != null,
                        onClick = { showMiscSheet = true },
                        icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
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
                    onOpenDiary = { showDiary = true }   // ← ДОБАВЛЕНО
                )
                TAB_TASKS -> CalendarScreen()
                TAB_POMODORO -> PomodoroScreen(viewModel = pomodoroViewModel)
                TAB_SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
                else -> HomeScreen(
                    viewModel = homeViewModel,
                    onOpenDiary = { showDiary = true }   // ← ДОБАВЛЕНО
                )
            }
        }
    }

    // === Popup «Разное» ===
    if (showMiscSheet) {
        MiscBottomSheet(
            onDismiss = { showMiscSheet = false },
            onSelect = { screen ->
                showMiscSheet = false
                miscScreen = screen
            }
        )
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
