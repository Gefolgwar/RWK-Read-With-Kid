package com.gefolgwar.readwithkid

/**
 * Follows a child reading [words] aloud. Repeats, reading by syllables, partial words and
 * self-corrections are not errors; a skipped word is passed over and remembered as skipped.
 */
class WordAligner(private val words: List<String>) {

    private data class Progress(
        /** Last word passed (read or skipped), or -1. */
        val pointer: Int = -1,
        val matched: Set<Int> = emptySet(),
        val skipped: Set<Int> = emptySet(),
        /** Words read correctly after a wrong attempt. */
        val retried: Set<Int> = emptySet(),
        /** The next expected word already got a wrong attempt. */
        val wrongAttempt: Boolean = false,
        /** Trailing recognised words that need more speech to resolve: unfinished syllables or an unconfirmed jump. */
        val pending: List<String> = emptyList()
    )

    private val normalizedWords = words.map { TextPreparation.normalize(it) }
    private var confirmed = Progress()
    private var tentative: Progress? = null

    val confirmedIndex: Int
        get() = confirmed.pointer

    val tentativeIndex: Int
        get() = tentative?.pointer ?: -1

    // Words actually recognised; words passed over by the look-ahead are not counted.
    val matchedCount: Int
        get() = confirmed.matched.size

    val skippedIndices: Set<Int>
        get() = confirmed.skipped

    /** Words read correctly after one or more wrong attempts (self-correction). */
    val retriedIndices: Set<Int>
        get() = confirmed.retried

    fun processPartial(recognizedWords: List<String>) {
        tentative = advance(confirmed, recognizedWords)
    }

    fun processFinal(recognizedWords: List<String>) {
        confirmed = advance(confirmed, recognizedWords)
        tentative = null
    }

    /** Accepts the latest partial hypothesis as final, e.g. when reading is paused or finished before a final result. */
    fun commitTentative() {
        tentative?.let { confirmed = it }
        tentative = null
    }

    fun reset() {
        confirmed = Progress()
        tentative = null
    }

    fun getWordStatus(index: Int): WordStatus {
        if (index < 0 || index >= words.size) return WordStatus.UNREAD
        return when {
            index in confirmed.matched -> WordStatus.CONFIRMED
            index in confirmed.skipped -> WordStatus.SKIPPED
            index <= tentativeIndex -> WordStatus.TENTATIVE
            else -> WordStatus.UNREAD
        }
    }

    private fun advance(from: Progress, recognizedWords: List<String>): Progress {
        val tokens = from.pending + recognizedWords.map(TextPreparation::normalize).filter { it.isNotEmpty() }
        var p = from.copy(pending = emptyList())
        var i = 0
        while (i < tokens.size) {
            val expected = p.pointer + 1
            if (expected >= normalizedWords.size) break
            val token = tokens[i]
            val target = normalizedWords[expected]

            // The whole word, or the word read by syllables
            val pieces = glue(tokens, i, target)
            if (pieces > 0) {
                when {
                    tokens.subList(i, i + pieces).joinToString("") == target -> p = p.readTo(expected)
                    i + pieces == tokens.size -> return p.copy(pending = tokens.subList(i, tokens.size))
                    else -> Unit // a partial word before the whole one — not an error
                }
                i += pieces
                continue
            }

            // Repeat of a recently read word, whole or by syllables
            val repeated = (maxOf(0, p.pointer - REPEAT_WINDOW + 1)..p.pointer).maxOfOrNull { glue(tokens, i, normalizedWords[it]) } ?: 0
            if (repeated > 0) {
                i += repeated
                continue
            }

            val jump = (expected + 1..minOf(expected + MAX_SKIP, normalizedWords.lastIndex))
                .firstOrNull { normalizedWords[it] == token }
            if (jump != null) {
                // Skipping one word is common; a longer jump must be confirmed by the next word to avoid a chance match.
                if (jump - expected > 1 && i + 1 == tokens.size) return p.copy(pending = tokens.subList(i, tokens.size))
                if (jump - expected == 1 || normalizedWords.getOrNull(jump + 1) == tokens[i + 1]) {
                    p = p.readTo(jump)
                    i++
                    continue
                }
            }

            p = p.copy(wrongAttempt = true)
            i++
        }
        return p
    }

    /** How many tokens from [start] glue together into the beginning of [word] (stops once the word is complete). */
    private fun glue(tokens: List<String>, start: Int, word: String): Int {
        var glued = ""
        var count = 0
        while (glued != word && start + count < tokens.size && word.startsWith(glued + tokens[start + count])) {
            glued += tokens[start + count]
            count++
        }
        return count
    }

    /** Reads word [index]; words between the pointer and [index] are skipped. */
    private fun Progress.readTo(index: Int) = copy(
        pointer = index,
        matched = matched + index,
        skipped = skipped + (pointer + 1 until index),
        retried = if (wrongAttempt && index == pointer + 1) retried + index else retried,
        wrongAttempt = false
    )

    private companion object {
        /** How many of the last passed words a repeat is recognised against. */
        const val REPEAT_WINDOW = 3

        /** Maximum number of words the child may skip in one go. */
        const val MAX_SKIP = 2
    }
}
