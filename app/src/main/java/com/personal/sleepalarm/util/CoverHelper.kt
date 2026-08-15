package com.personal.sleepalarm.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
        path?.let { runCatching { File(it).delete() } }
    }

    /** Загружает Bitmap из файла. Возвращает null при ошибке. */
    fun loadBitmap(path: String?): Bitmap? {
        return path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
}