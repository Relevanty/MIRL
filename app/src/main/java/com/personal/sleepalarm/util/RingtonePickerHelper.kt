package com.personal.sleepalarm.util

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build

/**
 * Helper для системного выбора мелодии будильника (F2).
 *
 * НЕ запускает activity сам — UI-слой делает это через
 * ActivityResultContracts.StartActivityForResult.
 * Helper только:
 * 1. формирует Intent пикера;
 * 2. парсит выбранный URI из результата;
 * 3. получает читаемое название мелодии для отображения.
 *
 * Всё локальное, без интернета.
 */
object RingtonePickerHelper {

    /**
     * Создаёт Intent системного пикера мелодий.
     *
     * @param title заголовок окна пикера.
     * @param existingUriString сохранённый ранее URI (null — без подсветки).
     */
    fun createPickerIntent(
        title: String,
        existingUriString: String?
    ): Intent {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)

            // Подсвечиваем текущий выбор, если он есть и валиден.
            val existingUri = parseUriSafely(existingUriString)
            if (existingUri != null) {
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    existingUri
                )
            }
        }

        return intent
    }

    /**
     * Извлекает выбранный URI из результата пикера.
     *
     * Возвращает null, если пользователь нажал «Отмена»
     * или результат пустой.
     *
     * Учитывает deprecated-вариант getParcelableExtra на API 33+.
     */
    fun parsePickedUri(data: Intent?): Uri? {
        if (data == null) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
    }

    /**
     * Возвращает читаемое название мелодии по URI.
     *
     * Возвращает null, если URI невалиден или мелодия недоступна
     * (например, файл удалён). UI в этом случае покажет fallback-текст.
     */
    fun getRingtoneTitle(
        context: Context,
        uriString: String?
    ): String? {
        val uri = parseUriSafely(uriString) ?: return null

        return runCatching {
            RingtoneManager.getRingtone(context.applicationContext, uri)
                ?.getTitle(context)
        }.getOrNull()
    }

    /**
     * Безопасный парсинг строки в URI.
     */
    private fun parseUriSafely(uriString: String?): Uri? {
        if (uriString.isNullOrBlank()) return null

        return runCatching { Uri.parse(uriString) }.getOrNull()
    }
}