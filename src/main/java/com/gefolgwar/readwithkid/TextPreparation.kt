package com.gefolgwar.readwithkid

import java.text.Normalizer

/** A word of the text: [display] is shown to the child, [normalized] is compared with recognised speech. */
data class Word(val display: String, val normalized: String)

data class PreparedText(val sentences: List<List<Word>>) {
    val words: List<Word> = sentences.flatten()
}

object TextPreparation {
    private val PARAGRAPH_BREAK = Regex("\\n[\\s\\u00A0]*\\n")
    // After . ! ? … optionally followed by closing quotes/brackets
    private val SENTENCE_END = Regex("(?<=[.!?…][»\"”)]{0,2})[\\s\\u00A0]+")
    private val WHITESPACE = Regex("[\\s\\u00A0]+")
    private val APOSTROPHES = Regex("['ʼ’‘`]")
    // Also drops combining marks such as the stress mark U+0301
    private val NOT_WORD_CHAR = Regex("[^\\p{L}\\p{N}-]")

    fun prepare(raw: String): PreparedText {
        val sentences = raw.split(PARAGRAPH_BREAK)
            .flatMap { it.trim().split(SENTENCE_END) }
            .map(::toWords)
            .filter { it.isNotEmpty() }
        return PreparedText(sentences)
    }

    fun normalize(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFC)
            .lowercase()
            .replace(APOSTROPHES, "")
            .replace(NOT_WORD_CHAR, "")
            .trim('-')

    /** Punctuation-only tokens (e.g. a dash) are not words: they stay attached to a neighbouring word for display. */
    private fun toWords(sentence: String): List<Word> {
        val words = mutableListOf<Word>()
        var prefix = ""
        for (token in sentence.split(WHITESPACE)) {
            if (token.isEmpty()) continue
            val normalized = normalize(token)
            when {
                normalized.isNotEmpty() -> {
                    words += Word(prefix + token, normalized)
                    prefix = ""
                }
                words.isNotEmpty() -> words[words.lastIndex] = words.last().let { it.copy(display = "${it.display} $token") }
                else -> prefix += "$token "
            }
        }
        return words
    }
}
