package com.personal.sleepalarm.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import java.io.File

/**
 * Работа с обложками библиотеки.
 *
 * Обложки копируются из внешнего URI в приватное хранилище
 * (filesDir/covers/), чтобы не потерять доступ после перезагрузки
 * и не зависеть от внешних content:// URI. Без интернета.
 */
object CoverHelper {

    private const val DIR = "covers"
    private val bitmapCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /**
     * Копирует изображение по URI в приватное хранилище.
     * Возвращает абсолютный путь файла или null при ошибке.
     */
    fun copyCover(context: Context, uri: Uri): String? {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val target = File(dir, "cover_${System.currentTimeMillis()}.jpg")

        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            } ?: return null
            target.absolutePath
        }.getOrNull()
    }

    /** Удаляет файл обложки (при замене или удалении элемента). */
    fun deleteCover(path: String?) {
        path?.let {
            runCatching { File(it).delete() }
            bitmapCache.snapshot().keys.filter { key -> key.startsWith("$it#") }.forEach(bitmapCache::remove)
        }
    }

    /** Загружает Bitmap из файла. Возвращает null при ошибке. */
    fun loadBitmap(path: String?): Bitmap? {
        return loadBitmap(path, 1_024)
    }

    /** Декодирует уменьшенную версию и переиспользует её между шариками/карточками. */
    fun loadBitmap(path: String?, maxDimensionPx: Int): Bitmap? {
        if (path == null) return null
        val boundedSize = maxDimensionPx.coerceIn(64, 2_048)
        val key = "$path#$boundedSize"
        bitmapCache.get(key)?.let { return it }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > boundedSize * 2 || bounds.outHeight / sample > boundedSize * 2) {
                sample *= 2
            }
            val bitmap = BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
            ) ?: return null
            bitmapCache.put(key, bitmap)
            bitmap
        }.getOrNull()
    }
}
