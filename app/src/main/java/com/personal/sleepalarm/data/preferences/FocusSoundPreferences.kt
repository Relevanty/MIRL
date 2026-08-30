package com.personal.sleepalarm.data.preferences

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.sleepalarm.domain.focusaudio.CustomFocusSoundFile
import com.personal.sleepalarm.domain.focusaudio.FocusSoundRules
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSelection
import com.personal.sleepalarm.domain.focusaudio.FocusSoundSettings
import com.personal.sleepalarm.domain.focusaudio.FocusSoundscapeSelection
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.focusSoundDataStore by preferencesDataStore(name = "focus_soundscape_prefs")

/**
 * Offline preferences for the continuous focus soundscape. These settings are separate
 * from short Pomodoro/notification signals, so changing an ambience cannot create a
 * second completion sound.
 */
class FocusSoundPreferences(context: Context) {
    private val appContext = context.applicationContext

    fun observeSettings(): Flow<FocusSoundSettings> =
        appContext.focusSoundDataStore.data.map(::decodeSettings)

    suspend fun getSettings(): FocusSoundSettings = observeSettings().first()

    fun observeTaskOverride(taskId: Int): Flow<FocusSoundscapeSelection?> {
        requireValidTaskId(taskId)
        return appContext.focusSoundDataStore.data.map { preferences ->
            FocusSoundPreferenceCodec.decodeSoundscape(preferences[taskOverrideKey(taskId)])
        }
    }

    suspend fun getTaskOverride(taskId: Int): FocusSoundscapeSelection? =
        observeTaskOverride(taskId).first()

    fun observeEffectiveSelection(taskId: Int? = null): Flow<FocusSoundscapeSelection> {
        taskId?.let(::requireValidTaskId)
        return appContext.focusSoundDataStore.data.map { preferences ->
            val settings = decodeSettings(preferences)
            val override = taskId?.let {
                FocusSoundPreferenceCodec.decodeSoundscape(preferences[taskOverrideKey(it)])
            }
            FocusSoundRules.effectiveSelection(settings, override)
        }
    }

    suspend fun getEffectiveSelection(taskId: Int? = null): FocusSoundscapeSelection =
        observeEffectiveSelection(taskId).first()

    suspend fun setDefaultSelection(selection: FocusSoundscapeSelection) {
        val safe = selection.normalized()
        appContext.focusSoundDataStore.edit { preferences ->
            preferences[KEY_DEFAULT_SELECTION] = FocusSoundPreferenceCodec.encodeSoundscape(safe)
        }
    }

    /**
     * Records a choice and optionally makes it the default for focus sessions without a
     * task-specific override. The recent list is updated atomically with the selection.
     */
    suspend fun select(selection: FocusSoundscapeSelection, setAsDefault: Boolean = true) {
        val safe = selection.normalized()
        appContext.focusSoundDataStore.edit { preferences ->
            val encoded = FocusSoundPreferenceCodec.encodeSoundscape(safe)
            preferences[KEY_LAST_SELECTION] = encoded
            if (setAsDefault) preferences[KEY_DEFAULT_SELECTION] = encoded
            val current = FocusSoundPreferenceCodec.decodeRecents(preferences[KEY_RECENTS])
            preferences[KEY_RECENTS] = FocusSoundPreferenceCodec.encodeRecents(
                FocusSoundRules.pushRecent(current, safe.primary)
            )
        }
    }

    suspend fun setVolume(volumePercent: Int) {
        appContext.focusSoundDataStore.edit { preferences ->
            preferences[KEY_VOLUME] = FocusSoundRules.normalizeVolume(volumePercent)
        }
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        appContext.focusSoundDataStore.edit { preferences ->
            val current = FocusSoundRules.sanitizeFavoriteIds(preferences[KEY_FAVORITES].orEmpty())
            val updated = if (favorite) {
                FocusSoundRules.sanitizeFavoriteIds(current + id)
            } else {
                current - id
            }
            preferences[KEY_FAVORITES] = updated
        }
    }

    suspend fun toggleFavorite(id: String) {
        appContext.focusSoundDataStore.edit { preferences ->
            preferences[KEY_FAVORITES] = FocusSoundRules.toggleFavorite(
                preferences[KEY_FAVORITES].orEmpty(),
                id
            )
        }
    }

    suspend fun addCustomFiles(files: List<CustomFocusSoundFile>) {
        val incoming = FocusSoundRules.normalizeCustomLibrary(files)
        if (incoming.isEmpty()) return
        appContext.focusSoundDataStore.edit { preferences ->
            val current = FocusSoundPreferenceCodec.decodeCustomLibrary(preferences[KEY_CUSTOM_LIBRARY])
            val merged = FocusSoundRules.normalizeCustomLibrary(incoming + current)
            preferences[KEY_CUSTOM_LIBRARY] = FocusSoundPreferenceCodec.encodeCustomLibrary(merged)
        }
    }

    /** Removes the item from MIRL's library only; the source document remains untouched. */
    suspend fun removeCustomFile(uriString: String) {
        val normalizedUri = uriString.trim()
        if (normalizedUri.isEmpty()) return
        appContext.focusSoundDataStore.edit { preferences ->
            val updated = FocusSoundPreferenceCodec.decodeCustomLibrary(preferences[KEY_CUSTOM_LIBRARY])
                .filterNot { it.uriString == normalizedUri }
            if (updated.isEmpty()) {
                preferences.remove(KEY_CUSTOM_LIBRARY)
            } else {
                preferences[KEY_CUSTOM_LIBRARY] = FocusSoundPreferenceCodec.encodeCustomLibrary(updated)
            }
            for (key in listOf(KEY_DEFAULT_SELECTION, KEY_LAST_SELECTION)) {
                val selection = FocusSoundPreferenceCodec.decodeSoundscape(preferences[key])
                if (selection?.primary?.customFile?.uriString == normalizedUri) {
                    preferences[key] = FocusSoundPreferenceCodec.encodeSoundscape(
                        selection.copy(
                            primary = FocusSoundSelection.silence(),
                            secondaryLayerId = null
                        )
                    )
                }
            }
            val taskOverrideNames = preferences.asMap().keys
                .map { it.name }
                .filter { it.startsWith("task_") && it.endsWith("_soundscape_v1") }
            for (keyName in taskOverrideNames) {
                val key = stringPreferencesKey(keyName)
                val selection = FocusSoundPreferenceCodec.decodeSoundscape(preferences[key])
                if (selection?.primary?.customFile?.uriString == normalizedUri) {
                    preferences[key] = FocusSoundPreferenceCodec.encodeSoundscape(
                        selection.copy(
                            primary = FocusSoundSelection.silence(),
                            secondaryLayerId = null
                        )
                    )
                }
            }
            val recents = FocusSoundPreferenceCodec.decodeRecents(preferences[KEY_RECENTS])
                .filterNot { it.customFile?.uriString == normalizedUri }
            if (recents.isEmpty()) {
                preferences.remove(KEY_RECENTS)
            } else {
                preferences[KEY_RECENTS] = FocusSoundPreferenceCodec.encodeRecents(recents)
            }
            preferences[KEY_FAVORITES] = FocusSoundRules.sanitizeFavoriteIds(
                preferences[KEY_FAVORITES].orEmpty() -
                    "${com.personal.sleepalarm.domain.focusaudio.FocusSoundCatalog.CUSTOM_FILE_ID}:$normalizedUri"
            )
        }
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                Uri.parse(normalizedUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    /** Null removes the override and returns the task to the current global default. */
    suspend fun setTaskOverride(taskId: Int, selection: FocusSoundscapeSelection?) {
        requireValidTaskId(taskId)
        appContext.focusSoundDataStore.edit { preferences ->
            val key = taskOverrideKey(taskId)
            if (selection == null) {
                preferences.remove(key)
            } else {
                preferences[key] = FocusSoundPreferenceCodec.encodeSoundscape(selection.normalized())
            }
        }
    }

    suspend fun selectForTask(
        taskId: Int,
        selection: FocusSoundscapeSelection,
        rememberForTask: Boolean
    ) {
        requireValidTaskId(taskId)
        val safe = selection.normalized()
        appContext.focusSoundDataStore.edit { preferences ->
            preferences[KEY_LAST_SELECTION] = FocusSoundPreferenceCodec.encodeSoundscape(safe)
            val current = FocusSoundPreferenceCodec.decodeRecents(preferences[KEY_RECENTS])
            preferences[KEY_RECENTS] = FocusSoundPreferenceCodec.encodeRecents(
                FocusSoundRules.pushRecent(current, safe.primary)
            )
            val key = taskOverrideKey(taskId)
            if (rememberForTask) {
                preferences[key] = FocusSoundPreferenceCodec.encodeSoundscape(safe)
            } else {
                preferences.remove(key)
            }
        }
    }

    suspend fun clearRecents() {
        appContext.focusSoundDataStore.edit { it.remove(KEY_RECENTS) }
    }

    private fun decodeSettings(preferences: Preferences): FocusSoundSettings = FocusSoundSettings(
        defaultSelection = FocusSoundPreferenceCodec.decodeSoundscape(preferences[KEY_DEFAULT_SELECTION])
            ?: FocusSoundscapeSelection(),
        lastSelection = FocusSoundPreferenceCodec.decodeSoundscape(preferences[KEY_LAST_SELECTION])
            ?: FocusSoundscapeSelection(),
        volumePercent = preferences[KEY_VOLUME] ?: FocusSoundRules.DEFAULT_VOLUME_PERCENT,
        favoriteIds = preferences[KEY_FAVORITES].orEmpty(),
        recentSelections = FocusSoundPreferenceCodec.decodeRecents(preferences[KEY_RECENTS]),
        customLibrary = FocusSoundPreferenceCodec.decodeCustomLibrary(preferences[KEY_CUSTOM_LIBRARY])
    ).normalized()

    private companion object {
        val KEY_DEFAULT_SELECTION = stringPreferencesKey("default_soundscape_v1")
        val KEY_LAST_SELECTION = stringPreferencesKey("last_soundscape_v1")
        val KEY_VOLUME = intPreferencesKey("primary_volume_percent")
        val KEY_FAVORITES = stringSetPreferencesKey("favorite_catalog_ids")
        val KEY_RECENTS = stringPreferencesKey("recent_selections_v1")
        val KEY_CUSTOM_LIBRARY = stringPreferencesKey("custom_library_v1")

        fun taskOverrideKey(taskId: Int) = stringPreferencesKey("task_${taskId}_soundscape_v1")

        fun requireValidTaskId(taskId: Int) {
            require(taskId > 0) { "A focus sound override requires a persisted task id" }
        }
    }
}

/** Versioned, corruption-tolerant codec kept independent of Android Uri and JSON. */
internal object FocusSoundPreferenceCodec {
    private const val VERSION = "1"
    private const val FIELD_SEPARATOR = "|"
    private const val RECENT_SEPARATOR = "\n"

    fun encodeSoundscape(selection: FocusSoundscapeSelection): String {
        val safe = selection.normalized()
        return listOf(
            VERSION,
            encodeText(encodeSelection(safe.primary)),
            encodeText(safe.secondaryLayerId),
            safe.secondaryVolumePercent.toString(),
            if (safe.playDuringRecovery) "1" else "0"
        ).joinToString(FIELD_SEPARATOR)
    }

    fun decodeSoundscape(raw: String?): FocusSoundscapeSelection? = runCatching {
        val fields = raw?.split(FIELD_SEPARATOR) ?: return null
        if (fields.size < 5 || fields[0] != VERSION) return null
        FocusSoundscapeSelection(
            primary = decodeSelection(decodeText(fields[1])) ?: return null,
            secondaryLayerId = decodeText(fields[2]),
            secondaryVolumePercent = fields[3].toIntOrNull()
                ?: FocusSoundRules.DEFAULT_SECONDARY_VOLUME_PERCENT,
            playDuringRecovery = fields[4] == "1"
        ).normalized()
    }.getOrNull()

    fun encodeRecents(items: List<FocusSoundSelection>): String =
        FocusSoundRules.normalizeRecents(items)
            .joinToString(RECENT_SEPARATOR, transform = ::encodeSelection)

    fun decodeRecents(raw: String?): List<FocusSoundSelection> {
        if (raw.isNullOrBlank()) return emptyList()
        return FocusSoundRules.normalizeRecents(
            raw.lineSequence().mapNotNull(::decodeSelection).toList()
        )
    }

    fun encodeCustomLibrary(files: List<CustomFocusSoundFile>): String =
        FocusSoundRules.normalizeCustomLibrary(files)
            .joinToString(RECENT_SEPARATOR) { file ->
                encodeSelection(FocusSoundSelection.custom(file))
            }

    fun decodeCustomLibrary(raw: String?): List<CustomFocusSoundFile> {
        if (raw.isNullOrBlank()) return emptyList()
        return FocusSoundRules.normalizeCustomLibrary(
            raw.lineSequence()
                .mapNotNull(::decodeSelection)
                .mapNotNull(FocusSoundSelection::customFile)
                .toList()
        )
    }

    internal fun encodeSelection(selection: FocusSoundSelection): String {
        val safe = selection.normalized()
        val file = safe.customFile
        return listOf(
            VERSION,
            encodeText(safe.catalogId),
            encodeText(file?.uriString),
            encodeText(file?.displayName),
            encodeText(file?.mimeType),
            file?.durationMillis?.toString().orEmpty(),
            file?.sizeBytes?.toString().orEmpty(),
            if (file?.persistablePermissionTaken == true) "1" else "0",
            encodeText(file?.artist),
            encodeText(file?.album),
            file?.addedAtMillis?.toString().orEmpty()
        ).joinToString(FIELD_SEPARATOR)
    }

    internal fun decodeSelection(raw: String?): FocusSoundSelection? = runCatching {
        val fields = raw?.split(FIELD_SEPARATOR) ?: return null
        if (fields.size < 8 || fields[0] != VERSION) return null
        val id = decodeText(fields[1]) ?: return null
        val customFile = decodeText(fields[2])?.let { uri ->
            CustomFocusSoundFile(
                uriString = uri,
                displayName = decodeText(fields[3]).orEmpty(),
                mimeType = decodeText(fields[4]),
                durationMillis = fields[5].toLongOrNull(),
                sizeBytes = fields[6].toLongOrNull(),
                persistablePermissionTaken = fields[7] == "1",
                artist = fields.getOrNull(8)?.let(::decodeText),
                album = fields.getOrNull(9)?.let(::decodeText),
                addedAtMillis = fields.getOrNull(10)?.toLongOrNull() ?: 0L
            )
        }
        FocusSoundSelection(id, customFile).normalized()
    }.getOrNull()

    private fun encodeText(value: String?): String {
        if (value == null) return ""
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeText(value: String): String? {
        if (value.isEmpty()) return null
        return runCatching {
            String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
        }.getOrNull()
    }
}
