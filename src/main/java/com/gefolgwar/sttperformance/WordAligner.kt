package com.gefolgwar.sttperformance

class WordAligner(private val words: List<String>) {

    var confirmedIndex: Int = -1
        private set

    var tentativeIndex: Int = -1
        private set

    private val normalizedWords = words.map { normalize(it) }

    fun processPartial(recognizedWords: List<String>) {
        tentativeIndex = matchWords(recognizedWords, confirmedIndex)
    }

    fun processFinal(recognizedWords: List<String>) {
        confirmedIndex = matchWords(recognizedWords, confirmedIndex)
        tentativeIndex = -1
    }

    fun reset() {
        confirmedIndex = -1
        tentativeIndex = -1
    }

    fun getWordStatus(index: Int): WordStatus {
        if (index < 0 || index >= words.size) return WordStatus.UNREAD
        return when {
            index <= confirmedIndex -> WordStatus.CONFIRMED
            index <= tentativeIndex -> WordStatus.TENTATIVE
            else -> WordStatus.UNREAD
        }
    }

    private fun matchWords(recognizedWords: List<String>, startFrom: Int): Int {
        if (recognizedWords.isEmpty()) return startFrom
        var pointer = startFrom
        for (recognized in recognizedWords) {
            val norm = normalize(recognized)
            var matched = false
            for (ahead in 1..3) {
                val candidate = pointer + ahead
                if (candidate >= normalizedWords.size) break
                if (normalizedWords[candidate] == norm) {
                    pointer = candidate
                    matched = true
                    break
                }
            }
            if (!matched && pointer == startFrom && recognizedWords.size == 1) {
                // single unrecognized word — don't advance
            }
        }
        return pointer
    }

    companion object {
        private val PUNCTUATION = Regex("[,.\\''“”\"!?\\—:;()«»]+")

        fun normalize(word: String): String {
            return word.lowercase().replace(PUNCTUATION, "")
        }
    }
}
