package com.personal.sleepalarm.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.personal.sleepalarm.alarm.AlarmScheduler
import com.personal.sleepalarm.alarm.PendingIntentFactory
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.repository.SleepProfileRepository
import com.personal.sleepalarm.data.repository.SleepSessionRepository
import com.personal.sleepalarm.domain.model.DismissType
import com.personal.sleepalarm.service.audio.AlarmSoundPlayer
import com.personal.sleepalarm.service.audio.AlarmVibrator
import com.personal.sleepalarm.service.audio.CueSoundPlayer
import com.personal.sleepalarm.service.audio.VibrationPattern
import com.personal.sleepalarm.ui.AlarmActivity
import com.personal.sleepalarm.util.IntentExtras
import com.personal.sleepalarm.util.PermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Foreground service активной сессии сна.
 *
 * ДОБАВЛЕНО (F9): SleepSensorTracker — автоопределение засыпания.
 * - старт/стоп трекера управляется из observeProfileChanges через
 *   manageSensorTracker (применяется немедленно при смене тумблера);
 * - при детекции: updateDetectedOnset в БД;
 * - если autoCorrectWakeEnabled и будильник ещё не корректировали —
 *   пересчёт estimatedWakeTime + rescheduleAllForSession (один раз).
 *
 * Основной звук и вибрация также живут в сервисе, поэтому сигнал продолжает
 * работать, даже если прошивка не открыла полноэкранный Activity.
 */
class SleepForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var sessionRepository: SleepSessionRepository
    private lateinit var profileRepository: SleepProfileRepository
    private lateinit var notificationBuilder: SleepNotificationBuilder
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var pendingIntentFactory: PendingIntentFactory
    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmSoundPlayer: AlarmSoundPlayer
    private lateinit var alarmVibrator: AlarmVibrator

    private val audioMutex = Mutex()

    private var wakeLock: PowerManager.WakeLock? = null

    private var activeSessionId: Int? = null
    private var sessionJob: Job? = null
    private var profileJob: Job? = null
    private var alarmStartJob: Job? = null

    private var alarmTriggeredForCurrentSession = false

    // === ДОБАВЛЕНО: F9 — автоопределение засыпания ===
    private var sensorTracker: SleepSensorTracker? = null

    /** Уже зафиксировали засыпание в этой сессии (или оно уже есть в БД). */
    private var detectedOnsetHandled = false

    /** Уже пересчитали будильник по детекции (защита от повтора). */
    private var wakeCorrected = false

    override fun onCreate() {
        super.onCreate()

        val database = AppDatabase.getInstance(applicationContext)

        sessionRepository = SleepSessionRepository(
            database = database,
            sessionDao = database.sleepSessionDao(),
            cueEventDao = database.cueEventDao()
        )

        profileRepository = SleepProfileRepository(
            database.alarmProfileDao()
        )

        notificationBuilder = SleepNotificationBuilder(applicationContext).also {
            it.createNotificationChannels()
        }

        alarmScheduler = AlarmScheduler.create(
            context = applicationContext,
            sessionRepository = sessionRepository
        )

        pendingIntentFactory = PendingIntentFactory(applicationContext)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmSoundPlayer = AlarmSoundPlayer(applicationContext)
        alarmVibrator = AlarmVibrator(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        releaseRuntimeResources()
        alarmSoundPlayer.close()
        alarmVibrator.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_TRIGGER_ALARM -> {
                val sessionId = intent.getIntExtra(IntentExtras.EXTRA_SESSION_ID, -1)
                if (sessionId < 0) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                handleAlarmTrigger(sessionId)
                return START_REDELIVER_INTENT
            }

            ACTION_SET_ALARM_VOLUME -> {
                alarmSoundPlayer.setVolumeFraction(
                    intent.getFloatExtra(EXTRA_ALARM_VOLUME, 1f)
                )
                return START_NOT_STICKY
            }

            ACTION_PULSE_ALARM_VIBRATION -> {
                alarmVibrator.start(VibrationPattern.REPEAT_BURST)
                return START_NOT_STICKY
            }

            ACTION_STOP_ALARM_VIBRATION -> {
                alarmVibrator.cancel()
                return START_NOT_STICKY
            }

            ACTION_STOP_AND_CANCEL -> {
                handleStopAndCancel()
                return START_NOT_STICKY
            }

            ACTION_STOP_ONLY -> {
                cleanupAndStop()
                return START_NOT_STICKY
            }
        }

        // id сессии берём ТОЛЬКО из Intent или из памяти.
        // НЕ читаем БД синхронно (это и был краш).
        val sessionIdFromIntent = intent?.getIntExtra(IntentExtras.EXTRA_SESSION_ID, -1)
            ?.takeIf { it >= 0 }

        val previousSessionId = activeSessionId
        val sessionId = sessionIdFromIntent ?: previousSessionId

        if (sessionId == null) {
            Log.w(TAG, "No session id for SleepForegroundService, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        if (previousSessionId != sessionId) {
            detectedOnsetHandled = false
            wakeCorrected = false
            stopSensorTracker()
            alarmSoundPlayer.stop()
            alarmVibrator.cancel()
        }
        activeSessionId = sessionId

        acquireWakeLockIfNeeded()

        // Время подъёма и первого пиипа — ИЗ INTENT, без БД.
        val wakeTimeFromIntent = intent?.getLongExtra(EXTRA_WAKE_TIME, -1L)
            ?.takeIf { it > 0 }
        val firstCueTimeFromIntent = intent?.getLongExtra(EXTRA_FIRST_CUE_TIME, -1L)
            ?.takeIf { it > 0 }

        val initialNotification = if (wakeTimeFromIntent != null) {
            notificationBuilder.buildStartConfirmationNotification(
                wakeTime = wakeTimeFromIntent,
                firstCueTime = firstCueTimeFromIntent
            )
        } else {
            notificationBuilder.buildPlaceholderNotification()
        }

        startForegroundCompat(initialNotification)

        // Всплывающее heads-up подтверждение установки будильника.
        if (wakeTimeFromIntent != null) {
            val setConfirmation = notificationBuilder.buildAlarmSetNotification(
                wakeTime = wakeTimeFromIntent,
                firstCueTime = firstCueTimeFromIntent
            )
            runCatching {
                notificationManager.notify(
                    SleepNotificationBuilder.ALARM_SET_NOTIFICATION_ID,
                    setConfirmation
                )
            }.onFailure {
                Log.e(TAG, "Failed to show alarm-set confirmation", it)
            }
        }

        if (sessionJob?.isActive == true && previousSessionId == sessionId) {
            return START_REDELIVER_INTENT
        }

        alarmTriggeredForCurrentSession = false
        startSessionLoop(sessionId)
        observeProfileChanges(sessionId)

        return START_REDELIVER_INTENT
    }

    // =================================================================
    // Цикл сессии
    // =================================================================

    private fun startSessionLoop(sessionId: Int) {
        sessionJob?.cancel()

        sessionJob = serviceScope.launch(Dispatchers.Default) {
            var lastNotificationUpdate = 0L

            withContext(Dispatchers.IO) {
                sessionRepository.recoverInterruptedCuePlaybacks(sessionId)
            }

            while (isActive) {
                val session = withContext(Dispatchers.IO) {
                    sessionRepository.getSession(sessionId)
                }

                if (session == null || !session.isActive) {
                    Log.i(TAG, "Session is null or inactive, stopping service")
                    cleanupAndStop()
                    break
                }

                val now = System.currentTimeMillis()

                if (now >= session.estimatedWakeTime) {
                    if (!alarmTriggeredForCurrentSession) {
                        alarmTriggeredForCurrentSession = true
                        triggerAlarmFallback(session)
                    }

                    if (now >= session.estimatedWakeTime + MISSED_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) {
                            alarmScheduler.cancelAllAlarmsForSession(session.id)
                            sessionRepository.finishSession(
                                sessionId = session.id,
                                actualWakeTime = now,
                                dismissType = DismissType.MISSED
                            )
                        }
                        cleanupAndStop()
                        break
                    }
                }

                var nextCueTime: Long? = null

                if (session.cuesEnabled && now < session.estimatedWakeTime) {
                    val finalCycleStart = session.estimatedWakeTime -
                            session.cycleLengthMinutes * MINUTE_MS

                    val scheduledCues = withContext(Dispatchers.IO) {
                        sessionRepository.getScheduledCues(session.id)
                    }

                    val dueCues = scheduledCues.filter { it.scheduledTime <= now }

                    dueCues.forEach { cue ->
                        val insideAllowedWindow = cue.scheduledTime < finalCycleStart
                        val notTooLate = cue.scheduledTime >= now - MAX_CUE_LATENESS_MS

                        if (insideAllowedWindow && notTooLate) {
                            playCue(session, cue.cueIndex)
                        } else {
                            withContext(Dispatchers.IO) {
                                sessionRepository.markCueSkipped(
                                    sessionId = session.id,
                                    cueIndex = cue.cueIndex
                                )
                            }
                        }
                    }

                    nextCueTime = scheduledCues
                        .firstOrNull { cue ->
                            cue.scheduledTime > now && cue.scheduledTime < finalCycleStart
                        }
                        ?.scheduledTime
                }

                if (now - lastNotificationUpdate >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                    updateNotification(
                        session = session,
                        nextCueTime = nextCueTime
                    )
                    lastNotificationUpdate = now
                }

                delay(LOOP_INTERVAL_MS)
            }
        }
    }

    private fun updateNotification(
        session: SleepSessionEntity,
        nextCueTime: Long?
    ) {
        val now = System.currentTimeMillis()
        val currentCycle = currentCycle(session, now)

        val notification = notificationBuilder.buildSleepNotification(
            session = session,
            nextCueTime = nextCueTime,
            currentCycle = currentCycle
        )

        runCatching {
            notificationManager.notify(
                SleepNotificationBuilder.SLEEP_NOTIFICATION_ID,
                notification
            )
        }.onFailure {
            Log.e(TAG, "Failed to update sleep notification", it)
        }
    }

    private fun currentCycle(
        session: SleepSessionEntity,
        now: Long
    ): Int? {
        if (now < session.estimatedSleepStartTime) {
            return null
        }

        if (now > session.estimatedWakeTime) {
            return session.cyclesPlanned
        }

        if (session.cycleLengthMinutes <= 0) {
            return null
        }

        val minutesFromSleepStart =
            (now - session.estimatedSleepStartTime) / MINUTE_MS

        val cycle = (minutesFromSleepStart / session.cycleLengthMinutes).toInt() + 1

        return cycle.coerceIn(1, session.cyclesPlanned)
    }

    // =================================================================
    // Alarm fallback
    // =================================================================

    /**
     * Точка входа от AlarmReceiver. Сначала немедленно переводит сервис в
     * foreground, затем проверяет сессию и запускает звук независимо от того,
     * смог ли HyperOS открыть полноэкранный Activity.
     */
    private fun handleAlarmTrigger(sessionId: Int) {
        val previousSessionId = activeSessionId
        if (activeSessionId == sessionId &&
            alarmTriggeredForCurrentSession &&
            (alarmStartJob?.isActive == true || alarmSoundPlayer.isPlaying())
        ) {
            return
        }

        if (previousSessionId != sessionId) {
            detectedOnsetHandled = false
            wakeCorrected = false
            stopSensorTracker()
            alarmSoundPlayer.stop()
            alarmVibrator.cancel()
        }
        activeSessionId = sessionId
        acquireWakeLockIfNeeded()

        // startForegroundService требует вызвать startForeground без ожидания
        // Room. После проверки заменим эту заглушку тревожным уведомлением.
        startForegroundCompat(notificationBuilder.buildPlaceholderNotification())

        alarmStartJob?.cancel()
        alarmStartJob = serviceScope.launch {
            val session = withContext(Dispatchers.IO) {
                sessionRepository.getSession(sessionId)
            }
            val now = System.currentTimeMillis()
            if (session == null ||
                !session.isActive ||
                session.estimatedWakeTime > now + ALARM_EARLY_TOLERANCE_MS ||
                now > session.estimatedWakeTime + MISSED_TIMEOUT_MS
            ) {
                Log.w(TAG, "Ignoring invalid alarm trigger for sessionId=$sessionId")
                cleanupAndStop()
                return@launch
            }

            alarmTriggeredForCurrentSession = true
            showAlarmPresentation(session.id)
            startAlarmPlayback()

            if (sessionJob?.isActive != true) {
                startSessionLoop(session.id)
                observeProfileChanges(session.id)
            }
        }
    }

    private fun triggerAlarmFallback(session: SleepSessionEntity) {
        Log.i(TAG, "Triggering alarm fallback for sessionId=${session.id}")

        showAlarmPresentation(session.id)
        alarmStartJob?.cancel()
        alarmStartJob = serviceScope.launch { startAlarmPlayback() }
    }

    private fun showAlarmPresentation(sessionId: Int) {

        val fullScreenIntent = pendingIntentFactory.mainAlarmShowPendingIntentForTrigger(sessionId)

        val alarmNotification = notificationBuilder.buildAlarmNotification(
            sessionId = sessionId,
            fullScreenIntent = fullScreenIntent
        )

        runCatching {
            startForegroundCompat(alarmNotification)
        }.onFailure {
            Log.e(TAG, "Failed to promote alarm foreground notification", it)
        }

        // Отдельный новый notification ID важен: некоторые прошивки не
        // запускают fullScreenIntent при обновлении уже существующего FGS.
        runCatching {
            notificationManager.notify(
                SleepNotificationBuilder.ALARM_NOTIFICATION_ID,
                alarmNotification
            )
        }.onFailure {
            Log.e(TAG, "Failed to post full-screen alarm notification", it)
        }

        if (PermissionChecker.shouldLaunchAlarmDirectly(applicationContext)) {
            startAlarmActivity(sessionId)
        }
    }

    private suspend fun startAlarmPlayback() {
        val profile = withContext(Dispatchers.IO) { profileRepository.getProfile() }
        val customUri = profile.alarmRingtoneUri?.let { raw ->
            runCatching { android.net.Uri.parse(raw) }.getOrNull()
        }

        alarmSoundPlayer.startSuspend(
            quietMode = profile.quietAlarmEnabled,
            customRingtoneUri = customUri
        )
        if (profile.vibrationEnabled) {
            alarmVibrator.start(VibrationPattern.ALARM_RAMP)
        } else {
            alarmVibrator.cancel()
        }
    }

    private fun startAlarmActivity(sessionId: Int) {
        runCatching {
            val activityIntent = Intent(applicationContext, AlarmActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra(IntentExtras.EXTRA_SESSION_ID, sessionId)
            }

            applicationContext.startActivity(activityIntent)
        }.onFailure {
            Log.e(TAG, "Failed to start AlarmActivity from service", it)
        }
    }

    // =================================================================
    // Профиль: немедленное применение cues + управление сенсором (F9)
    // =================================================================

    private fun observeProfileChanges(sessionId: Int) {
        profileJob?.cancel()

        profileJob = serviceScope.launch {
            profileRepository.observeProfile().collect { profile ->
                withContext(Dispatchers.IO) {
                    applyCuesEnabledChange(
                        sessionId = sessionId,
                        cuesEnabled = profile.cuesEnabled
                    )
                }

                // ДОБАВЛЕНО: F9 — старт/стоп трекера по тумблеру.
                manageSensorTracker(
                    sessionId = sessionId,
                    autoDetectEnabled = profile.autoDetectOnsetEnabled,
                    autoCorrectEnabled = profile.autoCorrectWakeEnabled,
                    autoCorrectMinConfidence = profile.autoCorrectMinConfidencePercent,
                    autoCorrectMaxShiftMinutes = profile.autoCorrectMaxShiftMinutes,
                    hardWakeHour = profile.preferredWakeHour,
                    hardWakeMinute = profile.preferredWakeMinute
                )
            }
        }
    }

    private suspend fun applyCuesEnabledChange(
        sessionId: Int,
        cuesEnabled: Boolean
    ) {
        val session = sessionRepository.getSession(sessionId) ?: return

        if (!session.isActive) {
            return
        }

        if (session.cuesEnabled == cuesEnabled) {
            return
        }

        val updatedSession = session.copy(cuesEnabled = cuesEnabled)
        sessionRepository.updateSession(updatedSession)

        alarmScheduler.cancelAllAlarmsForSession(sessionId)
        alarmScheduler.scheduleMainAlarm(updatedSession)

        val now = System.currentTimeMillis()
        val finalCycleStart = updatedSession.estimatedWakeTime -
                updatedSession.cycleLengthMinutes * MINUTE_MS

        if (cuesEnabled) {
            val scheduledCues = sessionRepository.getScheduledCues(sessionId)

            scheduledCues
                .filter { cue ->
                    cue.scheduledTime <= now || cue.scheduledTime >= finalCycleStart
                }
                .forEach { cue ->
                    sessionRepository.markCueSkipped(
                        sessionId = sessionId,
                        cueIndex = cue.cueIndex
                    )
                }

            val futureCues = sessionRepository.getScheduledCues(sessionId)
                .filter { cue ->
                    cue.scheduledTime > now && cue.scheduledTime < finalCycleStart
                }

            alarmScheduler.scheduleCueAlarms(
                sessionId = sessionId,
                cues = futureCues
            )
        }
    }

    // =================================================================
    // ДОБАВЛЕНО: F9 — управление SleepSensorTracker
    // =================================================================

    /**
     * Решает, запускать или останавливать трекер засыпания.
     *
     * Запускаем, если:
     * - тумблер autoDetectOnsetEnabled включён;
     * - сессия активна;
     * - засыпание ещё не зафиксировано (в т.ч. не восстановлено из БД);
     * - с момента bedTime прошло не больше MAX_DETECT_WINDOW_MS
     *   (иначе после reboot/позднего старта неподвижность «сейчас»
     *   не равна засыпанию).
     */
    private suspend fun manageSensorTracker(
        sessionId: Int,
        autoDetectEnabled: Boolean,
        autoCorrectEnabled: Boolean,
        autoCorrectMinConfidence: Int,
        autoCorrectMaxShiftMinutes: Int,
        hardWakeHour: Int,
        hardWakeMinute: Int
    ) {
        val session = withContext(Dispatchers.IO) {
            sessionRepository.getSession(sessionId)
        } ?: return

        // Если засыпание уже есть в БД (например после reboot) — не детектим заново.
        if (session.detectedSleepOnsetTime != null) {
            detectedOnsetHandled = true
        }

        val now = System.currentTimeMillis()
        val withinWindow = (now - session.bedTimePlanned) < MAX_DETECT_WINDOW_MS

        val shouldRun = autoDetectEnabled &&
                session.isActive &&
                !detectedOnsetHandled &&
                withinWindow

        if (shouldRun && sensorTracker == null) {
            startSensorTracker(
                session,
                autoCorrectEnabled,
                autoCorrectMinConfidence,
                autoCorrectMaxShiftMinutes,
                hardWakeHour,
                hardWakeMinute
            )
        } else if (!shouldRun) {
            stopSensorTracker()
        }
    }

    private suspend fun startSensorTracker(
        session: SleepSessionEntity,
        autoCorrectEnabled: Boolean,
        autoCorrectMinConfidence: Int,
        autoCorrectMaxShiftMinutes: Int,
        hardWakeHour: Int,
        hardWakeMinute: Int
    ) {
        val tracker = SleepSensorTracker(applicationContext)
        sensorTracker = tracker

        val expectedLatency = withContext(Dispatchers.IO) {
            sessionRepository.getTypicalConfirmedOnsetLatencyMinutes()
        }
        tracker.start(session.bedTimePlanned, expectedLatency) { onsetMs, latencyMin, confidencePercent ->
            // onDetected приходит на main-потоке (слушатель без Handler).
            // Переключаемся на IO для записи в БД.
            serviceScope.launch(Dispatchers.IO) {
                runCatching {
                    sessionRepository.updateDetectedOnset(
                        sessionId = session.id,
                        onsetTime = onsetMs,
                        latencyMinutes = latencyMin,
                        confidencePercent = confidencePercent,
                        source = "PHONE_CONTEXT_HEURISTIC",
                        uncertaintyMinutes = ((100 - confidencePercent) / 3).coerceIn(5, 20)
                    )
                }.onFailure {
                    Log.e(TAG, "Failed to persist detected onset", it)
                }

                detectedOnsetHandled = true
                stopSensorTracker()

                // Опциональная автокоррекция будильника — только с явного согласия
                // и только один раз за сессию.
                if (autoCorrectEnabled && !wakeCorrected && confidencePercent >= autoCorrectMinConfidence) {
                    correctWakeByDetectedOnset(
                        session.id,
                        onsetMs,
                        autoCorrectMaxShiftMinutes,
                        hardWakeHour,
                        hardWakeMinute
                    )
                } else if (autoCorrectEnabled && confidencePercent < autoCorrectMinConfidence) {
                    Log.i(TAG, "Auto-correct skipped: confidence=$confidencePercent < $autoCorrectMinConfidence")
                }
            }
        }

        Log.i(
            TAG,
            "Sensor tracker armed for sessionId=${session.id}, autoCorrect=$autoCorrectEnabled"
        )
    }

    /**
     * Пересчитывает estimatedWakeTime по детектированному засыпанию
     * и переставляет будильник + cues. Выполняется ОДИН раз.
     */
    private suspend fun correctWakeByDetectedOnset(
        sessionId: Int,
        onsetMs: Long,
        maxShiftMinutes: Int,
        hardWakeHour: Int,
        hardWakeMinute: Int
    ) {
        val fresh = sessionRepository.getSession(sessionId) ?: return
        if (!fresh.isActive) return

        val rawWake = onsetMs +
                fresh.cyclesPlanned.toLong() * fresh.cycleLengthMinutes * MINUTE_MS

        val maxShift = maxShiftMinutes.coerceIn(0, 120) * MINUTE_MS
        val boundedByShift = rawWake.coerceIn(
            fresh.estimatedWakeTime - maxShift,
            fresh.estimatedWakeTime + maxShift
        )
        val zone = ZoneId.systemDefault()
        val bedDate = Instant.ofEpochMilli(fresh.bedTimePlanned).atZone(zone).toLocalDate()
        var hardWake = bedDate.atTime(
            LocalTime.of(hardWakeHour.coerceIn(0, 23), hardWakeMinute.coerceIn(0, 59))
        ).atZone(zone)
        if (hardWake.toInstant().toEpochMilli() <= fresh.bedTimePlanned) hardWake = hardWake.plusDays(1)
        val newWake = minOf(boundedByShift, hardWake.toInstant().toEpochMilli())

        // Не двигаем будильник в прошлое.
        if (newWake <= System.currentTimeMillis()) {
            Log.i(TAG, "Auto-correct skipped: newWake in past")
            return
        }

        val updated = fresh.copy(
            estimatedSleepStartTime = onsetMs,
            estimatedWakeTime = newWake
        )

        sessionRepository.updateSession(updated)
        alarmScheduler.rescheduleAllForSession(updated)
        wakeCorrected = true

        Log.i(TAG, "Wake auto-corrected to $newWake for sessionId=$sessionId")
    }

    private fun stopSensorTracker() {
        sensorTracker?.stop()
        sensorTracker = null
    }

    // =================================================================
    // Audio
    // =================================================================

    /**
     * Проигрывает cue ТОЛЬКО выбранным пользователем звуком.
     *
     * Возвращает true, если cue реально сыграл (тогда сервис пометит PLAYED),
     * и false, если звук не выбран или недоступен (сервис пометит SKIPPED).
     *
     * ДОБАВЛЕНО: убрано ветвление по cueType (beep/binaural/tts больше нет).
     */
    private suspend fun playCue(session: SleepSessionEntity, cueIndex: Int): Boolean {
        return withContext(Dispatchers.IO) {
            audioMutex.withLock {
                val claimed = sessionRepository.claimCuePlayback(
                    sessionId = session.id,
                    cueIndex = cueIndex,
                    playedBy = PLAYED_BY_SERVICE
                )
                if (!claimed) return@withLock false

                val volumeFraction = session.cueVolumePercent / 100f
                val cueRingtone = session.cueRingtoneUri
                val played = cueRingtone != null && CueSoundPlayer.play(
                    context = applicationContext,
                    uriString = cueRingtone,
                    volumeFraction = volumeFraction,
                    maxPlayMs = MAX_CUE_PLAY_MS
                )
                sessionRepository.completeCuePlayback(
                    sessionId = session.id,
                    cueIndex = cueIndex,
                    played = played
                )
                played
            }
        }
    }

    // =================================================================
    // Остановка
    // =================================================================

    private fun handleStopAndCancel() {
        Log.i(TAG, "Stop and cancel session requested")

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val session = sessionRepository.getActiveSession()

                if (session != null) {
                    alarmScheduler.cancelAllAlarmsForSession(session.id)
                    sessionRepository.cancelSession(session.id)
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Error while cancelling session", throwable)
            } finally {
                withContext(NonCancellable) {
                    cleanupAndStop()
                }
            }
        }
    }

    private fun cleanupAndStop() {
        Log.i(TAG, "Cleanup and stop SleepForegroundService")

        releaseRuntimeResources()

        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }

        SleepNotificationBuilder.cancelSleepNotification(applicationContext)

        stopSelf()
    }

    private fun releaseRuntimeResources() {
        sessionJob?.cancel()
        sessionJob = null
        profileJob?.cancel()
        profileJob = null
        alarmStartJob?.cancel()
        alarmStartJob = null

        stopSensorTracker() // ДОБАВЛЕНО: F9
        alarmSoundPlayer.stop()
        alarmVibrator.cancel()
        releaseWakeLock()
    }

    // =================================================================
    // WakeLock
    // =================================================================

    private fun acquireWakeLockIfNeeded() {
        if (wakeLock?.isHeld == true) {
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    // =================================================================
    // Foreground compatibility
    // =================================================================

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SleepNotificationBuilder.SLEEP_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                SleepNotificationBuilder.SLEEP_NOTIFICATION_ID,
                notification
            )
        }
    }

    // =================================================================
    // Companion
    // =================================================================

    companion object {

        private const val TAG = "SleepForegroundService"

        const val ACTION_START = "com.personal.sleepalarm.action.START_SLEEP_SERVICE"
        const val ACTION_STOP_AND_CANCEL = "com.personal.sleepalarm.action.STOP_AND_CANCEL"
        const val ACTION_STOP_ONLY = "com.personal.sleepalarm.action.STOP_ONLY"
        const val ACTION_TRIGGER_ALARM = "com.personal.sleepalarm.action.TRIGGER_ALARM"
        const val ACTION_SET_ALARM_VOLUME = "com.personal.sleepalarm.action.SET_ALARM_VOLUME"
        const val ACTION_PULSE_ALARM_VIBRATION =
            "com.personal.sleepalarm.action.PULSE_ALARM_VIBRATION"
        const val ACTION_STOP_ALARM_VIBRATION =
            "com.personal.sleepalarm.action.STOP_ALARM_VIBRATION"
        private const val MAX_CUE_PLAY_MS = 15_000L
        private const val MINUTE_MS = 60L * 1000L
        private const val LOOP_INTERVAL_MS = 15_000L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 60_000L
        private const val MAX_CUE_LATENESS_MS = 5L * 60L * 1000L
        private const val MISSED_TIMEOUT_MS = 10L * 60L * 1000L
        private const val ALARM_EARLY_TOLERANCE_MS = 5L * 60L * 1000L

        private const val PLAYED_BY_SERVICE = "SERVICE"

        private const val WAKE_LOCK_TAG = "sleepalarm:SleepForegroundService"
        private const val WAKE_LOCK_TIMEOUT_MS = 16L * 60L * 60L * 1000L

        // ДОБАВЛЕНО: F9 — не детектим засыпание, если с момента bedTime
        // прошло больше часа (поздний старт / восстановление после reboot).
        // Give the phone-only detector enough time for a slow wind-down without
        // keeping it alive all night; the session itself remains user-controlled.
        private const val MAX_DETECT_WINDOW_MS = 3L * 60L * 60L * 1000L

        // ДОБАВЛЕНО: ключи для передачи данных сессии прямо в сервис.
        const val EXTRA_WAKE_TIME = "extra_wake_time"
        const val EXTRA_FIRST_CUE_TIME = "extra_first_cue_time"
        private const val EXTRA_ALARM_VOLUME = "extra_alarm_volume"

        fun start(
            context: Context,
            sessionId: Int,
            wakeTime: Long,
            firstCueTime: Long?
        ) {
            val intent = Intent(context, SleepForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(IntentExtras.EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_WAKE_TIME, wakeTime)
                firstCueTime?.let { putExtra(EXTRA_FIRST_CUE_TIME, it) }
            }

            context.startForegroundService(intent)
        }

        fun stop(
            context: Context,
            cancelSession: Boolean
        ) {
            val intent = Intent(context, SleepForegroundService::class.java).apply {
                action = if (cancelSession) {
                    ACTION_STOP_AND_CANCEL
                } else {
                    ACTION_STOP_ONLY
                }
            }

            context.startService(intent)
        }

        fun triggerAlarm(context: Context, sessionId: Int) {
            val intent = Intent(context, SleepForegroundService::class.java).apply {
                action = ACTION_TRIGGER_ALARM
                putExtra(IntentExtras.EXTRA_SESSION_ID, sessionId)
            }
            context.startForegroundService(intent)
        }

        fun setAlarmVolume(context: Context, fraction: Float) {
            val intent = Intent(context, SleepForegroundService::class.java).apply {
                action = ACTION_SET_ALARM_VOLUME
                putExtra(EXTRA_ALARM_VOLUME, fraction.coerceIn(0f, 1f))
            }
            context.startService(intent)
        }

        fun pulseAlarmVibration(context: Context) {
            context.startService(
                Intent(context, SleepForegroundService::class.java).apply {
                    action = ACTION_PULSE_ALARM_VIBRATION
                }
            )
        }

        fun stopAlarmVibration(context: Context) {
            context.startService(
                Intent(context, SleepForegroundService::class.java).apply {
                    action = ACTION_STOP_ALARM_VIBRATION
                }
            )
        }
    }
}
