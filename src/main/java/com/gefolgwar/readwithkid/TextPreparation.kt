package com.gefolgwar.readwithkid

import java.text.Normalizer

/** A word of the text: [display] is shown to the child, [normalized] is compared with recognised speech. */
data class Word(val display: String, val normalized: String)

data class PreparedText(val sentences: List<List<Word>>) {
    val words: List<Word> = sentences.flatten()
}

/** Parts of a text that speech recognition handles poorly; parents should write them out as words. */
data class TextWarnings(
    val digits: List<String>,
    val abbreviations: List<String>,
    val symbols: List<String>
) {
    val isEmpty: Boolean get() = digits.isEmpty() && abbreviations.isEmpty() && symbols.isEmpty()
}

object TextPreparation {
    private val PARAGRAPH_BREAK = Regex("\\n[\\s\\u00A0]*\\n")
    // After . ! ? … optionally followed by closing quotes/brackets (German closes with “ and ‘)
    private val SENTENCE_END = Regex("(?<=[.!?…][»\"”“‘)]{0,2})[\\s\\u00A0]+")
    private val WHITESPACE = Regex("[\\s\\u00A0]+")
    private val APOSTROPHES = Regex("['ʼ’‘`]")
    // Also drops combining marks such as the stress mark U+0301, and hyphens:
    // a hyphenated word may be recognised as its parts ("Baden-Württemberg" -> "baden württemberg").
    private val NOT_WORD_CHAR = Regex("[^\\p{L}\\p{N}]")
    // German: ß and ss are the same; umlauts match their ae/oe/ue spelling.
    private val GERMAN_SPELLING = listOf("ß" to "ss", "ä" to "ae", "ö" to "oe", "ü" to "ue")

    fun prepare(raw: String): PreparedText {
        val sentences = raw.split(PARAGRAPH_BREAK)
            .flatMap { it.trim().split(SENTENCE_END) }
            .map(::toWords)
            .filter { it.isNotEmpty() }
        return PreparedText(sentences)
    }

    private const val TITLE_WORDS = 5

    /** Title of a text: its first words, cut with "…" when the text goes on. */
    fun autoTitle(raw: String): String {
        val words = prepare(raw).words
        val title = words.take(TITLE_WORDS).joinToString(" ") { it.display }.trimEnd { it in ".,!?…:;-–—" }
        return if (words.size > TITLE_WORDS) "$title…" else title
    }

    // e.g. "т.д.", "z.B."
    private val DOTTED_ABBREVIATION = Regex("\\p{L}{1,3}\\.\\p{L}{1,3}\\.?")
    private const val ORDINARY_PUNCTUATION = ".,!?…:;-‐–—'’‘`\"«»„“”‚()[]"

    fun findHardParts(raw: String): TextWarnings {
        val digits = linkedSetOf<String>()
        val abbreviations = linkedSetOf<String>()
        val symbols = linkedSetOf<String>()
        for (token in raw.split(WHITESPACE)) {
            if (token.isEmpty()) continue
            if (token.any { it.isDigit() }) digits += token.trim { !it.isLetterOrDigit() }
            val core = token.trim { !it.isLetterOrDigit() && it != '.' }
            val word = core.trim('.')
            when {
                DOTTED_ABBREVIATION.matches(core) -> abbreviations += core
                word.count { it.isLetter() } >= 2 && word.filter { it.isLetter() }.all { it.isUpperCase() } -> abbreviations += word
            }
            token.filterNot {
                it.isLetterOrDigit() || Character.getType(it) == Character.NON_SPACING_MARK.toInt() || it in ORDINARY_PUNCTUATION
            }.forEach { symbols += it.toString() }
        }
        return TextWarnings(digits.toList(), abbreviations.toList(), symbols.toList())
    }

    fun normalize(word: String): String =
        GERMAN_SPELLING.fold(
            Normalizer.normalize(word, Normalizer.Form.NFC)
                .lowercase()
                .replace(APOSTROPHES, "")
                .replace(NOT_WORD_CHAR, "")
        ) { result, (from, to) -> result.replace(from, to) }

    /** Suggests the language from the alphabet: mostly Cyrillic is Ukrainian, mostly Latin is German; null without letters. */
    fun detectLanguage(raw: String): TextLanguage? {
        var cyrillic = 0
        var latin = 0
        for (c in raw) {
            if (!c.isLetter()) continue
            when (Character.UnicodeScript.of(c.code)) {
                Character.UnicodeScript.CYRILLIC -> cyrillic++
                Character.UnicodeScript.LATIN -> latin++
                else -> Unit
            }
        }
        return when {
            cyrillic == 0 && latin == 0 -> null
            cyrillic >= latin -> TextLanguage.UK
            else -> TextLanguage.DE
        }
    }

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
