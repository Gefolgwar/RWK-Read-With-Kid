package com.gefolgwar.readwithkid

/**
 * Follows a child reading [words] aloud. Repeats, reading by syllables, partial words and
 * self-corrections are not errors; a skipped word is passed over and remembered as skipped.
 *
 * Words at [unknownIndices] are missing from the speech model's vocabulary and cannot be recognised: such a word
 * is passed once the next known word is read (or with the last known word) and counts neither as read nor as skipped.
 *
 * A word passed over where the model was not sure of what it heard in its place is uncertain: the model could not
 * check it, so it is neither read nor an error. After the text the child reads skipped and uncertain words again,
 * see [review].
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
        /** Words whose latest wrong attempt the model was not sure of; passed over, they are uncertain. */
        val unsure: MutableSet<Int> = mutableSetOf(),
        /** Skipped words the child read correctly after the text; they stay errors of the reading. */
        val corrected: MutableSet<Int> = mutableSetOf(),
        /** Trailing recognised words that need more speech to resolve: unfinished syllables or an unconfirmed jump. */
        var pending: List<Token> = emptyList()
    ) {
        fun copy() = Progress(
            pointer, matched.toMutableSet(), skipped.toMutableSet(), retried.toMutableSet(), wrongAttempt,
            unsure.toMutableSet(), corrected.toMutableSet(), pending
        )

        /** Reads word [index]; words between the pointer and [index] are skipped. */
        fun readTo(index: Int) {
            if (wrongAttempt && index == pointer + 1) retried += index
            matched += index
            skipped += (pointer + 1 until index)
            unsure -= index
            wrongAttempt = false
            pointer = index
        }
    }

    /** A recognised word as compared with the text, and whether the model was sure of it. */
    private class Token(val text: String, val sure: Boolean)

    /** Indices of the known words in [words]; only these are followed. */
    private val known = words.indices.filter { it !in unknownIndices }
    private val normalizedWords = known.map { TextPreparation.normalize(words[it]) }
    private var confirmed = Progress()
    private var tentative: Progress? = null
    /** The word being read again after the text, and its first syllables heard so far. */
    private var reviewed = -1
    private var reviewHeard: List<String> = emptyList()

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

    /** Words misread or skipped and not corrected after the text; without uncertain words. */
    val skippedIndices: Set<Int>
        get() = (confirmed.skipped - confirmed.unsure - confirmed.corrected).mapTo(mutableSetOf()) { known[it] }

    /** Words passed over where the model was not sure what it heard in their place: neither read nor an error. */
    val uncertainIndices: Set<Int>
        get() = confirmed.skipped.intersect(confirmed.unsure).mapTo(mutableSetOf()) { known[it] }

    /** Misread or skipped words the child read correctly after the text; they still count as errors of the reading. */
    val correctedIndices: Set<Int>
        get() = confirmed.corrected.mapTo(mutableSetOf()) { known[it] }

    /** Words to read again after the text: misread, skipped and uncertain ones, in text order. */
    val reviewIndices: List<Int>
        get() = confirmed.skipped.sorted().map { known[it] }

    /** Words read correctly after one or more wrong attempts (self-correction). */
    val retriedIndices: Set<Int>
        get() = confirmed.retried.mapTo(mutableSetOf()) { known[it] }

    fun processPartial(recognizedWords: List<String>) {
        tentative = advance(confirmed, tokens(recognizedWords))
    }

    /** [unsure]: indices into [recognizedWords] of the words the model was not sure of. */
    fun processFinal(recognizedWords: List<String>, unsure: Set<Int> = emptySet()) {
        confirmed = advance(confirmed, tokens(recognizedWords, unsure))
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
        reviewed = -1
        reviewHeard = emptyList()
    }

    /**
     * The child reads word [index], one of [reviewIndices], again after the text. [ReviewOutcome.READ] once
     * [recognizedWords] hold the word, whole or by syllables: an uncertain word then counts as read, a misread or
     * skipped word as corrected. A final result holding only the word's first syllables waits for the rest of it, as
     * a partial result does ([ReviewOutcome.LISTENING]); any other final result is [ReviewOutcome.WRONG].
     */
    fun review(index: Int, recognizedWords: List<String>, final: Boolean): ReviewOutcome {
        val k = known.binarySearch(index)
        if (k !in confirmed.skipped || k in confirmed.corrected) return ReviewOutcome.WRONG
        if (k != reviewed) {
            reviewed = k
            reviewHeard = emptyList()
        }
        val tokens = reviewHeard + tokens(recognizedWords).map { it.text }
        val target = normalizedWords[k]
        val read = tokens.indices.any { i ->
            val pieces = glue(tokens, i, target)
            pieces > 0 && tokens.subList(i, i + pieces).joinToString("") == target
        }
        if (read) {
            reviewHeard = emptyList()
            confirmed.apply {
                if (k in unsure) {
                    matched += k
                    skipped -= k
                    unsure -= k
                } else corrected += k
            }
            return ReviewOutcome.READ
        }
        if (!final) return ReviewOutcome.LISTENING
        // Read by syllables with a pause: the rest of the word is still to come
        if (tokens.isNotEmpty() && glue(tokens, 0, target) == tokens.size) {
            reviewHeard = tokens
            return ReviewOutcome.LISTENING
        }
        reviewHeard = emptyList()
        return ReviewOutcome.WRONG
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

    private fun advance(from: Progress, recognized: List<Token>): Progress {
        val heard = from.pending + recognized
        val tokens = heard.map { it.text }
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
                    tokens.subList(i, i + pieces).joinToString("") == target -> p.readTo(expected)
                    i + pieces == tokens.size -> return p.apply { pending = heard.subList(i, tokens.size) }
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
                p.readTo(jump)
                i++
                continue
            }
            if (mayConfirm) return p.apply { pending = heard.subList(i, tokens.size) }

            // Right after an unknown word the model says something else in its place: not a wrong attempt.
            // Only the latest attempt decides whether the model was sure the word was misread.
            if (!followsUnknown(expected)) {
                p.wrongAttempt = true
                if (heard[i].sure) p.unsure -= expected else p.unsure += expected
            }
            i++
        }
        return p
    }

    /**
     * The recognised words as compared with the text, sure unless their index is in [unsure]: speech the model could
     * not take for a word of its grammar matches no word, and words without letters or digits are left out.
     */
    private fun tokens(recognizedWords: List<String>, unsure: Set<Int> = emptySet()) =
        recognizedWords.mapIndexedNotNull { i, word ->
            val text = if (word == OUTSIDE_GRAMMAR) word else TextPreparation.normalize(word)
            if (text.isEmpty()) null else Token(text, i !in unsure)
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

        /** What the model says, with a grammar, for speech that is none of its words: a wrong attempt. */
        const val OUTSIDE_GRAMMAR = "[unk]"
    }
}

/** How reading a word again after the text went, see [WordAligner.review]. */
enum class ReviewOutcome {
    /** The word was read correctly. */
    READ,
    /** Nothing decided yet: more speech is awaited. */
    LISTENING,
    /** The word was misread again. */
    WRONG
}
