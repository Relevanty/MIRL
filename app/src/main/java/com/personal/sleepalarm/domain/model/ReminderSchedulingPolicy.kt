package com.personal.sleepalarm.domain.model

/** Pure rule gate used before a reminder is handed to AlarmManager. */
object ReminderSchedulingPolicy {
    fun canSchedule(
        triggerRule: String,
        linkedType: String,
        linkedId: Int?,
        taskExists: Boolean,
        taskDone: Boolean,
        taskDueAtMillis: Long?,
        taskStartAtMillis: Long?
    ): Boolean {
        if (triggerRule == "AT_TIME" || triggerRule == "BEFORE_SLEEP") return true
        if (linkedType != "TASK" || linkedId == null || !taskExists || taskDone) return false
        return when (triggerRule) {
            "BEFORE_DEADLINE", "BECOMES_URGENT" -> taskDueAtMillis != null
            "BEFORE_FOCUS" -> taskStartAtMillis != null
            "NO_PROGRESS" -> true
            else -> false
        }
    }
}
