package com.personal.sleepalarm.domain.model

/**
 * Режим расписания lucid-подсказок.
 *
 * PERIODIC — классический: первый сигнал через firstCueDelay,
 *            далее каждые cueInterval минут.
 *
 * REM_TARGETED — сигналы привязаны к теоретическим REM-окнам
 *                внутри каждого цикла сна (F7).
 */
enum class CueScheduleMode {
    PERIODIC,
    REM_TARGETED
}