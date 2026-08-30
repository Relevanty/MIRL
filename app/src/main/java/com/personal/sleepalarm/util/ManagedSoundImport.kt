package com.personal.sleepalarm.util

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Copies picker-owned audio into MIRL's private storage.
 *
 * OEM ringtone providers (notably theme applications) often grant access only
 * for the picker result callback and do not support persistable URI grants.
 * Keeping their original content URI therefore works in preview but fails when
 * an alarm or notification is played later. A private copy gives every runtime
 * playback path the same stable URI.
 */
object ManagedSoundImport {
    private const val DIRECTORY = "managed_sounds"
    private const val MAX_SOUND_BYTES = 64L * 1024L * 1024L

    data class ImportedSound(
        val uriString: String,
        val displayName: String,
        internal val filePath: String
    )

    fun copyIntoApp(context: Context, source: Uri, slot: String): ImportedSound? = runCatching {
        val appContext = context.applicationContext
        val safeSlot = sanitizeSlot(slot)
        val displayName = resolveDisplayName(appContext, source)
        val safeName = sanitizeDisplayName(displayName)
        val directory = File(appContext.filesDir, DIRECTORY).apply { mkdirs() }
        val target = File(directory, "${safeSlot}_${System.currentTimeMillis()}_$safeName")
        val temporary = File(directory, ".${target.name}.tmp")

        try {
            appContext.contentResolver.openInputStream(source)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_SOUND_BYTES) {
                            throw IOException("Selected sound is larger than $MAX_SOUND_BYTES bytes")
                        }
                        output.write(buffer, 0, read)
                    }
                    if (total == 0L) throw IOException("Selected sound is empty")
                }
            } ?: throw IOException("Unable to open selected sound")

            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (throwable: Throwable) {
            temporary.delete()
            target.delete()
            throw throwable
        }

        if (!canPrepare(target)) {
            target.delete()
            throw IOException("Selected file is not a playable audio source")
        }

        val ownedUri = try {
            FileProvider.getUriForFile(
                appContext,
                providerAuthority(appContext),
                target,
                displayName
            )
        } catch (throwable: Throwable) {
            target.delete()
            throw throwable
        }
        ImportedSound(
            uriString = ownedUri.toString(),
            displayName = displayName,
            filePath = target.absolutePath
        )
    }.getOrNull()

    /** Removes superseded private copies only after the new URI was persisted. */
    fun deleteOlderCopies(
        context: Context,
        slot: String,
        keepFilePath: String,
        protectedFilePaths: Set<String> = emptySet()
    ) {
        val prefix = "${sanitizeSlot(slot)}_"
        val keep = canonicalFileOrNull(File(keepFilePath))
        val protected = protectedFilePaths.mapNotNullTo(mutableSetOf()) { path ->
            canonicalFileOrNull(File(path))
        }
        val protectedNames = protected.mapTo(mutableSetOf()) { it.name }
        val directory = File(context.applicationContext.filesDir, DIRECTORY)
        directory.listFiles().orEmpty().forEach { file ->
            val candidate = canonicalFileOrNull(file)
            if (
                candidate != null &&
                shouldDeleteCopy(file.name, prefix, keep?.name, protectedNames)
            ) {
                runCatching { file.delete() }
            }
        }
    }

    fun deleteAllCopies(
        context: Context,
        slot: String,
        protectedFilePaths: Set<String> = emptySet()
    ) {
        val prefix = "${sanitizeSlot(slot)}_"
        val protected = protectedFilePaths.mapNotNullTo(mutableSetOf()) { path ->
            canonicalFileOrNull(File(path))
        }
        val directory = File(context.applicationContext.filesDir, DIRECTORY)
        directory.listFiles().orEmpty().forEach { file ->
            val candidate = canonicalFileOrNull(file)
            if (candidate != null && candidate !in protected && file.name.startsWith(prefix)) {
                runCatching { file.delete() }
            }
        }
    }

    /** Deletes an uncommitted import, while refusing paths outside our private sound directory. */
    fun deleteImportedCopy(context: Context, filePath: String) {
        val directory = canonicalFileOrNull(
            File(context.applicationContext.filesDir, DIRECTORY)
        ) ?: return
        val candidate = canonicalFileOrNull(File(filePath)) ?: return
        if (candidate.parentFile == directory) runCatching { candidate.delete() }
    }

    /** Resolves only URIs issued by this app's managed-sound FileProvider root. */
    fun ownedFilePath(context: Context, uriString: String?): String? = runCatching {
        val appContext = context.applicationContext
        val uri = Uri.parse(uriString ?: return null)
        if (uri.scheme != "content" || uri.authority != providerAuthority(appContext)) return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments.first() != DIRECTORY) return null

        val directory = File(appContext.filesDir, DIRECTORY).canonicalFile
        val candidate = File(directory, segments.last()).canonicalFile
        candidate.absolutePath.takeIf { candidate.parentFile == directory }
    }.getOrNull()

    fun isOwnedUri(context: Context, uriString: String?): Boolean =
        ownedFilePath(context, uriString) != null

    internal fun sanitizeSlot(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
        .take(48)
        .ifBlank { "sound" }

    internal fun sanitizeDisplayName(value: String): String = value
        .replace(Regex("[^A-Za-zА-Яа-я0-9._ -]"), "_")
        .trim()
        .take(96)
        .ifBlank { "sound.audio" }

    internal fun shouldDeleteCopy(
        fileName: String,
        slotPrefix: String,
        keepFileName: String?,
        protectedFileNames: Set<String>
    ): Boolean = fileName.startsWith(slotPrefix) &&
        fileName != keepFileName &&
        fileName !in protectedFileNames

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        val queried = runCatching {
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
        if (queried != null) return queried

        val ringtoneTitle = runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return ringtoneTitle ?: "sound_${System.currentTimeMillis()}.audio"
    }

    private fun canPrepare(file: File): Boolean {
        var player: MediaPlayer? = null
        return try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { player?.release() }
        }
    }

    private fun providerAuthority(context: Context): String = "${context.packageName}.files"

    private fun canonicalFileOrNull(file: File): File? = runCatching {
        file.canonicalFile
    }.getOrNull()
}
