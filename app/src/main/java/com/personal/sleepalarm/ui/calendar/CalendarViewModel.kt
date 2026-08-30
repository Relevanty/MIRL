package com.personal.sleepalarm.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.alarm.EventAlarmScheduler
import com.personal.sleepalarm.service.EventNotificationBuilder
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.db.entity.CalendarEventEntity
import com.personal.sleepalarm.data.db.entity.StudySessionEntity
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.db.entity.SleepSessionEntity
import com.personal.sleepalarm.data.db.entity.DDayEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

    val events: StateFlow<List<CalendarEventEntity>> = eventDao
        .observeAll()
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

    val milestones: StateFlow<List<DDayEntity>> = database.ddayDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            val id = eventDao.insert(event).toInt()
            val saved = event.copy(id = id)
            scheduleIfLive(saved)
        }
    }

    fun updateEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            eventNotifications.cancel(event.id)
            eventDao.update(event)
            scheduleIfLive(event)
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

