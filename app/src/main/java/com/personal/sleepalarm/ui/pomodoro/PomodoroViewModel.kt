package com.personal.sleepalarm.ui.pomodoro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.OtherActivityEntity
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.PomodoroSessionEntity
import com.personal.sleepalarm.data.db.entity.StudySessionEntity
import com.personal.sleepalarm.data.db.entity.SubjectEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.preferences.AppSignalPreferences
import com.personal.sleepalarm.data.preferences.AppSignalSettings
import com.personal.sleepalarm.data.preferences.AppSignalType
import com.personal.sleepalarm.data.preferences.AppSoundMode
import com.personal.sleepalarm.data.preferences.AppSoundSelection
import com.personal.sleepalarm.data.repository.ActivityRecordRepository
import com.personal.sleepalarm.data.repository.ManualActivityInput
import com.personal.sleepalarm.alarm.TaskLinkedReminderCoordinator
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.model.focusActivityType
import com.personal.sleepalarm.domain.model.focusItemTaskId
import com.personal.sleepalarm.domain.model.taskFocusItemId
import com.personal.sleepalarm.domain.model.primaryLabel
import com.personal.sleepalarm.domain.model.nextFocusDurationMinutes
import com.personal.sleepalarm.domain.coordinator.TaskLifecycleCoordinator
import com.personal.sleepalarm.domain.calculator.ActivityDayBoundary
import com.personal.sleepalarm.service.audio.AppNotificationSoundPlayer
import com.personal.sleepalarm.service.AppNotificationChannelIds
import com.personal.sleepalarm.ui.MainActivity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TimerMode {
    IDLE,
    FOCUS,
    FOCUS_PAUSED,
    BREAK,
    BREAK_PAUSED
}

private data class ActiveFocus(
    val type: FocusActivityType,
    val itemId: Int,
    val itemName: String
)

class PomodoroViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application.applicationContext)
    private val subjectDao = database.subjectDao()
    private val taskDao = database.taskDao()
    private val otherActivityDao = database.otherActivityDao()
    private val studyDao = database.studySessionDao()
    private val pomodoroDao = database.pomodoroDao()
    private val context = application.applicationContext
    private val taskReminderCoordinator = TaskLinkedReminderCoordinator(context, database)
    private val taskLifecycle = TaskLifecycleCoordinator(context, database)
    private val activityRepository = ActivityRecordRepository(
        database,
        taskReminderCoordinator
    )
    private val signalPreferences = AppSignalPreferences(context)

    val subjects: StateFlow<List<SubjectEntity>> = subjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workTasks: StateFlow<List<TaskEntity>> = taskDao.observeByRoutineFlag(false)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val otherActivities: StateFlow<List<OtherActivityEntity>> = otherActivityDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activityRecords: StateFlow<List<ActivityRecordEntity>> = activityRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val progressNowMillis: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        System.currentTimeMillis()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentDayRange: StateFlow<Pair<Long, Long>> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            val range = calculateActivityDayRange(now)
            emit(range)
            delay((range.second - now + 1_000L).coerceAtLeast(1_000L))
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        calculateActivityDayRange(System.currentTimeMillis())
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentDayFocusSessions: StateFlow<List<PomodoroSessionEntity>> = currentDayRange
        .flatMapLatest { (from, to) -> pomodoroDao.observeFocusOverlapping(from, to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activityType = MutableStateFlow(FocusActivityType.STUDY)
    val activityType: StateFlow<FocusActivityType> = _activityType

    private val _selectedItemId = MutableStateFlow<Int?>(null)
    val selectedItemId: StateFlow<Int?> = _selectedItemId
    private val selectedIds = mutableMapOf<FocusActivityType, Int?>()
    private var activeFocus: ActiveFocus? = null

    private val _focusDuration = MutableStateFlow(25L * MINUTE_MS)
    val focusDuration: StateFlow<Long> = _focusDuration

    private val _breakDuration = MutableStateFlow(5L * MINUTE_MS)
    val breakDuration: StateFlow<Long> = _breakDuration

    private val _signalSettings = MutableStateFlow(AppSignalSettings())
    val signalSettings: StateFlow<AppSignalSettings> = _signalSettings

    private val _tickerEnabled = MutableStateFlow(false)
    val tickerEnabled: StateFlow<Boolean> = _tickerEnabled

    private val _tickerInterval = MutableStateFlow(15)
    val tickerInterval: StateFlow<Int> = _tickerInterval

    private val _tickerStartTime = MutableStateFlow<LocalTime?>(null)
    val tickerStartTime: StateFlow<LocalTime?> = _tickerStartTime

    private val _tickerEndTime = MutableStateFlow<LocalTime?>(null)
    val tickerEndTime: StateFlow<LocalTime?> = _tickerEndTime

    private val _resetAfterBreak = MutableStateFlow(true)
    val resetAfterBreak: StateFlow<Boolean> = _resetAfterBreak

    private val _remaining = MutableStateFlow(25L * MINUTE_MS)
    val remaining: StateFlow<Long> = _remaining

    private val _mode = MutableStateFlow(TimerMode.IDLE)
    val mode: StateFlow<TimerMode> = _mode

    private var timerJob: Job? = null
    private var tickerJob: Job? = null
    private var startMillis = 0L

    init {
        createNotificationChannel()
        viewModelScope.launch {
            signalPreferences.observe(AppSignalType.POMODORO).collect {
                _signalSettings.value = it
            }
        }
        viewModelScope.launch {
            taskDao.observeByRoutineFlag(false).collect { tasks ->
                if (_mode.value == TimerMode.IDLE) {
                    focusItemTaskId(_selectedItemId.value)?.let { selectedTaskId ->
                        tasks.firstOrNull { it.id == selectedTaskId && !it.isDone }?.let { selectedTask ->
                            val canonicalType = selectedTask.focusActivityType()
                            if (_activityType.value != canonicalType) {
                                selectedIds[_activityType.value] = null
                                _activityType.value = canonicalType
                                _selectedItemId.value = taskFocusItemId(selectedTask.id)
                                selectedIds[canonicalType] = taskFocusItemId(selectedTask.id)
                            }
                        }
                    }
                }
                val focus = activeFocus ?: return@collect
                val taskId = focusItemTaskId(focus.itemId) ?: return@collect
                val task = tasks.firstOrNull { it.id == taskId }
                if (task == null || task.isDone) {
                    timerJob?.cancel()
                    if (_mode.value == TimerMode.FOCUS) {
                        if (task == null) {
                            // Deletion keeps elapsed work as an unlinked snapshot;
                            // completion records it on the task before closing focus.
                            activeFocus = focus.copy(itemId = 0)
                        }
                        finishFocusSession(completed = false)
                    }
                    activeFocus = null
                    _mode.value = TimerMode.IDLE
                    _remaining.value = _focusDuration.value
                } else {
                    activeFocus = focus.copy(
                        type = task.focusActivityType(),
                        itemName = task.primaryLabel()
                    )
                }
            }
        }
    }

    fun selectActivityType(type: FocusActivityType) {
        if (_mode.value != TimerMode.IDLE) return
        selectedIds[_activityType.value] = _selectedItemId.value
        _activityType.value = type
        _selectedItemId.value = selectedIds[type]
    }

    fun selectItem(id: Int) {
        if (_mode.value != TimerMode.IDLE) return
        selectedIds[_activityType.value] = id
        _selectedItemId.value = id
    }

    /** Подготавливает Pomodoro из карточки задачи в её собственной сфере. */
    fun prepareWorkTask(task: TaskEntity): Boolean {
        if (_mode.value != TimerMode.IDLE || task.isDone) return false
        val nextFocusMinutes = task.nextFocusDurationMinutes()
        if (nextFocusMinutes <= 0) return false
        selectActivityType(task.focusActivityType())
        selectItem(taskFocusItemId(task.id))
        // A final task cycle may legitimately be shorter than the generic
        // picker minimum, so do not route it through setFocusDuration().
        _focusDuration.value = nextFocusMinutes * MINUTE_MS
        _remaining.value = _focusDuration.value
        return true
    }

    fun setResetAfterBreak(value: Boolean) {
        _resetAfterBreak.value = value
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            POMODORO_CHANNEL,
            context.getString(R.string.pomodoro_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.pomodoro_notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun useSystemSound(uriString: String?) {
        saveSound(AppSoundSelection(AppSoundMode.SYSTEM, uriString))
    }

    fun useSoundFile(uriString: String) {
        saveSound(AppSoundSelection(AppSoundMode.FILE, uriString))
    }

    fun useSilentSound() {
        saveSound(AppSoundSelection(AppSoundMode.SILENT))
    }

    fun previewSound() {
        viewModelScope.launch {
            AppNotificationSoundPlayer.play(
                context,
                _signalSettings.value,
                allowSystemFallback = false
            )
        }
    }

    private fun saveSound(sound: AppSoundSelection) {
        val updated = _signalSettings.value.copy(sound = sound.normalized())
        _signalSettings.value = updated
        viewModelScope.launch {
            signalPreferences.setSound(AppSignalType.POMODORO, updated.sound)
        }
    }

    private fun showNotification(
        notificationId: Int,
        dedupeKey: String,
        title: String,
        text: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_FOCUS_PROTOCOL)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, POMODORO_CHANNEL)
            // Launcher aliases may use colorful artwork; notifications require a
            // dedicated single-color status-bar glyph.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setTimeoutAfter(6L * 60L * 60L * 1000L)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Audio is produced by exactly one in-app player. This also protects
            // against a user-assigned sound on an existing Android channel.
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(notificationId, notification) }
            .onSuccess {
                viewModelScope.launch {
                    AppNotificationSoundPlayer.play(
                        context = context,
                        settings = _signalSettings.value,
                        dedupeKey = dedupeKey
                    )
                }
            }
    }

    private fun cancelCompletionNotifications() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_FOCUS_COMPLETE_ID)
        manager.cancel(NOTIFICATION_BREAK_COMPLETE_ID)
    }

    fun start(itemId: Int, itemName: String) {
        if (_mode.value == TimerMode.FOCUS || _mode.value == TimerMode.BREAK ||
            _mode.value == TimerMode.BREAK_PAUSED
        ) return

        val type = _activityType.value
        cancelCompletionNotifications()
        selectedIds[type] = itemId
        _selectedItemId.value = itemId
        activeFocus = ActiveFocus(type, itemId, itemName)
        beginFocus(_focusDuration.value)
    }

    private fun beginFocus(duration: Long) {
        // A new focus interval means the previous break result has already
        // been handled; do not leave it hanging in the notification shade.
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_BREAK_COMPLETE_ID)
        timerJob?.cancel()
        startMillis = System.currentTimeMillis()
        _remaining.value = duration
        _mode.value = TimerMode.FOCUS
        timerJob = viewModelScope.launch {
            while (_remaining.value > 0L && _mode.value == TimerMode.FOCUS) {
                delay(1_000L)
                _remaining.value = (_remaining.value - 1_000L).coerceAtLeast(0L)
            }
            if (_remaining.value <= 0L && _mode.value == TimerMode.FOCUS) {
                onFocusCompleted()
            }
        }
    }

    private fun startBreak() {
        _remaining.value = _breakDuration.value
        _mode.value = TimerMode.BREAK
        timerJob = viewModelScope.launch {
            while (_remaining.value > 0L && _mode.value == TimerMode.BREAK) {
                delay(1_000L)
                _remaining.value = (_remaining.value - 1_000L).coerceAtLeast(0L)
            }
            if (_remaining.value <= 0L && _mode.value == TimerMode.BREAK) {
                onBreakCompleted()
            }
        }
    }

    private fun onFocusCompleted() {
        finishFocusSession(completed = true)
        showNotification(
            notificationId = NOTIFICATION_FOCUS_COMPLETE_ID,
            dedupeKey = "pomodoro-focus-$startMillis",
            title = context.getString(R.string.pomodoro_focus_complete_title),
            text = context.getString(R.string.pomodoro_focus_complete_message)
        )
        startBreak()
    }

    private fun onBreakCompleted() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_FOCUS_COMPLETE_ID)
        showNotification(
            notificationId = NOTIFICATION_BREAK_COMPLETE_ID,
            dedupeKey = "pomodoro-break-$startMillis",
            title = context.getString(R.string.pomodoro_break_complete_title),
            text = context.getString(R.string.pomodoro_break_complete_message)
        )
        _mode.value = if (_resetAfterBreak.value) TimerMode.IDLE else TimerMode.FOCUS_PAUSED
        _remaining.value = _focusDuration.value
    }

    fun toggle() {
        when (_mode.value) {
            TimerMode.IDLE -> activeFocus?.let { start(it.itemId, it.itemName) }
            TimerMode.FOCUS -> {
                timerJob?.cancel()
                finishFocusSession(completed = false)
                startBreak()
            }
            TimerMode.FOCUS_PAUSED -> beginFocus(_focusDuration.value)
            TimerMode.BREAK -> endBreakToIdle()
            TimerMode.BREAK_PAUSED -> {
                _mode.value = TimerMode.BREAK
                startBreak()
            }
        }
    }

    fun stop() {
        timerJob?.cancel()
        when (_mode.value) {
            TimerMode.FOCUS -> {
                finishFocusSession(completed = false)
                startBreak()
            }
            TimerMode.FOCUS_PAUSED -> startBreak()
            TimerMode.BREAK, TimerMode.BREAK_PAUSED -> endBreakToIdle()
            TimerMode.IDLE -> Unit
        }
    }

    private fun finishFocusSession(completed: Boolean) {
        val focus = activeFocus ?: return
        val end = System.currentTimeMillis()
        val duration = (end - startMillis).coerceAtMost(_focusDuration.value)
        if (duration < 1_000L) return

        val linkedTaskId = focusItemTaskId(focus.itemId)
        val session = PomodoroSessionEntity(
            startedAt = startMillis,
            durationMinutes = ((duration + MINUTE_MS - 1L) / MINUTE_MS).toInt(),
            completedAt = end,
            isCompleted = completed,
            isBreak = false,
            activityType = focus.type,
            subjectId = focus.itemId.takeIf {
                it > 0 && linkedTaskId == null && focus.type == FocusActivityType.STUDY
            },
            taskId = linkedTaskId,
            otherActivityId = focus.itemId.takeIf {
                it > 0 && linkedTaskId == null && focus.type == FocusActivityType.OTHER
            },
            itemName = focus.itemName,
            actualDurationMillis = duration,
            recordSource = "TIMER"
        )

        val recordedStart = startMillis
        viewModelScope.launch {
            val pomodoroId = pomodoroDao.insert(session).toInt()
            activityRepository.recordTimer(session, pomodoroId)
            if (focus.type == FocusActivityType.STUDY && linkedTaskId == null && focus.itemId > 0) {
                studyDao.insert(
                    StudySessionEntity(
                        subjectId = focus.itemId,
                        startMillis = recordedStart,
                        endMillis = end,
                        durationMillis = duration,
                        dateKey = dateKeyOf(recordedStart)
                    )
                )
            }
        }
    }

    fun setFocusDuration(minutes: Long) {
        _focusDuration.value = minutes.coerceIn(MIN_FOCUS_MINUTES, MAX_FOCUS_MINUTES) * MINUTE_MS
        if (_mode.value == TimerMode.IDLE || _mode.value == TimerMode.FOCUS_PAUSED) {
            _remaining.value = _focusDuration.value
        }
    }

    fun setBreakDuration(minutes: Long) {
        _breakDuration.value = minutes.coerceIn(MIN_BREAK_MINUTES, MAX_BREAK_MINUTES) * MINUTE_MS
    }

    /** Записывает фактически выполненный фокус, если пользователь забыл включить таймер. */
    fun addManualFocus(startMillis: Long, durationMinutes: Int) {
        val safeMinutes = durationMinutes.coerceIn(1, MAX_FOCUS_MINUTES.toInt())
        val duration = safeMinutes * MINUTE_MS
        val endMillis = startMillis + duration
        val selectedTaskId = focusItemTaskId(_selectedItemId.value)
        val focus = activeFocus ?: ActiveFocus(
            type = _activityType.value,
            itemId = _selectedItemId.value ?: 0,
            itemName = selectedTaskId?.let { taskId ->
                workTasks.value.firstOrNull { it.id == taskId }
                    ?.primaryLabel()
            } ?: when (_activityType.value) {
                FocusActivityType.STUDY -> subjects.value.firstOrNull { it.id == _selectedItemId.value }?.name.orEmpty()
                FocusActivityType.WORK -> workTasks.value.firstOrNull { it.id == _selectedItemId.value }
                    ?.primaryLabel()
                    .orEmpty()
                FocusActivityType.OTHER -> otherActivities.value.firstOrNull { it.id == _selectedItemId.value }?.name.orEmpty()
            }
        )
        val linkedTaskId = focusItemTaskId(focus.itemId)
        viewModelScope.launch {
            activityRepository.saveManual(
                ManualActivityInput(
                    taskId = linkedTaskId,
                    activityType = focus.type,
                    subjectId = focus.itemId.takeIf {
                        linkedTaskId == null && focus.type == FocusActivityType.STUDY && it != 0
                    },
                    otherActivityId = focus.itemId.takeIf {
                        linkedTaskId == null && focus.type == FocusActivityType.OTHER && it != 0
                    },
                    title = focus.itemName,
                    startedAt = startMillis,
                    endedAt = endMillis
                ),
                com.personal.sleepalarm.data.repository.ActivityConflictStrategy.KEEP_PARALLEL
            )
        }
    }

    fun endBreakToIdle() {
        if (_mode.value == TimerMode.BREAK || _mode.value == TimerMode.BREAK_PAUSED) {
            timerJob?.cancel()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_BREAK_COMPLETE_ID)
            _mode.value = TimerMode.IDLE
            _remaining.value = _focusDuration.value
        }
    }

    fun setTickerSettings(enabled: Boolean, interval: Int, startTime: LocalTime?, endTime: LocalTime?) {
        _tickerEnabled.value = enabled
        _tickerInterval.value = interval
        _tickerStartTime.value = startTime
        _tickerEndTime.value = endTime
        tickerJob?.cancel()
        if (!enabled) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_TICKER_ID)
        }
        if (enabled && startTime != null && endTime != null) {
            tickerJob = viewModelScope.launch {
                while (true) {
                    val now = LocalTime.now()
                    if (now.isAfter(startTime) && now.isBefore(endTime)) {
                        val sinceStart = java.time.Duration.between(startTime, now).toMinutes()
                        if (sinceStart % interval == 0L) {
                            showNotification(
                                notificationId = NOTIFICATION_TICKER_ID,
                                dedupeKey = "pomodoro-ticker-${now.hour}-${now.minute}",
                                title = context.getString(R.string.pomodoro_ticker_title),
                                text = context.getString(R.string.pomodoro_ticker_message, interval)
                            )
                            delay(MINUTE_MS)
                        }
                    }
                    delay(30_000L)
                }
            }
        }
    }

    fun addSubject(name: String, color: Int) = viewModelScope.launch {
        subjectDao.insert(SubjectEntity(name = name.trim(), color = color))
    }

    fun updateSubject(subject: SubjectEntity) = viewModelScope.launch { subjectDao.update(subject) }

    fun deleteSubject(id: Int) = viewModelScope.launch { subjectDao.deleteById(id) }

    fun addWorkTask(name: String) = viewModelScope.launch {
        taskLifecycle.save(
            TaskEntity(title = name.trim(), isMorningRoutine = false, category = "WORK")
        )
    }

    fun updateWorkTask(task: TaskEntity) = viewModelScope.launch {
        taskLifecycle.rename(task.id, task.title)
    }

    fun deleteWorkTask(id: Int) = viewModelScope.launch {
        taskLifecycle.delete(id)
    }

    fun addOtherActivity(name: String, color: Int) = viewModelScope.launch {
        otherActivityDao.insert(OtherActivityEntity(name = name.trim(), color = color))
    }

    fun updateOtherActivity(activity: OtherActivityEntity) =
        viewModelScope.launch { otherActivityDao.update(activity) }

    fun deleteOtherActivity(id: Int) = viewModelScope.launch { otherActivityDao.deleteById(id) }

    companion object {
        const val MIN_FOCUS_MINUTES = 5L
        const val MAX_FOCUS_MINUTES = 180L
        const val MIN_BREAK_MINUTES = 1L
        const val MAX_BREAK_MINUTES = 30L
        private const val MINUTE_MS = 60L * 1000L
        private const val POMODORO_CHANNEL = AppNotificationChannelIds.POMODORO
        private const val NOTIFICATION_FOCUS_COMPLETE_ID = 670_001
        private const val NOTIFICATION_BREAK_COMPLETE_ID = 670_002
        private const val NOTIFICATION_TICKER_ID = 670_003

        fun calculateActivityDayRange(nowMillis: Long): Pair<Long, Long> {
            return ActivityDayBoundary.currentBounds(nowMillis, ZoneId.systemDefault())
        }

        fun dateKeyOf(millis: Long): String = Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
