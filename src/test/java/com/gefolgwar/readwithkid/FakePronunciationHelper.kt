package com.gefolgwar.readwithkid

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Has a voice for the languages in [voices]; a word stays being said until [finishSaying]. */
class FakePronunciationHelper(vararg voices: TextLanguage) : PronunciationHelper {
    val voices = TextLanguage.entries.associateWith { MutableStateFlow(it in voices) }
    val said = mutableListOf<Pair<String, TextLanguage>>()
    private val beingSaid = mutableListOf<() -> Unit>()
    var released = false

    override fun voiceAvailable(language: TextLanguage): Flow<Boolean> = voices.getValue(language)

    override fun speak(word: String, language: TextLanguage, onDone: () -> Unit) {
        said += word to language
        beingSaid += onDone
    }

    override fun release() {
        released = true
    }

    fun finishSaying() {
        beingSaid.toList().also { beingSaid.clear() }.forEach { it() }
    }
}
