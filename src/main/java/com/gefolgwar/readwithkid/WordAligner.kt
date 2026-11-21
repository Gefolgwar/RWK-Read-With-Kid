package com.gefolgwar.readwithkid

/**
 * Follows a child reading [words] aloud. Repeats, reading by syllables, partial words and
 * self-corrections are not errors; a skipped word is passed over and remembered as skipped.
 *
 * Words at [unknownIndices] are missing from the speech model's vocabulary and cannot be recognised: such a word
 * is passed once the next known word is read (or with the last known word) and counts neither as read nor as skipped.
 */
class WordAligner(private val words: List<String>, unknownIndices: Set<Int> = emptySet()) {

    /**
     * Indices into [known], not into [words]. Changed in place, so following speech in a long text does not copy
     * everything for every word; [copy] it to keep the original.
     */
    private class Progress(
        /** Last word passed (read or skipped), or -1. */
        var pointer: Int = -1,
        val matched: MutableSet<Int> = mutableSetOf(),
        val skipped: MutableSet<Int> = mutableSetOf(),
        /** Words read correctly after a wrong attempt. */
        val retried: MutableSet<Int> = mutableSetOf(),
        /** The next expected word already got a wrong attempt. */
        var wrongAttempt: Boolean = false,
        /** What the model heard in place of a word, as it spelled it: the word itself once read, else the latest wrong attempt. */
        val heard: MutableMap<Int, String> = mutableMapOf(),
        /** Trailing recognised words, as heard, that need more speech to resolve: unfinished syllables or an unconfirmed jump. */
        var pending: List<String> = emptyList()
    ) {
        fun copy() = Progress(
            pointer, matched.toMutableSet(), skipped.toMutableSet(), retried.toMutableSet(), wrongAttempt, heard.toMutableMap(), pending
        )

        /** Reads word [index], heard as [heardAs]; words between the pointer and [index] are skipped. */
        fun readTo(index: Int, heardAs: String) {
            if (wrongAttempt && index == pointer + 1) retried += index
            matched += index
            skipped += (pointer + 1 until index)
            wrongAttempt = false
            heard[index] = heardAs
            pointer = index
        }
    }

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
     * What the model heard in place of a read or skipped word, or the latest wrong attempt at the next word, as it
     * spelled it; "" when nothing was heard. Null for a word further ahead and for a word the model cannot recognise.
     */
    fun heard(index: Int): String? {
        val k = known.binarySearch(index)
        if (k < 0) return null
        if (k in confirmed.matched || k in confirmed.skipped) return confirmed.heard[k].orEmpty()
        val latest = tentative ?: confirmed
        return if (k == latest.pointer + 1) latest.heard[k].orEmpty() else null
    }

    /**
     * What the model heard in place of a misread word, to show over it while reading: the wrong attempts at the next
     * word as soon as they are heard (also in a partial result), or what was heard in place of a word passed over.
     * Null for a word read correctly (also after a self-correction), skipped in silence, or not reached yet.
     */
    fun misheard(index: Int): String? {
        val k = known.binarySearch(index)
        if (k < 0) return null
        if (k in confirmed.skipped) return confirmed.heard[k]?.takeIf { it.isNotEmpty() }
        val latest = tentative ?: confirmed
        return latest.heard[k].takeIf { latest.wrongAttempt && k == latest.pointer + 1 }
    }

    /**
     * The child reads skipped word [index], or the next word, again. True once [recognizedWords] hold the word, whole
     * or by syllables: a skipped word then counts as read after a wrong attempt, the next word is read.
     * Otherwise a [final] result is what was heard in its place, and the next word stays the next one.
     */
    fun retry(index: Int, recognizedWords: List<String>, final: Boolean): Boolean {
        val k = known.binarySearch(index)
        val next = k >= 0 && k == confirmed.pointer + 1
        if (k !in confirmed.skipped && !next) return false
        val heardTokens = recognizedWords.filter { TextPreparation.normalize(it).isNotEmpty() }
        val tokens = heardTokens.map(TextPreparation::normalize)
        val target = normalizedWords[k]
        for (i in tokens.indices) {
            val pieces = glue(tokens, i, target)
            if (pieces > 0 && tokens.subList(i, i + pieces).joinToString("") == target) {
                val heardAs = heardTokens.subList(i, i + pieces).joinToString("")
                if (next) confirmed.readTo(k, heardAs) else confirmed.apply {
                    matched += k
                    skipped -= k
                    retried += k
                    heard[k] = heardAs
                }
                return true
            }
        }
        if (final) confirmed.apply {
            heard[k] = heardTokens.joinToString(" ")
            wrongAttempt = wrongAttempt || next
        }
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
        val p = from.copy().apply { pending = emptyList() }
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
                        p.readTo(expected, heardTokens.subList(i, i + pieces).joinToString(""))
                    i + pieces == tokens.size -> return p.apply { pending = heardTokens.subList(i, tokens.size) }
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

            // A skipped word, or words the model misheard although they were read: reading goes on where the text is
            // heard again. Skipping one word is common; a longer jump must be confirmed by the words heard after it,
            // to avoid a chance match. A jump that more speech may still confirm waits for it.
            var mayConfirm = false
            val jump = (expected + 1..minOf(expected + MAX_JUMP, normalizedWords.lastIndex)).firstOrNull { j ->
                if (normalizedWords[j] != token) return@firstOrNull false
                val needed = confirmationsFor(j - expected)
                val heardAfter = tokens.size - i - 1
                val agrees = (1..minOf(needed, heardAfter)).all { normalizedWords.getOrNull(j + it) == tokens[i + it] }
                if (agrees && heardAfter < needed) mayConfirm = true
                agrees && heardAfter >= needed
            }
            if (jump != null) {
                p.readTo(jump, heardTokens[i])
                i++
                continue
            }
            if (mayConfirm) return p.apply { pending = heardTokens.subList(i, tokens.size) }

            // Right after an unknown word the model says something else in its place: not a wrong attempt.
            // Only the latest attempt is kept: further attempts at the word are not added to the wrong one.
            if (!followsUnknown(expected)) {
                p.wrongAttempt = true
                p.heard[expected] = heardTokens[i]
            }
            i++
        }
        return p
    }

    /** How many words heard after a word [distance] words ahead of the expected one confirm a jump to it. */
    private fun confirmationsFor(distance: Int) = when {
        distance == 1 -> 0
        distance <= MAX_SKIP -> 1
        else -> 2
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

    private companion object {
        /** How many of the last passed words a repeat is recognised against. */
        const val REPEAT_WINDOW = 3

        /** Maximum number of words the child may skip with only the next word to confirm it. */
        const val MAX_SKIP = 2

        /**
         * How far ahead reading is looked for again after words the model misheard or the child skipped, e.g. the
         * rest of a line; such a jump is confirmed by the next two words.
         */
        const val MAX_JUMP = 10
    }
}
