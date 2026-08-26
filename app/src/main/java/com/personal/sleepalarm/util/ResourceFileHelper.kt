package com.personal.sleepalarm.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/** Локальные материалы библиотеки. Файл копируется внутрь MIRL и не требует сети. */
object ResourceFileHelper {
    private const val DIR = "library_resources"

    data class CopiedResource(val path: String, val displayName: String)

    fun copyIntoApp(context: Context, uri: Uri): CopiedResource? = runCatching {
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() } ?: "material_${System.currentTimeMillis()}"
        val safeName = displayName.replace(Regex("[^A-Za-zА-Яа-я0-9._ -]"), "_")
        val directory = File(context.filesDir, DIR).apply { mkdirs() }
        val target = File(directory, "${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use(input::copyTo)
        } ?: return null
        CopiedResource(target.absolutePath, displayName)
    }.getOrNull()

    fun delete(path: String?) {
        if (!path.isNullOrBlank()) runCatching { File(path).delete() }
    }

    fun open(context: Context, path: String?, referenceUrl: String): Boolean = runCatching {
        val intent = if (!path.isNullOrBlank() && File(path).exists()) {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                File(path)
            )
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            val normalized = referenceUrl.trim().let {
                if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
            }
            Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
