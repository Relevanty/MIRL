package com.personal.sleepalarm.service.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.personal.sleepalarm.R
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.domain.model.FocusProtocolPhase
import com.personal.sleepalarm.service.AppNotificationChannelIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lifecycle shell for the process-wide controller.
 *
 * It does not create a second notification. `startForeground()` adopts the active focus
 * protocol notification (same id); its small fallback is only used in the narrow race before
 * FocusProtocolManager has posted that notification.
 */
class FocusSoundscapeService : Service() {
    private lateinit var controller: FocusSoundscapeController
    private var foregroundNotificationId: Int? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var commandGeneration = 0L
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        controller = FocusSoundscapeController.get(this)
        ensureChannel()
        activeInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getIntExtra(EXTRA_SESSION_ID, 0)?.takeIf { it > 0 }
        val action = intent?.action
        if (sessionId == null || action !in PLAYBACK_ACTIONS) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // A newly created FGS must enter foreground promptly. A running instance keeps its
        // already validated notification until the asynchronous Room check accepts the new id.
        if (foregroundNotificationId == null) {
            adoptFocusNotification(notificationId(sessionId))
        }
        val generation = ++commandGeneration
        val requestedFadeMs = intent.getLongExtra(EXTRA_FADE_MS, DEFAULT_FADE_MS)
        serviceScope.launch {
            val sessionResult = runCatching {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(applicationContext)
                        .focusProtocolDao()
                        .getById(sessionId)
                }
            }
            if (destroyed || generation != commandGeneration) return@launch
            if (sessionResult.isFailure) {
                Log.e(TAG, "Unable to validate focus sound session=$sessionId", sessionResult.exceptionOrNull())
                rejectPlaybackCommand(sessionId, startId, removeNotification = false)
                return@launch
            }
            val session = sessionResult.getOrNull()

            if (session == null) {
                rejectPlaybackCommand(sessionId, startId, removeNotification = true)
                return@launch
            }
            if (!allowsFocusSoundscapePlayback(
                    phase = session.phase,
                    playDuringRecovery = session.soundscapePlayDuringRecovery,
                )
            ) {
                rejectPlaybackCommand(
                    sessionId = sessionId,
                    startId = startId,
                    removeNotification = session.phase.isTerminal,
                )
                return@launch
            }

            val canonicalMix = session.soundscapeMix()
            if (canonicalMix.isSilent) {
                rejectPlaybackCommand(sessionId, startId, removeNotification = false)
                return@launch
            }

            val validatedNotificationId = notificationId(sessionId)
            if (foregroundNotificationId != validatedNotificationId) {
                adoptFocusNotification(validatedNotificationId)
            }
            controller.bindFocusSession(sessionId)
            when (action) {
                ACTION_PLAY -> controller.playInProcess(canonicalMix, requestedFadeMs)
                ACTION_REMEMBER_MIX -> when (controller.state.value.status) {
                    FocusSoundPlaybackStatus.STOPPED -> {
                        // On process recreation the intent is only a wake-up hint. Room is the
                        // canonical source, so a stale serialized mix can never be resurrected.
                        controller.playInProcess(canonicalMix, DEFAULT_FADE_MS)
                    }
                    FocusSoundPlaybackStatus.PLAYING,
                    FocusSoundPlaybackStatus.LOADING -> Unit
                    FocusSoundPlaybackStatus.PAUSED,
                    FocusSoundPlaybackStatus.ERROR -> rejectPlaybackCommand(
                        sessionId,
                        startId,
                        removeNotification = false,
                    )
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        commandGeneration++
        serviceJob.cancel()
        if (activeInstance === this) activeInstance = null
        if (controller.state.value.status == FocusSoundPlaybackStatus.PLAYING ||
            controller.state.value.status == FocusSoundPlaybackStatus.LOADING
        ) {
            // Invalidate an in-flight prepare before dropping foreground ownership. A late
            // MediaPlayer/AudioTrack must never start from a destroyed service.
            controller.stopImmediatelyFromService(clearSession = false)
        }
        foregroundNotificationId?.let {
            // The focus notification remains owned by NotificationManager and continues to show
            // the timer/actions after background audio pauses or stops.
            stopForeground(STOP_FOREGROUND_DETACH)
        }
        foregroundNotificationId = null
        super.onDestroy()
    }

    private fun rejectPlaybackCommand(
        sessionId: Int,
        startId: Int,
        removeNotification: Boolean,
    ) {
        val boundSessionId = controller.boundFocusSessionId()
        if (boundSessionId != null && boundSessionId != sessionId) return
        if (boundSessionId == sessionId) {
            controller.stopImmediatelyFromService(clearSession = true)
        }
        val rejectedNotificationId = notificationId(sessionId)
        if (foregroundNotificationId == rejectedNotificationId) {
            stopForeground(
                if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH
            )
            foregroundNotificationId = null
            stopSelfResult(startId)
        }
    }

    private fun stopForTerminalNow(sessionId: Int): Boolean {
        val targetNotificationId = notificationId(sessionId)
        val ownsForeground = foregroundNotificationId == targetNotificationId
        val boundSessionId = controller.boundFocusSessionId()
        if (boundSessionId != null && boundSessionId != sessionId) return false
        val ownsPlayback = boundSessionId == sessionId
        if (!ownsForeground && !ownsPlayback) return false

        commandGeneration++
        controller.stopImmediatelyFromService(clearSession = true)
        if (ownsForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundNotificationId = null
        }
        stopSelf()
        return true
    }

    private fun adoptFocusNotification(notificationId: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        val existing = runCatching {
            manager.activeNotifications
                .firstOrNull { it.id == notificationId && it.packageName == packageName }
                ?.notification
        }.getOrNull()
        val notification = existing ?: fallbackNotification()
        ServiceCompat.startForeground(
            this,
            notificationId,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
        foregroundNotificationId = notificationId
    }

    private fun fallbackNotification(): Notification =
        NotificationCompat.Builder(this, AppNotificationChannelIds.FOCUS_PROTOCOL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.focus_protocol_phase_focus))
            .setContentText(getString(R.string.focus_protocol_channel_name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                AppNotificationChannelIds.FOCUS_PROTOCOL,
                getString(R.string.focus_protocol_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            }
        )
    }

    companion object {
        private const val ACTION_PLAY = "com.personal.sleepalarm.focus.sound.PLAY"
        private const val ACTION_REMEMBER_MIX = "com.personal.sleepalarm.focus.sound.REMEMBER"
        private const val EXTRA_SESSION_ID = "focus_sound_session_id"
        private const val EXTRA_FADE_MS = "focus_sound_fade_ms"
        private const val NOTIFICATION_BASE = 680_000
        private const val DEFAULT_FADE_MS = 700L
        private const val TAG = "FocusSoundscapeService"
        private val PLAYBACK_ACTIONS = setOf(ACTION_PLAY, ACTION_REMEMBER_MIX)

        @Volatile
        private var activeInstance: FocusSoundscapeService? = null

        fun play(
            context: Context,
            sessionId: Int,
            mix: FocusSoundMix,
            fadeMs: Long,
        ) {
            val intent = Intent(context, FocusSoundscapeService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_FADE_MS, fadeMs)
                putMix(mix)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun rememberMix(context: Context, sessionId: Int, mix: FocusSoundMix) {
            val intent = Intent(context, FocusSoundscapeService::class.java).apply {
                action = ACTION_REMEMBER_MIX
                putExtra(EXTRA_SESSION_ID, sessionId)
                putMix(mix)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Removes only FGS ownership; STOP_FOREGROUND_DETACH preserves the focus notification. */
        fun detach(context: Context) {
            context.stopService(Intent(context, FocusSoundscapeService::class.java))
        }

        fun stop(context: Context) = detach(context)

        /**
         * Synchronously removes foreground ownership on the main thread before returning. The
         * caller may safely cancel the same notification id afterwards; removal here is already
         * idempotent and also covers a deferred FGS notification.
         */
        suspend fun stopForTerminal(context: Context, sessionId: Int): Boolean =
            withContext(Dispatchers.Main.immediate) {
                val live = activeInstance
                if (live != null) {
                    live.stopForTerminalNow(sessionId)
                } else {
                    val controller = FocusSoundscapeController.get(context.applicationContext)
                    if (controller.boundFocusSessionId() == sessionId) {
                        controller.stopImmediatelyFromService(clearSession = true)
                        context.stopService(Intent(context, FocusSoundscapeService::class.java))
                        true
                    } else {
                        false
                    }
                }
            }

        private fun notificationId(sessionId: Int): Int = NOTIFICATION_BASE + sessionId
    }
}

internal fun allowsFocusSoundscapePlayback(
    phase: FocusProtocolPhase,
    playDuringRecovery: Boolean,
): Boolean = phase == FocusProtocolPhase.FOCUS ||
    (phase == FocusProtocolPhase.RECOVERY && playDuringRecovery)

private const val KEY_MASTER = "focus_mix_master"
private const val KEY_PRIMARY_PRESENT = "focus_mix_primary_present"
private const val KEY_PRIMARY_TYPE = "focus_mix_primary_type"
private const val KEY_PRIMARY_ID = "focus_mix_primary_id"
private const val KEY_PRIMARY_ASSET = "focus_mix_primary_asset"
private const val KEY_PRIMARY_ROLE = "focus_mix_primary_role"
private const val KEY_PRIMARY_SEED = "focus_mix_primary_seed"
private const val KEY_PRIMARY_COLOR = "focus_mix_primary_color"
private const val KEY_PRIMARY_URI = "focus_mix_primary_uri"
private const val KEY_PRIMARY_VOLUME = "focus_mix_primary_volume"
private const val KEY_NOISE_PRESENT = "focus_mix_noise_present"
private const val KEY_NOISE_COLOR = "focus_mix_noise_color"
private const val KEY_NOISE_SEED = "focus_mix_noise_seed"
private const val KEY_NOISE_VOLUME = "focus_mix_noise_volume"

private fun Intent.putMix(mix: FocusSoundMix) {
    val normalized = mix.normalized()
    putExtra(KEY_MASTER, normalized.masterVolume)
    normalized.primary?.let { layer ->
        putExtra(KEY_PRIMARY_PRESENT, true)
        putExtra(KEY_PRIMARY_VOLUME, layer.volume)
        when (val source = layer.source) {
            FocusSoundSource.Silence -> putExtra(KEY_PRIMARY_TYPE, "silence")
            is FocusSoundSource.Noise -> {
                putExtra(KEY_PRIMARY_TYPE, "noise")
                putExtra(KEY_PRIMARY_COLOR, source.color.name)
                putExtra(KEY_PRIMARY_SEED, source.seed)
            }
            is FocusSoundSource.Bundled -> {
                putExtra(KEY_PRIMARY_TYPE, "bundled")
                putExtra(KEY_PRIMARY_ID, source.stableId)
                putExtra(KEY_PRIMARY_ASSET, source.assetPath)
                putExtra(KEY_PRIMARY_ROLE, source.role.name)
                putExtra(KEY_PRIMARY_SEED, source.seed)
            }
            is FocusSoundSource.CustomFile -> {
                putExtra(KEY_PRIMARY_TYPE, "custom")
                putExtra(KEY_PRIMARY_ID, source.stableId)
                putExtra(KEY_PRIMARY_URI, source.uri)
            }
        }
    }
    normalized.noise?.let { layer ->
        val source = layer.source as? FocusSoundSource.Noise ?: return@let
        putExtra(KEY_NOISE_PRESENT, true)
        putExtra(KEY_NOISE_COLOR, source.color.name)
        putExtra(KEY_NOISE_SEED, source.seed)
        putExtra(KEY_NOISE_VOLUME, layer.volume)
    }
}

private fun Intent.readMix(): FocusSoundMix? {
    if (!hasExtra(KEY_MASTER)) return null
    val primary = if (getBooleanExtra(KEY_PRIMARY_PRESENT, false)) {
        val source = when (getStringExtra(KEY_PRIMARY_TYPE)) {
            "noise" -> FocusSoundSource.Noise(
                color = enumValueOrNull<FocusNoiseColor>(getStringExtra(KEY_PRIMARY_COLOR))
                    ?: FocusNoiseColor.BROWN,
                seed = getIntExtra(KEY_PRIMARY_SEED, FocusNoiseColor.BROWN.name.hashCode()),
            )
            "bundled" -> {
                val id = getStringExtra(KEY_PRIMARY_ID) ?: return null
                FocusSoundSource.Bundled(
                    stableId = id,
                    assetPath = getStringExtra(KEY_PRIMARY_ASSET),
                    role = enumValueOrNull<FocusProceduralRole>(getStringExtra(KEY_PRIMARY_ROLE))
                        ?: FocusProceduralRole.AMBIENCE,
                    seed = getIntExtra(KEY_PRIMARY_SEED, id.hashCode()),
                )
            }
            "custom" -> {
                val uri = getStringExtra(KEY_PRIMARY_URI) ?: return null
                FocusSoundSource.CustomFile(
                    uri = uri,
                    stableId = getStringExtra(KEY_PRIMARY_ID) ?: "custom",
                )
            }
            else -> FocusSoundSource.Silence
        }
        FocusSoundLayerSelection(source, getFloatExtra(KEY_PRIMARY_VOLUME, 0.35f))
    } else {
        null
    }
    val noise = if (getBooleanExtra(KEY_NOISE_PRESENT, false)) {
        val color = enumValueOrNull<FocusNoiseColor>(getStringExtra(KEY_NOISE_COLOR))
            ?: FocusNoiseColor.BROWN
        FocusSoundLayerSelection(
            FocusSoundSource.Noise(color, getIntExtra(KEY_NOISE_SEED, color.name.hashCode())),
            getFloatExtra(KEY_NOISE_VOLUME, 0.20f),
        )
    } else {
        null
    }
    return FocusSoundMix(
        primary = primary,
        noise = noise,
        masterVolume = getFloatExtra(KEY_MASTER, FocusSoundMix.DEFAULT_MASTER_VOLUME),
    ).normalized()
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
