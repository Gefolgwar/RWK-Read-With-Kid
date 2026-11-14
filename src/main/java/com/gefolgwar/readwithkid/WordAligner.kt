package com.gefolgwar.readwithkid

class WordAligner(private val words: List<String>) {

    var confirmedIndex: Int = -1
        private set

    var tentativeIndex: Int = -1
        private set

    private val normalizedWords = words.map { TextPreparation.normalize(it) }

    // Words actually recognised; words passed over by the look-ahead are not counted.
    private val matchedIndices = mutableSetOf<Int>()
    private var tentativeMatches: List<Int> = emptyList()

    val matchedCount: Int
        get() = matchedIndices.size

    fun processPartial(recognizedWords: List<String>) {
        val (pointer, matches) = matchWords(recognizedWords, confirmedIndex)
        tentativeIndex = pointer
        tentativeMatches = matches
    }

    fun processFinal(recognizedWords: List<String>) {
        val (pointer, matches) = matchWords(recognizedWords, confirmedIndex)
        confirmedIndex = pointer
        matchedIndices += matches
        tentativeIndex = -1
        tentativeMatches = emptyList()
    }

    /** Accepts the latest partial hypothesis as final, e.g. when reading is paused or finished before a final result. */
    fun commitTentative() {
        if (tentativeIndex > confirmedIndex) {
            confirmedIndex = tentativeIndex
            matchedIndices += tentativeMatches
        }
        tentativeIndex = -1
        tentativeMatches = emptyList()
    }

    fun reset() {
        confirmedIndex = -1
        tentativeIndex = -1
        matchedIndices.clear()
        tentativeMatches = emptyList()
    }

    fun getWordStatus(index: Int): WordStatus {
        if (index < 0 || index >= words.size) return WordStatus.UNREAD
        return when {
            index <= confirmedIndex -> WordStatus.CONFIRMED
            index <= tentativeIndex -> WordStatus.TENTATIVE
            else -> WordStatus.UNREAD
        }
    }

    private fun matchWords(recognizedWords: List<String>, startFrom: Int): Pair<Int, List<Int>> {
        if (recognizedWords.isEmpty()) return startFrom to emptyList()
        var pointer = startFrom
        val matches = mutableListOf<Int>()
        for (recognized in recognizedWords) {
            val norm = TextPreparation.normalize(recognized)
            var matched = false
            for (ahead in 1..3) {
                val candidate = pointer + ahead
                if (candidate >= normalizedWords.size) break
                if (normalizedWords[candidate] == norm) {
                    pointer = candidate
                    matches += candidate
                    matched = true
                    break
                }
            }
            if (!matched && pointer == startFrom && recognizedWords.size == 1) {
                // single unrecognized word — don't advance
            }
        }
        return pointer to matches
    }
}
