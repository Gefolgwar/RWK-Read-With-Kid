package com.gefolgwar.readwithkid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Says a word aloud, so the child hears how it is pronounced. */
interface PronunciationHelper {
    /** Whether the phone has a voice for [language]; without one there is no help. */
    fun voiceAvailable(language: TextLanguage): Flow<Boolean>

    /** Says [word]; [onDone] is called on the main thread once it is said, interrupted or failed. */
    fun speak(word: String, language: TextLanguage, onDone: () -> Unit)

    fun release()
}

/** The phone's text-to-speech. */
class TtsPronunciationHelper(context: Context) : PronunciationHelper {
    private val mainHandler = Handler(Looper.getMainLooper())
    /** Null until the speech service has started, false if it could not. */
    private val ready = MutableStateFlow<Boolean?>(null)
    private val beingSaid = ConcurrentHashMap<String, () -> Unit>()
    private var nextId = 0
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.value = status == TextToSpeech.SUCCESS }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = finished(utteranceId)
            override fun onStop(utteranceId: String, interrupted: Boolean) = finished(utteranceId)
            override fun onError(utteranceId: String, errorCode: Int) = finished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finished(utteranceId)
        })
    }

    override fun voiceAvailable(language: TextLanguage): Flow<Boolean> =
        ready.map { it == true && tts.isLanguageAvailable(language.locale) >= TextToSpeech.LANG_AVAILABLE }
            .distinctUntilChanged()

    override fun speak(word: String, language: TextLanguage, onDone: () -> Unit) {
        // A new word interrupts the one being said; not every engine reports that, so it is finished here.
        beingSaid.keys.toList().forEach(::finished)
        val id = (nextId++).toString()
        beingSaid[id] = onDone
        tts.setLanguage(language.locale)
        if (tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finished(id)
    }

    override fun release() {
        beingSaid.clear()
        tts.stop()
        tts.shutdown()
    }

    private fun finished(utteranceId: String) {
        val onDone = beingSaid.remove(utteranceId) ?: return
        mainHandler.post(onDone)
    }

    private val TextLanguage.locale: Locale
        get() = when (this) {
            TextLanguage.UK -> Locale.forLanguageTag("uk-UA")
            TextLanguage.DE -> Locale.GERMANY
        }
}
