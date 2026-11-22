package com.gefolgwar.readwithkid

/**
 * Follows a child reading [words] aloud, sentence by sentence: [sentenceOf] gives the sentence of each word. Repeats,
 * reading by syllables, partial words and self-corrections are not errors; a skipped word is passed over and
 * remembered as skipped.
 *
 * The child reads the sentences in any order: reading goes on wherever the text is heard again, also in another
 * sentence, or in the sentence chosen with [startAt]. A sentence is read once its last word is passed, or when the
 * child leaves it for another one: its words not passed yet are then skipped. Reading a sentence again replaces how it
 * was read; [firstReadings] keeps how each sentence was read the first time.
 *
 * Words at [unknownIndices] are missing from the speech model's vocabulary and cannot be recognised: such a word
 * is passed once the next known word is read (or with the last known word) and counts neither as read nor as skipped.
 *
 * A word passed over where the model was not sure of what it heard in its place is uncertain: the model could not
 * check it, so it is neither read nor an error.
 */
class WordAligner(
    private val words: List<String>,
    unknownIndices: Set<Int> = emptySet(),
    private val sentenceOf: List<Int> = List(words.size) { 0 }
) {

    /**
     * Indices into [known], not into [words]. Changed in place, so following speech in a long text does not copy
     * everything for every word; [copy] it to keep the original.
     */
    private inner class Progress(
        /** Last word passed (read or skipped), or -1. */
        var pointer: Int = -1,
        /** The sentence being read: some of its words are passed, the rest not yet; -1 for none. */
        var active: Int = -1,
        val matched: MutableSet<Int> = mutableSetOf(),
        val skipped: MutableSet<Int> = mutableSetOf(),
        /** Words read correctly after one or more wrong attempts. */
        val retried: MutableSet<Int> = mutableSetOf(),
        /** The next expected word already got a wrong attempt. */
        var wrongAttempt: Boolean = false,
        /** Words whose latest wrong attempt the model was not sure of; passed over, they are uncertain. */
        val unsure: MutableSet<Int> = mutableSetOf(),
        /** What the model heard in place of a word: its latest wrong attempt, as the model spelled it. */
        val heard: MutableMap<Int, String> = mutableMapOf(),
        /** How each sentence read so far was read the first time. */
        val first: MutableMap<Int, SentenceReading> = mutableMapOf(),
        /** Trailing recognised words that need more speech to resolve: unfinished syllables or an unconfirmed jump. */
        var pending: List<Token> = emptyList()
    ) {
        fun copy() = Progress(
            pointer, active, matched.toMutableSet(), skipped.toMutableSet(), retried.toMutableSet(), wrongAttempt,
            unsure.toMutableSet(), heard.toMutableMap(), first.toMutableMap(), pending
        )

        /**
         * Reads word [k]. In the sentence being read, the words between the pointer and [k] are skipped; a word in
         * another sentence leaves the sentence being read and starts reading the other one, from its first word.
         */
        fun readTo(k: Int) {
            val retry = wrongAttempt && k == pointer + 1
            val sentence = sentenceOfKnown[k]
            if (sentence != active) {
                leave()
                begin(sentence)
            }
            if (retry) retried += k
            matched += k
            skipped += (pointer + 1 until k)
            unsure -= k
            heard -= k
            wrongAttempt = false
            pointer = k
            if (k == knownOf.getValue(sentence).last()) finish()
        }

        /** Leaves the sentence being read: its words not passed yet are skipped. */
        fun leave() {
            if (active < 0) return
            skipped += (pointer + 1..knownOf.getValue(active).last())
            finish()
        }

        /** Starts reading [sentence] afresh from its first word, forgetting how it was read before. */
        private fun begin(sentence: Int) {
            val inSentence = knownOf.getValue(sentence)
            matched -= inSentence
            skipped -= inSentence
            retried -= inSentence
            unsure -= inSentence
            heard -= inSentence
            active = sentence
            pointer = inSentence.first() - 1
        }

        private fun finish() {
            pointer = knownOf.getValue(active).last()
            if (active !in first) first[active] = reading(active)
            active = -1
            wrongAttempt = false
        }

        /** Whether [sentence] is read in this progress, and not being read again. */
        fun isRead(sentence: Int) = sentence in first && sentence != active

        /** How [sentence] was read last. */
        fun reading(sentence: Int): SentenceReading {
            val inSentence = knownOf.getValue(sentence)
            val uncertain = inSentence.filter { it in skipped && it in unsure }
            return SentenceReading(
                read = inSentence.filter { it in matched }.mapTo(mutableSetOf()) { known[it] },
                misread = inSentence.filter { it in skipped && it !in unsure }.associate { known[it] to heard[it].orEmpty() },
                uncertain = uncertain.mapTo(mutableSetOf()) { known[it] },
                retried = inSentence.filter { it in retried }.mapTo(mutableSetOf()) { known[it] }
            )
        }
    }

    /** A recognised word as compared with the text, as the model spelled it, and whether the model was sure of it. */
    private class Token(val text: String, val spelled: String, val sure: Boolean)

    /** Indices of the known words in [words]; only these are followed. */
    private val known = words.indices.filter { it !in unknownIndices }
    private val normalizedWords = known.map { TextPreparation.normalize(words[it]) }
    private val sentenceOfKnown = known.map { sentenceOf[it] }
    /** The known words of each sentence that has any. */
    private val knownOf: Map<Int, List<Int>> = known.indices.groupBy { sentenceOfKnown[it] }
    private var confirmed = Progress()
    private var tentative: Progress? = null

    val confirmedIndex: Int
        get() = passedTo(confirmed.pointer)

    val tentativeIndex: Int
        get() = tentative?.let { passedTo(it.pointer) } ?: -1

    /** The word the child is expected to read next, also by a partial result; [words] size after the last word. */
    val nextIndex: Int
        get() {
            val k = (tentative ?: confirmed).pointer + 1
            if (k > known.lastIndex) return words.size
            val next = passedTo(k - 1) + 1
            // Words the model cannot recognise that end the sentence before belong to that one
            return if (sentenceOf[next] == sentenceOfKnown[k]) next else sentenceOf.indexOf(sentenceOfKnown[k])
        }

    // Words recognised in the latest reading of each sentence; words passed over by the look-ahead are not counted.
    val matchedCount: Int
        get() = confirmed.matched.size

    /** Words misread or skipped in the latest reading of each sentence; without uncertain words. */
    val skippedIndices: Set<Int>
        get() = (confirmed.skipped - confirmed.unsure).mapTo(mutableSetOf()) { known[it] }

    /** Words passed over where the model was not sure what it heard in their place: neither read nor an error. */
    val uncertainIndices: Set<Int>
        get() = confirmed.skipped.intersect(confirmed.unsure).mapTo(mutableSetOf()) { known[it] }

    /** Words read correctly after one or more wrong attempts (self-correction). */
    val retriedIndices: Set<Int>
        get() = confirmed.retried.mapTo(mutableSetOf()) { known[it] }

    /** How each sentence read so far was read the first time, by sentence. */
    val firstReadings: Map<Int, SentenceReading>
        get() = confirmed.first

    /**
     * The first sentence still to read: not read yet, or read last with too many errors; -1 when every sentence that
     * can be recognised is read well.
     */
    val sentenceToRead: Int
        get() = knownOf.keys.sorted().firstOrNull { sentenceStatus(it) != SentenceStatus.READ } ?: -1

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

    /**
     * The child chose [sentence] to read next: the latest partial hypothesis is accepted, the sentence being read is
     * left (its words not passed yet are skipped) and reading is expected from the first word of [sentence].
     */
    fun startAt(sentence: Int) {
        val first = knownOf[sentence]?.first() ?: return
        commitTentative()
        confirmed.apply {
            leave()
            pointer = first - 1
            wrongAttempt = false
            pending = emptyList()
        }
    }

    /** Reading stops: the latest partial hypothesis is accepted and the sentence being read is left. */
    fun stop() {
        commitTentative()
        confirmed.leave()
    }

    fun reset() {
        confirmed = Progress()
        tentative = null
    }

    /** Whether [sentence] is read, and how well, as last read; a sentence being read again is not read yet. */
    fun sentenceStatus(sentence: Int): SentenceStatus = when {
        !confirmed.isRead(sentence) -> SentenceStatus.UNREAD
        confirmed.reading(sentence).tooManyErrors -> SentenceStatus.TOO_MANY_ERRORS
        else -> SentenceStatus.READ
    }

    /** What the model heard in place of a misread word of a read sentence; null for any other word or when nothing was heard. */
    fun heard(index: Int): String? {
        val k = known.binarySearch(index)
        if (k < 0 || !confirmed.isRead(sentenceOfKnown[k]) || k !in confirmed.skipped || k in confirmed.unsure) return null
        return confirmed.heard[k]?.takeIf { it.isNotEmpty() }
    }

    /**
     * In a read sentence, how each word was read. In the sentence being read only the words read so far are marked,
     * so a misread or skipped word looks unread and the child reads on.
     */
    fun getWordStatus(index: Int): WordStatus {
        if (index < 0 || index >= words.size) return WordStatus.UNREAD
        val k = known.binarySearch(index) // negative for an unknown word
        if (confirmed.isRead(sentenceOf[index])) return when {
            k < 0 || k in confirmed.unsure && k in confirmed.skipped -> WordStatus.UNCHECKED
            k in confirmed.matched -> WordStatus.READ
            else -> WordStatus.MISREAD
        }
        return when {
            confirmed.passes(index, k) -> WordStatus.CONFIRMED
            tentative?.passes(index, k) == true -> WordStatus.TENTATIVE
            else -> WordStatus.UNREAD
        }
    }

    /** Whether word [index] ([k] among the known words) is read in the sentence being read; an unknown word once passed. */
    private fun Progress.passes(index: Int, k: Int): Boolean {
        if (active != sentenceOf[index] || index > passedTo(pointer)) return false
        return k < 0 || k in matched
    }

    private fun advance(from: Progress, recognized: List<Token>): Progress {
        val heard = from.pending + recognized
        val tokens = heard.map { it.text }
        val p = from.copy().apply { pending = emptyList() }
        // First token of the current attempt at the expected word, to show all of it
        var attempt = 0
        var i = 0
        while (i < tokens.size) {
            val expected = p.pointer + 1
            val token = tokens[i]
            val target = normalizedWords.getOrNull(expected)

            // The whole word, or the word read by syllables
            val pieces = if (target == null) 0 else glue(tokens, i, target)
            if (pieces > 0) {
                when {
                    tokens.subList(i, i + pieces).joinToString("") == target -> {
                        p.readTo(expected)
                        attempt = i + pieces
                    }
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
                attempt = i
                continue
            }

            // A skipped word, words the model misheard although they were read, or the child reading on elsewhere:
            // reading goes on where the text is heard again. Skipping one word is common; a longer jump must be
            // confirmed by the words heard after it, to avoid a chance match. A jump that more speech may still
            // confirm waits for it.
            var mayConfirm = false
            val jump = jumpTargets(p, expected).firstOrNull { j ->
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
                attempt = i
                continue
            }
            if (mayConfirm) return p.apply { pending = heard.subList(i, tokens.size) }

            // Right after an unknown word the model says something else in its place: not a wrong attempt.
            // Only the latest attempt decides whether the model was sure the word was misread.
            if (target != null && !followsUnknown(expected)) {
                p.wrongAttempt = true
                p.heard[expected] = heard.subList(attempt, i + 1).map { it.spelled }.filter { it.isNotEmpty() }.joinToString(" ")
                if (heard[i].sure) p.unsure -= expected else p.unsure += expected
            }
            i++
        }
        return p
    }

    /**
     * Where reading may go on after the expected word: any word ahead, nearest first, then any word behind in another
     * sentence, nearest first. Earlier words of the sentence being read are repeats or wrong attempts, not a new start.
     */
    private fun jumpTargets(p: Progress, expected: Int): Sequence<Int> =
        (expected + 1..normalizedWords.lastIndex).asSequence() +
            (minOf(expected, normalizedWords.size) - 1 downTo 0).asSequence().filter { sentenceOfKnown[it] != p.active }

    /**
     * The recognised words as compared with the text, sure unless their index is in [unsure]: speech the model could
     * not take for a word of its grammar matches no word, and words without letters or digits are left out.
     */
    private fun tokens(recognizedWords: List<String>, unsure: Set<Int> = emptySet()) =
        recognizedWords.mapIndexedNotNull { i, word ->
            val text = if (word == OUTSIDE_GRAMMAR) word else TextPreparation.normalize(word)
            if (text.isEmpty()) null else Token(text, if (word == OUTSIDE_GRAMMAR) "" else word, i !in unsure)
        }

    /** How many words heard after a word [distance] words from the expected one confirm a jump to it. */
    private fun confirmationsFor(distance: Int) = when (distance) {
        1 -> 0
        in 2..MAX_SKIP -> 1
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

        /** What the model says, with a grammar, for speech that is none of its words: a wrong attempt. */
        const val OUTSIDE_GRAMMAR = "[unk]"
    }
}

/** How the words of a sentence were read, by their index in the text; words the model cannot recognise are in none. */
data class SentenceReading(
    val read: Set<Int>,
    /** Misread or skipped words, with what the model heard in their place ("" when nothing). */
    val misread: Map<Int, String>,
    /** Words the model was not sure of: neither read nor errors. */
    val uncertain: Set<Int>,
    /** Words read correctly after one or more wrong attempts; they are read. */
    val retried: Set<Int>
) {
    /** More than half of the words checked were misread or skipped. */
    val tooManyErrors: Boolean
        get() = misread.size > read.size
}
