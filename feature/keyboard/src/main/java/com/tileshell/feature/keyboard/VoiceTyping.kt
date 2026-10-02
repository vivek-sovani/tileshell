package com.tileshell.feature.keyboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Where voice typing is (canvas "Voice typing"). */
sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState

    /** Something went wrong; [message] says what, in plain words. */
    data class Problem(val message: String) : VoiceState
}

/**
 * Voice typing through Android's own speech recognition (Google's on most
 * phones), in the keyboard's current language — English (India), मराठी or
 * हिन्दी. Prefers recognising on the phone when its offline voice pack is
 * installed. What's heard so far shows as it's spoken ([partial]); the final
 * text goes to [onText]. Needs the microphone permission, which a keyboard
 * can't ask for itself: [VoicePermissionActivity] asks.
 */
class VoiceTyping(private val context: Context, private val onText: (String) -> Unit) {

    var state by mutableStateOf<VoiceState>(VoiceState.Idle)
        private set
    var partial by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null
    private var language = KeyboardLanguage.ENGLISH

    /** Offline first; if the phone has no offline pack for the language, online. */
    private var offline = true

    val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start(language: KeyboardLanguage) {
        this.language = language
        offline = true
        listen()
    }

    private fun listen() {
        if (!hasPermission) {
            state = VoiceState.Problem("allow the microphone for voice typing")
            runCatching {
                context.startActivity(
                    Intent(context, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            state = VoiceState.Problem("no speech recognition on this phone")
            return
        }
        stop()
        partial = ""
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeFor(language))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        state = VoiceState.Listening
        runCatching { r.startListening(intent) }.onFailure {
            state = VoiceState.Problem("voice typing couldn't start")
        }
    }

    /** Tap while listening: stop, and put in what was heard. */
    fun finish() {
        runCatching { recognizer?.stopListening() }
    }

    /** Leaving the panel: stop without typing anything. */
    fun stop() {
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
        if (state == VoiceState.Listening) state = VoiceState.Idle
        partial = ""
    }

    fun clearProblem() {
        if (state is VoiceState.Problem) state = VoiceState.Idle
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            partial = ""
            state = VoiceState.Idle
            if (text.isNotEmpty()) onText(text)
            runCatching { recognizer?.destroy() }
            recognizer = null
        }

        override fun onError(error: Int) {
            partial = ""
            // No offline voice pack for this language: try again online, once.
            val noPack = error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
            if (noPack && offline) {
                offline = false
                runCatching { recognizer?.destroy() }
                recognizer = null
                listen()
                return
            }
            state = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceState.Problem("didn't catch that — tap to try again")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    VoiceState.Problem("no connection — download the offline voice pack to type without one")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceState.Problem("allow the microphone for voice typing")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    VoiceState.Problem("this language isn't available for voice typing here")
                SpeechRecognizer.ERROR_CLIENT -> VoiceState.Idle
                else -> VoiceState.Problem("voice typing stopped — tap to try again")
            }
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
    }

    companion object {
        fun localeFor(language: KeyboardLanguage): String = when (language) {
            KeyboardLanguage.ENGLISH -> "en-IN"
            KeyboardLanguage.MARATHI -> "mr-IN"
            KeyboardLanguage.HINDI -> "hi-IN"
        }

        /** The panel's language line: "English (India)", "मराठी", "हिन्दी". */
        fun labelFor(language: KeyboardLanguage): String =
            if (language == KeyboardLanguage.ENGLISH) "English (India)" else language.nativeName
    }
}

/**
 * Asks for the microphone, which a keyboard can't do itself, then closes; the
 * next tap on the keyboard's mic starts listening.
 */
class VoicePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }
}
