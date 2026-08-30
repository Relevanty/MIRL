package com.personal.sleepalarm.ui.english

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.personal.sleepalarm.service.audio.AppAudioAttributes
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OfflineTtsAvailability {
    INITIALIZING,
    READY,
    OFFLINE_VOICE_MISSING,
    ERROR
}

class OfflineEnglishTextToSpeech(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _availability = MutableStateFlow(OfflineTtsAvailability.INITIALIZING)
    val availability: StateFlow<OfflineTtsAvailability> = _availability.asStateFlow()

    private var engine: TextToSpeech? = null
    private val utteranceCounter = AtomicLong(0L)
    private val initializationGuard = EnglishAudioGenerationGuard()
    @Volatile
    private var closed = false
    @Volatile
    private var initializationFinished = false
    @Volatile
    private var initializationSucceeded = false

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        if (closed) return
        val token = initializationGuard.next()
        initializationFinished = false
        initializationSucceeded = false
        _availability.value = OfflineTtsAvailability.INITIALIZING
        val created = runCatching {
            TextToSpeech(appContext) { status ->
                mainHandler.post {
                    if (closed || !initializationGuard.isCurrent(token)) return@post
                    configureEngine(status)
                }
            }
        }.getOrElse {
            initializationFinished = true
            _availability.value = OfflineTtsAvailability.ERROR
            null
        }
        engine = created
    }

    fun speak(word: String): Boolean {
        val tts = engine ?: return false
        if (closed || _availability.value != OfflineTtsAvailability.READY) return false
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
        }
        val spoken = runCatching {
            tts.speak(
                word,
                TextToSpeech.QUEUE_FLUSH,
                params,
                "mirl-english-${utteranceCounter.incrementAndGet()}"
            ) != TextToSpeech.ERROR
        }.getOrDefault(false)
        if (!spoken) _availability.value = OfflineTtsAvailability.ERROR
        return spoken
    }

    fun stop() {
        runCatching { engine?.stop() }
    }

    /** Re-checks voices after returning from Android settings without leaking another engine. */
    fun refreshAvailability() {
        if (closed) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshAvailability() }
            return
        }
        if (!initializationFinished) return
        if (!initializationSucceeded) {
            initializationGuard.invalidate()
            runCatching { engine?.shutdown() }
            engine = null
            initializeEngine()
            return
        }
        engine?.let(::configureOfflineVoice)
    }

    private fun configureEngine(status: Int) {
        if (closed) return
        initializationFinished = true
        initializationSucceeded = status == TextToSpeech.SUCCESS
        if (status != TextToSpeech.SUCCESS) {
            _availability.value = OfflineTtsAvailability.ERROR
            return
        }
        engine?.let(::configureOfflineVoice)
    }

    private fun configureOfflineVoice(tts: TextToSpeech) {
        if (closed) return
        val voiceResult = runCatching {
            tts.voices
                .asSequence()
                .filter { voice ->
                    voice.locale.language.equals(Locale.ENGLISH.language, ignoreCase = true) &&
                        !voice.isNetworkConnectionRequired
                }
                .sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }.thenBy { it.latency })
                .firstOrNull()
        }
        if (voiceResult.isFailure) {
            _availability.value = OfflineTtsAvailability.ERROR
            return
        }
        val offlineVoice = voiceResult.getOrNull()
        if (offlineVoice == null) {
            _availability.value = OfflineTtsAvailability.OFFLINE_VOICE_MISSING
            return
        }
        val configured = runCatching {
            tts.setAudioAttributes(AppAudioAttributes.speech) != TextToSpeech.ERROR &&
                tts.setVoice(offlineVoice) != TextToSpeech.ERROR &&
                tts.setSpeechRate(0.86f) != TextToSpeech.ERROR &&
                tts.setPitch(1f) != TextToSpeech.ERROR
        }.getOrDefault(false)
        _availability.value = if (configured) {
            OfflineTtsAvailability.READY
        } else {
            OfflineTtsAvailability.ERROR
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        initializationGuard.invalidate()
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }
}

/** Opens Android's local TTS configuration without ever starting a network flow in MIRL. */
fun openOfflineTextToSpeechSettings(context: Context): Boolean {
    val intents = listOf(
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    )
    return intents.any { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return@any false
        runCatching { context.startActivity(intent) }.isSuccess
    }
}

enum class OnDeviceRecognitionAvailability {
    AVAILABLE,
    UNSUPPORTED
}

enum class OnDeviceRecognitionError {
    NO_MATCH,
    SPEECH_TIMEOUT,
    AUDIO,
    PERMISSION,
    BUSY,
    SERVICE_UNAVAILABLE,
    START_FAILED
}

data class OnDeviceRecognitionState(
    val availability: OnDeviceRecognitionAvailability,
    val isListening: Boolean = false,
    val partialText: String = "",
    val error: OnDeviceRecognitionError? = null
)

/** Pure monotonically increasing guard used to reject callbacks from old sessions. */
internal class EnglishAudioGenerationGuard {
    private var generation = 0L

    fun next(): Long = ++generation

    fun invalidate() {
        generation++
    }

    fun isCurrent(token: Long): Boolean = token == generation
}

class OnDeviceEnglishSpeechRecognizer(
    context: Context,
    private val onFinalResults: (List<String>) -> Unit
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(
        OnDeviceRecognitionState(
            availability = if (isAvailable(appContext)) {
                OnDeviceRecognitionAvailability.AVAILABLE
            } else {
                OnDeviceRecognitionAvailability.UNSUPPORTED
            }
        )
    )
    val state: StateFlow<OnDeviceRecognitionState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private val callbackGuard = EnglishAudioGenerationGuard()
    @Volatile
    private var closed = false

    fun startListening(): Boolean {
        if (closed ||
            _state.value.availability != OnDeviceRecognitionAvailability.AVAILABLE ||
            _state.value.isListening
        ) {
            return false
        }

        val token = callbackGuard.next()
        return runCatching {
            val speechRecognizer = recognizer ?: createRecognizer().also { recognizer = it }
            speechRecognizer.setRecognitionListener(listenerFor(token))
            _state.value = _state.value.copy(
                isListening = true,
                partialText = "",
                error = null
            )
            speechRecognizer.startListening(recognitionIntent())
            true
        }.getOrElse { throwable ->
            callbackGuard.invalidate()
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            _state.value = _state.value.copy(
                availability = if (throwable is UnsupportedOperationException) {
                    OnDeviceRecognitionAvailability.UNSUPPORTED
                } else {
                    _state.value.availability
                },
                isListening = false,
                error = throwable.toRecognitionError()
            )
            false
        }
    }

    fun stopListening() {
        if (closed || !_state.value.isListening) return
        runCatching { recognizer?.stopListening() }
            .onFailure { throwable ->
                callbackGuard.invalidate()
                _state.value = _state.value.copy(
                    isListening = false,
                    error = throwable.toRecognitionError()
                )
            }
    }

    fun cancelListening() {
        callbackGuard.invalidate()
        runCatching { recognizer?.cancel() }
        _state.value = _state.value.copy(
            isListening = false,
            partialText = "",
            error = null
        )
    }

    private fun createRecognizer(): SpeechRecognizer {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw UnsupportedOperationException("On-device recognition requires Android 12+")
        }
        return SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext).also {
            recognizer = it
        }
    }

    private fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    private fun listenerFor(token: Long): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = updateIfCurrent(token) {
            it.copy(isListening = true, error = null)
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (!canAccept(token)) return
            callbackGuard.invalidate()
            _state.value = _state.value.copy(
                isListening = false,
                error = recognitionError(error)
            )
        }

        override fun onResults(results: Bundle?) {
            if (!canAccept(token)) return
            val values = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            callbackGuard.invalidate()
            _state.value = _state.value.copy(
                isListening = false,
                partialText = values.firstOrNull().orEmpty(),
                error = null
            )
            onFinalResults(values)
        }

        override fun onPartialResults(partialResults: Bundle?) = updateIfCurrent(token) {
            it.copy(
                partialText = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
            )
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun canAccept(token: Long): Boolean = !closed && callbackGuard.isCurrent(token)

    private inline fun updateIfCurrent(
        token: Long,
        transform: (OnDeviceRecognitionState) -> OnDeviceRecognitionState
    ) {
        if (canAccept(token)) _state.value = transform(_state.value)
    }

    override fun close() {
        if (closed) return
        closed = true
        callbackGuard.invalidate()
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        _state.value = _state.value.copy(isListening = false, partialText = "", error = null)
    }

    companion object {
        fun isAvailable(context: Context): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            }.getOrDefault(false)
        }

        internal fun recognitionError(error: Int): OnDeviceRecognitionError = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> OnDeviceRecognitionError.NO_MATCH
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> OnDeviceRecognitionError.SPEECH_TIMEOUT
            SpeechRecognizer.ERROR_AUDIO -> OnDeviceRecognitionError.AUDIO
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> OnDeviceRecognitionError.PERMISSION
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> OnDeviceRecognitionError.BUSY
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> OnDeviceRecognitionError.SERVICE_UNAVAILABLE
            else -> OnDeviceRecognitionError.START_FAILED
        }

        private fun Throwable.toRecognitionError(): OnDeviceRecognitionError = when (this) {
            is SecurityException -> OnDeviceRecognitionError.PERMISSION
            is UnsupportedOperationException -> OnDeviceRecognitionError.SERVICE_UNAVAILABLE
            else -> OnDeviceRecognitionError.START_FAILED
        }
    }
}
