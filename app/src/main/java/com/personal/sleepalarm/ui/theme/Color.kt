package com.personal.sleepalarm.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Вспомогательная палитра приложения.
 *
 * ВАЖНО: это НЕ источник цветов для UI-экранов. Экраны используют
 * MaterialTheme.colorScheme, которая строится из пресетов в ThemeCatalog
 * и Theme.kt (14 готовых тем).
 *
 * Константы ниже используются только там, где Compose-тема недоступна:
 * уведомления (NotificationBuilder), splash, системные окна.
 *
 * Ночная палитра — базовая идентичность приложения.
 */

// =====================================================================
// Ночная (тёмная) палитра
// =====================================================================

val NightTop = Color(0xFF0B1026)
val NightMid = Color(0xFF131A31)
val NightBottom = Color(0xFF0D1226)

val NightSurface = Color(0xFF131A31)
val NightSurfaceVariant = Color(0xFF1B2340)
val NightOutline = Color(0xFF2A3558)

val WarmAmber = Color(0xFFFFB86B)
val WarmAmberSoft = Color(0xFFFFD9A0)
val MoonTeal = Color(0xFF63D8C2)
val DawnRed = Color(0xFFFF7B72)
val SkyInfo = Color(0xFF7FB3FF)

val NightTextPrimary = Color(0xFFE9EDF9)
val NightTextMuted = Color(0xFF8A93B2)

// =====================================================================
// Светлая палитра (тёплые кремово-янтарные тона)
// =====================================================================

val LightBackground = Color(0xFFFFF7EC)
val LightSurface = Color(0xFFFFFBF4)
val LightSurfaceVariant = Color(0xFFF0E6D6)
val LightOutline = Color(0xFFD8C7AE)

val LightOnSurface = Color(0xFF2A2118)
val LightOnSurfaceMuted = Color(0xFF6B5D49)

val WarmAmberDeep = Color(0xFFB5701A)
val WarmAmberContainer = Color(0xFFFFE2B8)
val MoonTealDeep = Color(0xFF0E7C6B)