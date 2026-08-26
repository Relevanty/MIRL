package com.personal.sleepalarm.ui.pomodoro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.OtherActivityEntity
import com.personal.sleepalarm.data.db.entity.PomodoroSessionEntity
import com.personal.sleepalarm.data.db.entity.StudySessionEntity
import com.personal.sleepalarm.data.db.entity.SubjectEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.preferences.PomodoroSoundPreference
import com.personal.sleepalarm.data.repository.TaskRepository
import com.personal.sleepalarm.data.repository.ActivityRecordRepository
import com.personal.sleepalarm.data.repository.ManualActivityInput
import com.personal.sleepalarm.domain.model.FocusActivityType
import com.personal.sleepalarm.domain.calculator.ActivityDayBoundary
import com.personal.sleepalarm.service.audio.AppNotificationSoundPlayer
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
    private val taskRepository = TaskRepository(taskDao)
    private val otherActivityDao = database.otherActivityDao()
    private val studyDao = database.studySessionDao()
    private val pomodoroDao = database.pomodoroDao()
    private val activityRepository = ActivityRecordRepository(database)
    private val context = application.applicationContext
    private val soundPreference = PomodoroSoundPreference(context)

    val subjects: StateFlow<List<SubjectEntity>> = subjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workTasks: StateFlow<List<TaskEntity>> = taskDao.observeByRoutineFlag(false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val otherActivities: StateFlow<List<OtherActivityEntity>> = otherActivityDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private val _notificationSoundUri = MutableStateFlow<Uri?>(null)
    val notificationSoundUri: StateFlow<Uri?> = _notificationSoundUri

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
            soundPreference.observeUri().collect { _notificationSoundUri.value = it }
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

    /** Подготавливает Pomodoro из карточки задачи, не запуская таймер без подтверждения. */
    fun prepareWorkTask(task: TaskEntity) {
        if (_mode.value != TimerMode.IDLE) return
        selectActivityType(FocusActivityType.WORK)
        selectItem(task.id)
        setFocusDuration(task.plannedFocusMinutes.takeIf { it > 0 }?.toLong() ?: task.estimatedMinutes.toLong())
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
            enableVibration(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun setNotificationSound(uri: Uri?) {
        _notificationSoundUri.value = uri
        viewModelScope.launch { soundPreference.setUri(uri) }
    }

    fun resetNotificationSound() {
        _notificationSoundUri.value = null
        viewModelScope.launch { soundPreference.setUri(null) }
    }

    private fun showNotification(title: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(System.currentTimeMillis().toInt(), notification) }
            .onSuccess {
                viewModelScope.launch {
                    AppNotificationSoundPlayer.play(
                        context = context,
                        soundUri = _notificationSoundUri.value
                    )
                }
            }
    }

    fun start(itemId: Int, itemName: String) {
        if (_mode.value == TimerMode.FOCUS || _mode.value == TimerMode.BREAK ||
            _mode.value == TimerMode.BREAK_PAUSED
        ) return

        val type = _activityType.value
        selectedIds[type] = itemId
        _selectedItemId.value = itemId
        activeFocus = ActiveFocus(type, itemId, itemName)
        beginFocus(_focusDuration.value)
    }

    private fun beginFocus(duration: Long) {
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
            context.getString(R.string.pomodoro_focus_complete_title),
            context.getString(R.string.pomodoro_focus_complete_message)
        )
        startBreak()
    }

    private fun onBreakCompleted() {
        showNotification(
            context.getString(R.string.pomodoro_break_complete_title),
            context.getString(R.string.pomodoro_break_complete_message)
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

        val session = PomodoroSessionEntity(
            startedAt = startMillis,
            durationMinutes = ((duration + MINUTE_MS - 1L) / MINUTE_MS).toInt(),
            completedAt = end,
            isCompleted = completed,
            isBreak = false,
            activityType = focus.type,
            subjectId = focus.itemId.takeIf { focus.type == FocusActivityType.STUDY },
            taskId = focus.itemId.takeIf { focus.type == FocusActivityType.WORK },
            otherActivityId = focus.itemId.takeIf { focus.type == FocusActivityType.OTHER },
            itemName = focus.itemName,
            actualDurationMillis = duration,
            recordSource = "TIMER"
        )

        val recordedStart = startMillis
        viewModelScope.launch {
            val pomodoroId = pomodoroDao.insert(session).toInt()
            activityRepository.recordTimer(session, pomodoroId)
            if (focus.type == FocusActivityType.STUDY) {
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
        val focus = activeFocus ?: ActiveFocus(
            type = _activityType.value,
            itemId = _selectedItemId.value ?: 0,
            itemName = when (_activityType.value) {
                FocusActivityType.STUDY -> subjects.value.firstOrNull { it.id == _selectedItemId.value }?.name.orEmpty()
                FocusActivityType.WORK -> workTasks.value.firstOrNull { it.id == _selectedItemId.value }
                    ?.let { it.title.ifBlank { it.description.ifBlank { it.nextAction.ifBlank { "Задача #${it.id}" } } } }
                    .orEmpty()
                FocusActivityType.OTHER -> otherActivities.value.firstOrNull { it.id == _selectedItemId.value }?.name.orEmpty()
            }
        )
        viewModelScope.launch {
            activityRepository.saveManual(
                ManualActivityInput(
                    taskId = focus.itemId.takeIf { focus.type == FocusActivityType.WORK && it != 0 },
                    activityType = focus.type,
                    subjectId = focus.itemId.takeIf { focus.type == FocusActivityType.STUDY && it != 0 },
                    otherActivityId = focus.itemId.takeIf { focus.type == FocusActivityType.OTHER && it != 0 },
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
        if (enabled && startTime != null && endTime != null) {
            tickerJob = viewModelScope.launch {
                while (true) {
                    val now = LocalTime.now()
                    if (now.isAfter(startTime) && now.isBefore(endTime)) {
                        val sinceStart = java.time.Duration.between(startTime, now).toMinutes()
                        if (sinceStart % interval == 0L) {
                            showNotification(
                                context.getString(R.string.pomodoro_ticker_title),
                                context.getString(R.string.pomodoro_ticker_message, interval)
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
        taskDao.insert(TaskEntity(title = name.trim(), isMorningRoutine = false))
    }

    fun updateWorkTask(task: TaskEntity) = viewModelScope.launch { taskDao.update(task) }

    fun deleteWorkTask(id: Int) = viewModelScope.launch { taskDao.deleteById(id) }

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
        private const val POMODORO_CHANNEL = "pomodoro_channel_app_volume_v3"

        fun calculateActivityDayRange(nowMillis: Long): Pair<Long, Long> {
            return ActivityDayBoundary.currentBounds(nowMillis, ZoneId.systemDefault())
        }

        fun dateKeyOf(millis: Long): String = Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
