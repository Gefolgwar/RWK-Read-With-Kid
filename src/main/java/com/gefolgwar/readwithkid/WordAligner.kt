package com.gefolgwar.readwithkid

/**
 * Follows a child reading [words] aloud. Repeats, reading by syllables, partial words and
 * self-corrections are not errors; a skipped word is passed over and remembered as skipped.
 *
 * Words at [unknownIndices] are missing from the speech model's vocabulary and cannot be recognised: such a word
 * is passed once the next known word is read (or with the last known word) and counts neither as read nor as skipped.
 */
class WordAligner(private val words: List<String>, unknownIndices: Set<Int> = emptySet()) {

    /** Indices into [known], not into [words]. */
    private data class Progress(
        /** Last word passed (read or skipped), or -1. */
        val pointer: Int = -1,
        val matched: Set<Int> = emptySet(),
        val skipped: Set<Int> = emptySet(),
        /** Words read correctly after a wrong attempt. */
        val retried: Set<Int> = emptySet(),
        /** The next expected word already got a wrong attempt. */
        val wrongAttempt: Boolean = false,
        /** What the model heard in place of a word, as it spelled it: the word itself once read, else the wrong attempts. */
        val heard: Map<Int, String> = emptyMap(),
        /** Trailing recognised words, as heard, that need more speech to resolve: unfinished syllables or an unconfirmed jump. */
        val pending: List<String> = emptyList()
    )

    /** Indices of the known words in [words]; only these are followed. */
    private val known = words.indices.filter { it !in unknownIndices }
    private val normalizedWords = known.map { TextPreparation.normalize(words[it]) }
    private var confirmed = Progress()
    private var tentative: Progress? = null

    val confirmedIndex: Int
        get() = passedTo(confirmed.pointer)

    val tentativeIndex: Int
        get() = tentative?.let { passedTo(it.pointer) } ?: -1

    /** Words that can be recognised: the words that count. */
    val knownCount: Int
        get() = known.size

    // Words actually recognised; words passed over by the look-ahead are not counted.
    val matchedCount: Int
        get() = confirmed.matched.size

    val skippedIndices: Set<Int>
        get() = confirmed.skipped.mapTo(mutableSetOf()) { known[it] }

    /** Words read correctly after one or more wrong attempts (self-correction). */
    val retriedIndices: Set<Int>
        get() = confirmed.retried.mapTo(mutableSetOf()) { known[it] }

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

    /**
     * What the model heard in place of a read or skipped word, as it spelled it; "" when nothing was heard
     * in place of a skipped word. Null for a word not passed yet and for a word the model cannot recognise.
     */
    fun heard(index: Int): String? {
        val k = known.binarySearch(index)
        if (k !in confirmed.matched && k !in confirmed.skipped) return null
        return confirmed.heard[k].orEmpty()
    }

    /**
     * The child reads skipped word [index] again. True once [recognizedWords] hold the word, whole or by syllables:
     * it then counts as read after a wrong attempt. Otherwise a [final] result is what was heard in its place.
     */
    fun retry(index: Int, recognizedWords: List<String>, final: Boolean): Boolean {
        val k = known.binarySearch(index)
        if (k !in confirmed.skipped) return false
        val heardTokens = recognizedWords.filter { TextPreparation.normalize(it).isNotEmpty() }
        val tokens = heardTokens.map(TextPreparation::normalize)
        val target = normalizedWords[k]
        for (i in tokens.indices) {
            val pieces = glue(tokens, i, target)
            if (pieces > 0 && tokens.subList(i, i + pieces).joinToString("") == target) {
                confirmed = confirmed.copy(
                    matched = confirmed.matched + k,
                    skipped = confirmed.skipped - k,
                    retried = confirmed.retried + k,
                    heard = confirmed.heard + (k to heardTokens.subList(i, i + pieces).joinToString(""))
                )
                return true
            }
        }
        if (final) confirmed = confirmed.copy(heard = confirmed.heard + (k to heardTokens.joinToString(" ")))
        return false
    }

    fun getWordStatus(index: Int): WordStatus {
        if (index < 0 || index >= words.size) return WordStatus.UNREAD
        val k = known.binarySearch(index) // negative for an unknown word
        return when {
            k in confirmed.matched || k < 0 && index <= confirmedIndex -> WordStatus.CONFIRMED
            k in confirmed.skipped -> WordStatus.SKIPPED
            index <= tentativeIndex -> WordStatus.TENTATIVE
            else -> WordStatus.UNREAD
        }
    }

    private fun advance(from: Progress, recognizedWords: List<String>): Progress {
        // As the model spelled them, to show what was heard
        val heardTokens = from.pending + recognizedWords.filter { TextPreparation.normalize(it).isNotEmpty() }
        val tokens = heardTokens.map(TextPreparation::normalize)
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
                    tokens.subList(i, i + pieces).joinToString("") == target ->
                        p = p.readTo(expected, heardTokens.subList(i, i + pieces).joinToString(""))
                    i + pieces == tokens.size -> return p.copy(pending = heardTokens.subList(i, tokens.size))
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
                if (jump - expected > 1 && i + 1 == tokens.size) return p.copy(pending = heardTokens.subList(i, tokens.size))
                if (jump - expected == 1 || normalizedWords.getOrNull(jump + 1) == tokens[i + 1]) {
                    p = p.readTo(jump, heardTokens[i])
                    i++
                    continue
                }
            }

            // Right after an unknown word the model says something else in its place: not a wrong attempt.
            if (!followsUnknown(expected)) {
                val attempts = listOfNotNull(p.heard[expected], heardTokens[i]).joinToString(" ")
                p = p.copy(wrongAttempt = true, heard = p.heard + (expected to attempts))
            }
            i++
        }
        return p
    }

    /** Index in [words] of the last word passed when known word [k] is the last passed; unknown words at the end go with the last known one. */
    private fun passedTo(k: Int) = when (k) {
        -1 -> -1
        known.lastIndex -> words.lastIndex
        else -> known[k]
    }

    /** Whether unknown words stand right before known word [k]. */
    private fun followsUnknown(k: Int) = known[k] - (known.getOrNull(k - 1) ?: -1) > 1

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

    /** Reads word [index], heard as [heardAs]; words between the pointer and [index] are skipped. */
    private fun Progress.readTo(index: Int, heardAs: String) = copy(
        pointer = index,
        matched = matched + index,
        skipped = skipped + (pointer + 1 until index),
        retried = if (wrongAttempt && index == pointer + 1) retried + index else retried,
        wrongAttempt = false,
        heard = heard + (index to heardAs)
    )

    private companion object {
        /** How many of the last passed words a repeat is recognised against. */
        const val REPEAT_WINDOW = 3

        /** Maximum number of words the child may skip in one go. */
        const val MAX_SKIP = 2
    }
}
