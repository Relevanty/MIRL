package com.personal.sleepalarm.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * ViewModel расписания.
 *
 * Держит редактируемый текст и автосохраняет его через debounce (1 сек
 * после последней правки), чтобы не дёргать БД на каждом символе.
 */
class ScheduleViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application.applicationContext)

    private val repository = ScheduleRepository(database.scheduleDao())

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        // Загружаем сохранённое расписание один раз.
        viewModelScope.launch {
            val entity = repository.get()
            _content.value = entity?.content.orEmpty()
            _loaded.value = true
        }

        // Автосохранение: ждём 1 сек тишины после последней правки.
        viewModelScope.launch {
            _content
                .filter { _loaded.value }
                .debounce(SAVE_DEBOUNCE_MS)
                .collect { text ->
                    repository.save(text)
                }
        }
    }

    fun onContentChanged(text: String) {
        _content.value = text
    }

    /**
     * Финальное сохранение при уходе с экрана, если debounce не успел.
     * Отдельный scope, т.к. viewModelScope уже отменён в onCleared.
     */
    override fun onCleared() {
        super.onCleared()
        CoroutineScope(Dispatchers.IO).launch {
            if (_loaded.value) {
                repository.save(_content.value)
            }
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 1_000L
    }
}