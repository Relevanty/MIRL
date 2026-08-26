package com.personal.sleepalarm.ui.tasks

import androidx.compose.runtime.Immutable

@Immutable
enum class TaskQuadrant(val storageValue: Int) {
    NOW(1),
    SCHEDULE(2),
    DELEGATE(3),
    LET_GO(4);

    companion object {
        fun fromStorage(value: Int): TaskQuadrant = entries.firstOrNull {
            it.storageValue == value
        } ?: SCHEDULE
    }
}

@Immutable
enum class TaskEnergy(val storageValue: String) {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH");

    companion object {
        fun fromStorage(value: String): TaskEnergy = entries.firstOrNull {
            it.storageValue == value
        } ?: MEDIUM
    }
}

@Immutable
data class TaskChecklistItem(val text: String, val isDone: Boolean)

internal fun parseTaskChecklist(value: String): List<TaskChecklistItem> = value
    .lineSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .map { line ->
        when {
            line.startsWith("[x] ", ignoreCase = true) -> TaskChecklistItem(line.drop(4).trim(), true)
            line.startsWith("[ ] ") -> TaskChecklistItem(line.drop(4).trim(), false)
            else -> TaskChecklistItem(line, false)
        }
    }
    .toList()

internal fun serializeTaskChecklist(items: List<TaskChecklistItem>): String = items.joinToString("\n") {
    "[${if (it.isDone) "x" else " "}] ${it.text}"
}
