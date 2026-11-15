package com.gefolgwar.readwithkid

/**
 * Splits a shown word into syllables for reading, by rules (no dictionary).
 * The syllables joined give back the word: punctuation stays with the neighbouring syllable,
 * each part of a hyphenated word is split on its own.
 */
object Syllables {
    private const val APOSTROPHES = "'ʼ’"

    fun split(word: String, language: TextLanguage): List<String> {
        val cuts = sortedSetOf<Int>()
        letterRuns(word).forEachIndexed { i, run ->
            if (i > 0) cuts += run.first
            val nuclei = when (language) {
                TextLanguage.UK -> Ukrainian.nuclei(word, run)
                TextLanguage.DE -> German.nuclei(word, run)
            }
            nuclei.zipWithNext { a, b ->
                cuts += when (language) {
                    TextLanguage.UK -> Ukrainian.cut(word, a.last + 1, b.first)
                    TextLanguage.DE -> German.cut(word, a.last + 1, b.first)
                }
            }
        }
        return (listOf(0) + cuts + word.length).zipWithNext { from, to -> word.substring(from, to) }
    }

    /** Letters together with apostrophes between letters and combining marks such as the stress mark. */
    private fun letterRuns(word: String): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (i in word.indices) {
            val c = word[i]
            val inRun = c.isLetter() ||
                start >= 0 && Character.getType(c) == Character.NON_SPACING_MARK.toInt() ||
                start >= 0 && c in APOSTROPHES && word.getOrNull(i + 1)?.isLetter() == true
            if (inRun && start < 0) start = i
            if (!inRun && start >= 0) {
                runs += start until i
                start = -1
            }
        }
        if (start >= 0) runs += start until word.length
        return runs
    }

    /**
     * School rules: one vowel per syllable; a single consonant between vowels and a cluster of consonants
     * go to the next syllable, except that a sonorant right before a noisy consonant closes the syllable.
     * The soft sign and the apostrophe stay with the letter before them.
     */
    private object Ukrainian {
        private const val VOWELS = "аеєиіїоуюя"
        private const val SONORANTS = "йврлмн"
        private const val NOISY = "бгґджзкпстфхцчшщ"

        fun nuclei(word: String, run: IntRange) = run.filter { word[it].lowercaseChar() in VOWELS }.map { it..it }

        /** Where the consonants between the vowels at [from] - 1 and [to] are cut. */
        fun cut(word: String, from: Int, to: Int): Int {
            val consonants = (from until to).filter { word[it].lowercaseChar().let { c -> c in SONORANTS || c in NOISY } }
            if (consonants.isEmpty()) return to
            val first = word[consonants[0]].lowercaseChar()
            val next = word[consonants[0] + 1].lowercaseChar()
            // "ч" before "к" ends the syllable too: the diminutive suffix, со-неч-ко, доч-ка.
            val closes = first in SONORANTS && next in NOISY || first == 'ч' && next == 'к'
            return if (closes && consonants.size > 1) consonants[1] else consonants[0]
        }
    }

    /**
     * Spoken syllables: diphthongs and long vowels are one vowel, "sch", "ch" and "ck" one consonant.
     * Of the consonants between vowels the last goes to the next syllable,
     * or the last two when they can start a syllable (gras-grü-ne).
     */
    private object German {
        private const val VOWELS = "aeiouäöüy"
        private val DOUBLE_VOWELS = setOf("ai", "au", "äu", "ei", "eu", "ie", "aa", "ee", "oo")
        private val CONSONANT_GROUPS = listOf("sch", "ch", "ck")
        private val ONSETS = setOf("bl", "br", "dr", "fl", "fr", "gl", "gr", "kl", "kr", "pl", "pr", "tr")

        private fun lower(word: String, i: Int) = word.getOrNull(i)?.lowercaseChar()

        // "u" after "q" is part of the consonant: Qu-el-le.
        private fun isVowel(word: String, i: Int): Boolean {
            val c = lower(word, i) ?: return false
            return c in VOWELS && !(c == 'u' && lower(word, i - 1) == 'q')
        }

        fun nuclei(word: String, run: IntRange): List<IntRange> {
            val nuclei = mutableListOf<IntRange>()
            var i = run.first
            while (i <= run.last) {
                if (!isVowel(word, i)) {
                    i++
                    continue
                }
                val long = i + 1 <= run.last && isVowel(word, i + 1) && word.substring(i, i + 2).lowercase() in DOUBLE_VOWELS
                val end = if (long) i + 1 else i
                nuclei += i..end
                i = end + 1
            }
            return nuclei
        }

        fun cut(word: String, from: Int, to: Int): Int {
            // Start of each consonant (group) between the vowels
            val starts = mutableListOf<Int>()
            var i = from
            while (i < to) {
                starts += i
                val rest = word.substring(i, to).lowercase()
                i += CONSONANT_GROUPS.firstOrNull { rest.startsWith(it) }?.length ?: 1
            }
            if (starts.isEmpty()) return to
            val last = starts.last()
            val beforeLast = starts.getOrNull(starts.size - 2)
            return if (beforeLast != null && word.substring(beforeLast, to).lowercase() in ONSETS) beforeLast else last
        }
    }
}
