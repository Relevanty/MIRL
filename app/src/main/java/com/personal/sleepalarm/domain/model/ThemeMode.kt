package com.personal.sleepalarm.domain.model

/**
 * Режим темы приложения.
 *
 * Хранится в DataStore Preferences (НЕ в Room),
 * потому что тема нужна до Compose и не связана с историей сна.
 */
enum class ThemeMode {
    /** Следовать системной теме (тёмная/светлая). */
    SYSTEM,

    /** Всегда тёмная (ночная) тема. */
    DARK,

    /** Всегда светлая тема. */
    LIGHT
}