package com.personal.sleepalarm.util

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns

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

    private const val DEFAULT_ALARM_URI = "content://settings/system/alarm_alert"
    private const val DEFAULT_NOTIFICATION_URI = "content://settings/system/notification_sound"
    private const val DEFAULT_RINGTONE_URI = "content://settings/system/ringtone"

    /**
     * Создаёт Intent системного пикера мелодий.
     *
     * @param title заголовок окна пикера.
     * @param existingUriString сохранённый ранее URI (null — без подсветки).
     */
    fun createPickerIntent(
        title: String,
        existingUriString: String?,
        ringtoneType: Int = RingtoneManager.TYPE_ALARM,
        showSilent: Boolean = false
    ): Intent {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                ringtoneType
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, showSilent)
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

        val extraUri = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
        }.getOrNull()
        // Some OEM pickers (including theme apps) return only Intent.data.
        return extraUri ?: data.data
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
        if (!isSoundReadable(context, uri)) return null

        return runCatching {
            RingtoneManager.getRingtone(context.applicationContext, uri)
                ?.getTitle(context)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Resolves both system ringtone titles and names of arbitrary audio documents. */
    fun getSoundTitle(context: Context, uriString: String?): String? {
        val uri = parseUriSafely(uriString) ?: return null
        if (!isSoundReadable(context, uri)) return null

        val documentTitle = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (documentTitle != null) return documentTitle

        return runCatching {
            RingtoneManager.getRingtone(context.applicationContext, uri)
                ?.getTitle(context)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Opens the URI for real; constructing a Ringtone alone does not prove it is readable. */
    fun isSoundReadable(context: Context, uriString: String?): Boolean {
        val uri = parseUriSafely(uriString) ?: return false
        return isSoundReadable(context, uri)
    }

    fun isSoundReadable(context: Context, uri: Uri): Boolean = runCatching {
        context.applicationContext.contentResolver
            .openAssetFileDescriptor(uri, "r")
            ?.use { descriptor -> descriptor.fileDescriptor.valid() }
            ?: false
    }.getOrDefault(false)

    /** True only for Android's stable symbolic "use current system default" URI. */
    fun isDefaultAlias(uri: Uri, ringtoneType: Int): Boolean =
        isDefaultAlias(uri.toString(), ringtoneType)

    internal fun isDefaultAlias(uriString: String, ringtoneType: Int): Boolean = when (uriString) {
        DEFAULT_ALARM_URI -> ringtoneType and RingtoneManager.TYPE_ALARM != 0
        DEFAULT_NOTIFICATION_URI -> ringtoneType and RingtoneManager.TYPE_NOTIFICATION != 0
        DEFAULT_RINGTONE_URI -> ringtoneType and RingtoneManager.TYPE_RINGTONE != 0
        else -> false
    }

    /**
     * Безопасный парсинг строки в URI.
     */
    private fun parseUriSafely(uriString: String?): Uri? {
        if (uriString.isNullOrBlank()) return null

        return runCatching { Uri.parse(uriString) }.getOrNull()
    }
}
