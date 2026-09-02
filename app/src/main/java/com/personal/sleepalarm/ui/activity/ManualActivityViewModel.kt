package com.personal.sleepalarm.ui.activity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.app.App
import com.personal.sleepalarm.data.db.entity.ActivityRecordEntity
import com.personal.sleepalarm.data.db.entity.ProjectEntity
import com.personal.sleepalarm.data.db.entity.TaskEntity
import com.personal.sleepalarm.data.repository.ActivityConflictStrategy
import com.personal.sleepalarm.data.repository.ManualActivityInput
import com.personal.sleepalarm.data.repository.SaveActivityResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ManualActivityEvent {
    data class Saved(val id: Int) : ManualActivityEvent
    data class Error(val reason: String) : ManualActivityEvent
    data object Deleted : ManualActivityEvent
}

class ManualActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val locator = (application as App).serviceLocator
    private val repository = locator.activityRecordRepository

    val tasks: StateFlow<List<TaskEntity>> = locator.taskRepository.observeGeneralTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projects: StateFlow<List<ProjectEntity>> = locator.database.projectDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _conflicts = MutableStateFlow<List<ActivityRecordEntity>>(emptyList())
    val conflicts: StateFlow<List<ActivityRecordEntity>> = _conflicts
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    private val _events = MutableSharedFlow<ManualActivityEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun save(input: ManualActivityInput, strategy: ActivityConflictStrategy = ActivityConflictStrategy.ASK) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            try {
                when (val result = repository.saveManual(input, strategy)) {
                    is SaveActivityResult.Saved -> {
                        _conflicts.value = emptyList()
                        _events.emit(ManualActivityEvent.Saved(result.id))
                    }
                    is SaveActivityResult.Conflicts -> _conflicts.value = result.records
                    is SaveActivityResult.Invalid -> {
                        _conflicts.value = emptyList()
                        _events.emit(ManualActivityEvent.Error(result.reason))
                    }
                }
            } finally {
                _saving.value = false
            }
        }
    }

    fun clearConflicts() {
        _conflicts.value = emptyList()
    }

    suspend fun latestManual(): ActivityRecordEntity? = repository.latestManual()

    fun delete(recordId: Int) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            try {
                if (repository.deleteManual(recordId)) _events.emit(ManualActivityEvent.Deleted)
            } finally {
                _saving.value = false
            }
        }
    }
}
