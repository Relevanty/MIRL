package com.personal.sleepalarm.ui.calendar

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.personal.sleepalarm.R
import com.personal.sleepalarm.alarm.EventAlarmScheduler
import com.personal.sleepalarm.service.EventNotificationBuilder
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
import com.personal.sleepalarm.data.db.entity.StudySessionEntity
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalendarViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application.applicationContext)
    private val eventDao = database.calendarEventDao()
    private val studyDao = database.studySessionDao()
    private val eventScheduler = EventAlarmScheduler(application.applicationContext)
    private val eventNotifications = EventNotificationBuilder(application.applicationContext)

    private val _eventsLoaded = MutableStateFlow(false)
    val eventsLoaded: StateFlow<Boolean> = _eventsLoaded.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()
    private val _savedEvent = MutableStateFlow<CalendarEventEntity?>(null)
    val savedEvent: StateFlow<CalendarEventEntity?> = _savedEvent.asStateFlow()

    val events: StateFlow<List<CalendarEventEntity>> = eventDao
        .observeAll()
        .onEach { _eventsLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val studyFrom = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
    private val studyTo = System.currentTimeMillis() + 24L * 3600 * 1000

    val studySessions: StateFlow<List<StudySessionEntity>> = studyDao
        .observeInRange(studyFrom, studyTo)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val actualActivities: StateFlow<List<ActivityRecordEntity>> = database.activityRecordDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = database.taskDao().observeByRoutineFlag(false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sleepSessions: StateFlow<List<SleepSessionEntity>> = database.sleepSessionDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearSaveError() { _saveError.value = null }

    fun consumeSavedEvent(event: CalendarEventEntity) {
        if (_savedEvent.value == event) _savedEvent.value = null
    }

    /** Durable plan-only save: UI closes only after receiving a persisted id. */
    fun savePlannedActivity(event: CalendarEventEntity) {
        if (_saving.value || _savedEvent.value != null) return
        val validation = validatePlannedActivity(event)
        if (validation != null) {
            _saveError.value = getApplication<Application>().getString(validation.messageResource())
            return
        }
        _saving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                val saved = try {
                    database.withTransaction {
                        val current = if (event.id == 0) null else {
                            eventDao.getById(event.id) ?: throw PlanSaveFailure(R.string.calendar_plan_missing)
                        }
                        val task = event.taskId?.let { taskId ->
                            database.taskDao().getById(taskId)
                                ?: throw PlanSaveFailure(R.string.calendar_plan_task_missing)
                        }
                        if (task == null && event.projectId != null &&
                            database.projectDao().getById(event.projectId) == null
                        ) throw PlanSaveFailure(R.string.calendar_plan_project_missing)
                        val normalized = normalizePlannedActivity(event, current, task)
                        if (current == null) normalized.copy(id = eventDao.insert(normalized).toInt())
                        else {
                            eventDao.update(normalized)
                            normalized
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: PlanSaveFailure) {
                    _saveError.value = getApplication<Application>().getString(failure.messageResource)
                    return@launch
                } catch (_: Exception) {
                    _saveError.value = getApplication<Application>().getString(R.string.calendar_plan_save_failed)
                    return@launch
                }

                // The database write has committed. Deliver the real id even if
                // Android cannot schedule its notification; retry must not insert twice.
                _savedEvent.value = saved
                try {
                    eventNotifications.cancel(saved.id)
                    scheduleIfLive(saved)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(
                        getApplication(), R.string.calendar_plan_alarm_failed, Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                _saving.value = false
            }
        }
    }

    fun deleteEvent(id: Int) {
        viewModelScope.launch {
            eventScheduler.cancel(id)
            eventNotifications.cancel(id)
            eventDao.deleteById(id)
        }
    }

    private suspend fun scheduleIfLive(event: CalendarEventEntity) {
        val linkedTaskDone = event.taskId
            ?.let { database.taskDao().getById(it)?.isDone }
            ?: false
        if (linkedTaskDone) eventScheduler.cancel(event.id)
        else eventScheduler.schedule(event)
    }
}

private class PlanSaveFailure(val messageResource: Int) : IllegalStateException()

private fun PlannedActivityValidationError.messageResource(): Int = when (this) {
    PlannedActivityValidationError.TITLE -> R.string.calendar_plan_title_required
    PlannedActivityValidationError.TIME_RANGE -> R.string.calendar_plan_time_invalid
    PlannedActivityValidationError.REPEAT -> R.string.calendar_plan_repeat_invalid
    PlannedActivityValidationError.REMINDER -> R.string.calendar_plan_reminder_invalid
    PlannedActivityValidationError.INVALID_ID -> R.string.calendar_plan_missing
}

fun eventsOn(events: List<CalendarEventEntity>, date: LocalDate): List<CalendarEventEntity> {
    val zone = ZoneId.systemDefault()
    return events.filter { ev ->
        val start = Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalDate()
        when (ev.repeatRule) {
            "daily" -> !date.isBefore(start)
            "weekly" -> !date.isBefore(start) && date.dayOfWeek == start.dayOfWeek
            else -> date == start
        }
    }
}

