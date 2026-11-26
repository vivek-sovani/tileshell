package com.tileshell.feature.livetiles.health

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * The beep (and, when asked, the spoken time) at each kilometre of a walk. Plays as navigation guidance so music
 * ducks for a moment and comes back; needs no permission. Text-to-speech is created when first needed and let go when
 * the walk ends.
 */
class WalkAnnouncer(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayList<String>()
    private var focus: AudioFocusRequest? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun announce(text: String, voice: Boolean) {
        main.post {
            takeFocus()
            beep()
            if (!voice) {
                main.postDelayed({ dropFocus() }, 700L)
                return@post
            }
            main.postDelayed({ speak(text) }, 650L)
        }
    }

    fun shutdown() {
        main.post {
            runCatching { tts?.stop(); tts?.shutdown() }
            tts = null
            ready = false
            pending.clear()
            dropFocus()
        }
    }

    private fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            main.postDelayed({ runCatching { tone.release() } }, 700L)
        }
    }

    private fun speak(text: String) {
        val t = tts
        if (t == null) {
            pending += text
            tts = TextToSpeech(app) { status ->
                main.post {
                    ready = status == TextToSpeech.SUCCESS
                    val engine = tts
                    if (ready && engine != null) {
                        runCatching {
                            engine.language = Locale.getDefault()
                            engine.setAudioAttributes(attrs)
                            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) = Unit
                                override fun onDone(utteranceId: String?) { main.post { dropFocus() } }
                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) { main.post { dropFocus() } }
                            })
                        }
                        pending.toList().also { pending.clear() }.forEach { say(engine, it) }
                    } else {
                        pending.clear()
                        dropFocus()
                    }
                }
            }
        } else if (ready) say(t, text) else pending += text
    }

    private fun say(engine: TextToSpeech, text: String) {
        runCatching { engine.speak(text, TextToSpeech.QUEUE_ADD, null, "km-${System.nanoTime()}") }
            .onFailure { dropFocus() }
    }

    private fun takeFocus() {
        val am = audio ?: return
        if (focus != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
        focus = req
        runCatching { am.requestAudioFocus(req) }
    }

    private fun dropFocus() {
        val am = audio ?: return
        focus?.let { runCatching { am.abandonAudioFocusRequest(it) } }
        focus = null
    }
}
