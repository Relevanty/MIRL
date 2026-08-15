package com.personal.sleepalarm.ui.pomodoro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.ui.MainActivity
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.StudySessionEntity
import com.personal.sleepalarm.data.db.entity.SubjectEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class TimerMode {
    IDLE,
    FOCUS,
    FOCUS_PAUSED,
    BREAK,
    BREAK_PAUSED
}

class PomodoroViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application.applicationContext)
    private val subjectDao = database.subjectDao()
    private val studyDao = database.studySessionDao()
    private val context = application.applicationContext

    val subjects: StateFlow<List<SubjectEntity>> = subjectDao
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // === "Сегодня" считается с 03:00 МСК до 03:00 следующего дня ===
    // Диапазон пересчитывается каждую минуту, чтобы корректно работать
    // при переходе через границу дня (3 часа ночи МСК).
    private val todayRange: kotlinx.coroutines.flow.Flow<Pair<Long, Long>> = flow {
        while (true) {
            emit(calculateTodayRange())
            delay(60_000L)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val todaySessions: StateFlow<List<StudySessionEntity>> = todayRange
        .flatMapLatest { (from, to) -> studyDao.observeInRange(from, to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // === Настройки длительности ===
    private val _focusDuration = MutableStateFlow(25L * 60L * 1000L)
    val focusDuration: StateFlow<Long> = _focusDuration

    private val _breakDuration = MutableStateFlow(5L * 60L * 1000L)
    val breakDuration: StateFlow<Long> = _breakDuration

    // === Настройки уведомлений ===
    private val _notificationSoundUri = MutableStateFlow<Uri?>(null)
    val notificationSoundUri: StateFlow<Uri?> = _notificationSoundUri

    // === Тикалка ===
    private val _tickerEnabled = MutableStateFlow(false)
    val tickerEnabled: StateFlow<Boolean> = _tickerEnabled

    private val _tickerInterval = MutableStateFlow(15)
    val tickerInterval: StateFlow<Int> = _tickerInterval

    private val _tickerStartTime = MutableStateFlow<LocalTime?>(null)
    val tickerStartTime: StateFlow<LocalTime?> = _tickerStartTime

    private val _tickerEndTime = MutableStateFlow<LocalTime?>(null)
    val tickerEndTime: StateFlow<LocalTime?> = _tickerEndTime

    // === Настройка: поведение после завершения отдыха ===
    // true — возврат в исходное положение (IDLE, кнопка «Старт», можно всё настроить)
    // false — пауза «продолжить» (FOCUS_PAUSED)
    private val _resetAfterBreak = MutableStateFlow(true)
    val resetAfterBreak: StateFlow<Boolean> = _resetAfterBreak

    fun setResetAfterBreak(value: Boolean) {
        _resetAfterBreak.value = value
    }
    private var tickerJob: Job? = null

    // === Таймер ===
    private val _remaining = MutableStateFlow(25L * 60L * 1000L)
    val remaining: StateFlow<Long> = _remaining

    private val _mode = MutableStateFlow(TimerMode.IDLE)
    val mode: StateFlow<TimerMode> = _mode

    private val _selectedSubjectId = MutableStateFlow<Int?>(null)
    val selectedSubjectId: StateFlow<Int?> = _selectedSubjectId

    private var timerJob: Job? = null
    private var startMillis = 0L
    private var focusRemainingWhenPaused = 0L

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "pomodoro_channel",
            "Pomodoro Timer",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for Pomodoro timer"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Возвращает временной диапазон "сегодня" с учётом границы 03:00 МСК.
     *
     * Если сейчас до 03:00 МСК — это ещё "вчера" (с 03:00 позавчера до 03:00 вчера).
     * Если сейчас >= 03:00 МСК — это "сегодня" (с 03:00 вчера до 03:00 сегодня).
     */
    private fun calculateTodayRange(): Pair<Long, Long> {
        val zone = ZoneId.of("Europe/Moscow")
        val now = ZonedDateTime.now(zone)
        val todayStart = if (now.hour < 3) {
            now.toLocalDate().minusDays(1).atTime(3, 0).atZone(zone)
        } else {
            now.toLocalDate().atTime(3, 0).atZone(zone)
        }
        val todayEnd = todayStart.plusDays(1)
        return todayStart.toInstant().toEpochMilli() to todayEnd.toInstant().toEpochMilli()
    }

    // === Звук уведомлений ===

    fun setNotificationSound(uri: Uri?) {
        _notificationSoundUri.value = uri
    }

    fun resetNotificationSound() {
        _notificationSoundUri.value = null
    }

    private fun showNotification(title: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = _notificationSoundUri.value
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, "pomodoro_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // === Таймер ===

    fun start(subjectId: Int) {
        if (_mode.value == TimerMode.FOCUS || _mode.value == TimerMode.BREAK) return
        _selectedSubjectId.value = subjectId
        startMillis = System.currentTimeMillis()
        _remaining.value = _focusDuration.value
        _mode.value = TimerMode.FOCUS

        timerJob = viewModelScope.launch {
            while (_remaining.value > 0L &&
                (_mode.value == TimerMode.FOCUS || _mode.value == TimerMode.BREAK)) {
                delay(1_000L)
                _remaining.value -= 1_000L
            }
            when (_mode.value) {
                TimerMode.FOCUS -> onFocusCompleted()
                TimerMode.BREAK -> onBreakCompleted()
                else -> {}
            }
        }
    }

    private fun startBreak() {
        _remaining.value = _breakDuration.value
        _mode.value = TimerMode.BREAK

        timerJob = viewModelScope.launch {
            while (_remaining.value > 0L && _mode.value == TimerMode.BREAK) {
                delay(1_000L)
                _remaining.value -= 1_000L
            }
            if (_remaining.value <= 0L) {
                onBreakCompleted()
            }
        }
    }

    private fun onFocusCompleted() {
        finishFocusSession()
        focusRemainingWhenPaused = _focusDuration.value
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
        if (_resetAfterBreak.value) {
            _mode.value = TimerMode.IDLE          // исходное положение
        } else {
            _mode.value = TimerMode.FOCUS_PAUSED  // «продолжить»
        }
        _remaining.value = _focusDuration.value
    }

    fun toggle() {
        when (_mode.value) {
            TimerMode.IDLE -> {
                val id = _selectedSubjectId.value ?: subjects.value.firstOrNull()?.id ?: return
                start(id)
            }
            TimerMode.FOCUS -> {
                timerJob?.cancel()
                focusRemainingWhenPaused = _remaining.value
                finishFocusSession()
                startBreak()
            }
            TimerMode.FOCUS_PAUSED -> {
                _mode.value = TimerMode.FOCUS
                timerJob = viewModelScope.launch {
                    while (_remaining.value > 0L && _mode.value == TimerMode.FOCUS) {
                        delay(1_000L)
                        _remaining.value -= 1_000L
                    }
                    if (_remaining.value <= 0L) {
                        onFocusCompleted()
                    }
                }
            }
            TimerMode.BREAK -> {
                timerJob?.cancel()
                // Пропуск отдыха — возврат в исходное положение
                _mode.value = TimerMode.IDLE
                _remaining.value = _focusDuration.value
            }
            TimerMode.BREAK_PAUSED -> {
                _mode.value = TimerMode.BREAK
                timerJob = viewModelScope.launch {
                    while (_remaining.value > 0L && _mode.value == TimerMode.BREAK) {
                        delay(1_000L)
                        _remaining.value -= 1_000L
                    }
                    if (_remaining.value <= 0L) {
                        onBreakCompleted()
                    }
                }
            }
        }
    }

    fun stop() {
        timerJob?.cancel()
        when (_mode.value) {
            TimerMode.FOCUS -> {
                focusRemainingWhenPaused = _remaining.value
                finishFocusSession()
                startBreak()
            }
            TimerMode.FOCUS_PAUSED -> {
                startBreak()
            }
            TimerMode.BREAK -> {
                // Пропуск отдыха — возврат в исходное положение
                _mode.value = TimerMode.IDLE
                _remaining.value = _focusDuration.value
            }
            TimerMode.BREAK_PAUSED -> {
                _mode.value = TimerMode.IDLE
                _remaining.value = _focusDuration.value
            }
            TimerMode.IDLE -> {}
        }
    }

    private fun finishFocusSession() {
        val subjectId = _selectedSubjectId.value ?: return
        val end = System.currentTimeMillis()
        val duration = end - startMillis

        if (duration >= 1_000L) {
            viewModelScope.launch {
                studyDao.insert(
                    StudySessionEntity(
                        subjectId = subjectId,
                        startMillis = startMillis,
                        endMillis = end,
                        durationMillis = duration,
                        dateKey = dateKeyOf(startMillis)
                    )
                )
            }
        }
    }

    private fun finishBreak() {
        _mode.value = TimerMode.FOCUS_PAUSED
        _remaining.value = focusRemainingWhenPaused
    }

    fun setFocusDuration(minutes: Long) {
        val m = minutes.coerceIn(MIN_FOCUS_MINUTES, MAX_FOCUS_MINUTES)
        _focusDuration.value = m * 60L * 1000L
        if (_mode.value == TimerMode.IDLE) {
            _remaining.value = _focusDuration.value
        }
        if (_mode.value == TimerMode.FOCUS_PAUSED) {
            focusRemainingWhenPaused = _focusDuration.value
            _remaining.value = _focusDuration.value
        }
    }

    fun setBreakDuration(minutes: Long) {
        _breakDuration.value =
            minutes.coerceIn(MIN_BREAK_MINUTES, MAX_BREAK_MINUTES) * 60L * 1000L
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
                        val minutesSinceStart = java.time.Duration.between(startTime, now).toMinutes()
                        if (minutesSinceStart % interval == 0L) {
                            showNotification(
                                context.getString(R.string.pomodoro_ticker_title),
                                context.getString(R.string.pomodoro_ticker_message, interval)
                            )
                            delay(60_000L)
                        }
                    }
                    delay(30_000L)
                }
            }
        }
    }

    fun addSubject(name: String, color: Int) {
        viewModelScope.launch {
            subjectDao.insert(SubjectEntity(name = name.trim(), color = color))
        }
    }

    fun updateSubject(subject: SubjectEntity) {
        viewModelScope.launch { subjectDao.update(subject) }
    }

    fun deleteSubject(id: Int) {
        viewModelScope.launch { subjectDao.deleteById(id) }
    }

    companion object {
        const val MIN_FOCUS_MINUTES = 5L
        const val MAX_FOCUS_MINUTES = 180L   // до 3 часов
        const val MIN_BREAK_MINUTES = 1L
        const val MAX_BREAK_MINUTES = 30L   // до 3 часов
        fun dateKeyOf(millis: Long): String =
            Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
