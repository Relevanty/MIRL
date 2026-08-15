package com.personal.sleepalarm.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Управляет языком приложения без привязки к языку всей системы.
 *
 * Android 13+ хранит выбор штатным LocaleManager. На Android 8–12 локаль
 * сохраняется синхронно и применяется к базовому Context до создания UI.
 */
object AppLanguageManager {

    const val SYSTEM = "system"
    const val RUSSIAN = "ru"
    const val ENGLISH = "en"

    private const val PREFERENCES = "app_language"
    private const val KEY_LANGUAGE = "language"

    fun currentLanguage(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            return locales.takeUnless { it.isEmpty }?.get(0)?.language ?: SYSTEM
        }

        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM)
            .orEmpty()
            .ifBlank { SYSTEM }
    }

    @SuppressLint("ApplySharedPref") // must persist before Activity.recreate()
    fun setLanguage(context: Context, language: String) {
        val normalized = language.takeIf { it == RUSSIAN || it == ENGLISH } ?: SYSTEM

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (normalized == SYSTEM) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(normalized)
                }
            return
        }

        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalized)
            .commit()

        val locale = localeFor(normalized)
        Locale.setDefault(locale)
        context.findActivity()?.recreate()
    }

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context

        val language = currentLanguage(context)
        if (language == SYSTEM) return context

        val locale = localeFor(language)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    private fun localeFor(language: String): Locale {
        return if (language == SYSTEM) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Locale.forLanguageTag(language)
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
