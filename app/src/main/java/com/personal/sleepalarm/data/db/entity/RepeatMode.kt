package com.personal.sleepalarm.data.db.entity

/**
 * Режим повторения напоминания.
 *
 * Хранится в БД как TEXT через конвертер в Converters.
 */
enum class RepeatMode {
    /** Один раз, после срабатывания отключается. */
    ONCE,

    /** Ежедневно в одно и то же время. */
    DAILY,

    /** По выбранным дням недели (битовая маска в daysOfWeek). */
    WEEKLY,

    /** Каждые N дней (intervalDays). */
    INTERVAL
}