package com.gefolgwar.readwithkid

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WordAlignerTest {

    private val words = listOf("Україна", "є", "суверенна", "і", "незалежна", "демократична", "соціальна", "правова", "держава")
    private lateinit var aligner: WordAligner

    @Before
    fun setup() {
        aligner = WordAligner(words)
    }

    @Test
    fun `initial state has all words UNREAD`() {
        for (i in words.indices) {
            assertEquals(WordStatus.UNREAD, aligner.getWordStatus(i))
        }
        assertEquals(-1, aligner.confirmedIndex)
        assertEquals(-1, aligner.tentativeIndex)
    }

    @Test
    fun `processPartial advances tentativeIndex`() {
        aligner.processPartial(listOf("україна"))
        assertEquals(0, aligner.tentativeIndex)
        assertEquals(-1, aligner.confirmedIndex)
        assertEquals(WordStatus.TENTATIVE, aligner.getWordStatus(0))
    }

    @Test
    fun `processFinal advances confirmedIndex`() {
        aligner.processFinal(listOf("україна", "є"))
        assertEquals(1, aligner.confirmedIndex)
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(0))
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(1))
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(2))
    }

    @Test
    fun `case insensitive matching`() {
        aligner.processFinal(listOf("україна", "Є"))
        assertEquals(1, aligner.confirmedIndex)
    }

    @Test
    fun `punctuation stripping`() {
        val alignerWithPunct = WordAligner(listOf("держава.", "Мова,", "є"))
        alignerWithPunct.processFinal(listOf("держава", "мова", "є"))
        assertEquals(2, alignerWithPunct.confirmedIndex)
    }

    @Test
    fun `look-ahead skips misrecognized word`() {
        aligner.processFinal(listOf("україна", "GARBAGE", "суверенна"))
        // "україна" matches index 0, "GARBAGE" doesn't match "є" but look-ahead finds "суверенна" at index 2
        assertEquals(2, aligner.confirmedIndex)
    }

    @Test
    fun `look-ahead limited to 3 positions`() {
        // words: "Україна"(0) "є"(1) "суверенна"(2) "і"(3) "незалежна"(4)
        // Try to match "незалежна" from start — it's at index 4, beyond 3-position look-ahead
        aligner.processFinal(listOf("незалежна"))
        assertEquals(-1, aligner.confirmedIndex)
    }

    @Test
    fun `partial resets tentativeIndex on new partial`() {
        aligner.processPartial(listOf("україна", "є"))
        assertEquals(1, aligner.tentativeIndex)

        aligner.processPartial(listOf("україна"))
        assertEquals(0, aligner.tentativeIndex)
    }

    @Test
    fun `final after partial resets tentativeIndex`() {
        aligner.processPartial(listOf("україна", "є", "суверенна"))
        assertEquals(2, aligner.tentativeIndex)

        aligner.processFinal(listOf("україна", "є"))
        assertEquals(1, aligner.confirmedIndex)
        assertEquals(-1, aligner.tentativeIndex)
    }

    @Test
    fun `sequential finals advance confirmed progressively`() {
        aligner.processFinal(listOf("україна"))
        assertEquals(0, aligner.confirmedIndex)

        aligner.processFinal(listOf("є", "суверенна"))
        assertEquals(2, aligner.confirmedIndex)
    }

    @Test
    fun `mixed partial and final sequence`() {
        aligner.processPartial(listOf("україна"))
        assertEquals(WordStatus.TENTATIVE, aligner.getWordStatus(0))

        aligner.processFinal(listOf("україна", "є"))
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(0))
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(1))

        aligner.processPartial(listOf("суверенна", "і"))
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(0))
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(1))
        assertEquals(WordStatus.TENTATIVE, aligner.getWordStatus(2))
        assertEquals(WordStatus.TENTATIVE, aligner.getWordStatus(3))
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(4))
    }

    @Test
    fun `empty input does not advance`() {
        aligner.processPartial(emptyList())
        assertEquals(-1, aligner.tentativeIndex)

        aligner.processFinal(emptyList())
        assertEquals(-1, aligner.confirmedIndex)
    }

    @Test
    fun `reset clears both pointers`() {
        aligner.processFinal(listOf("україна", "є"))
        aligner.processPartial(listOf("суверенна"))
        aligner.reset()
        assertEquals(-1, aligner.confirmedIndex)
        assertEquals(-1, aligner.tentativeIndex)
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(0))
    }

    @Test
    fun `getWordStatus out of bounds returns UNREAD`() {
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(-1))
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(100))
    }

    @Test
    fun `ukrainian apostrophe handled in normalization`() {
        val alignerUkr = WordAligner(listOf("східнослов'янської", "групи"))
        alignerUkr.processFinal(listOf("східнослов'янської", "групи"))
        assertEquals(1, alignerUkr.confirmedIndex)
    }

    @Test
    fun `different apostrophe forms in text and speech match`() {
        val alignerUkr = WordAligner(listOf("сімʼя", "м’яч"))
        alignerUkr.processFinal(listOf("сім'я", "м'яч"))
        assertEquals(1, alignerUkr.confirmedIndex)
    }

    @Test
    fun `matchedCount excludes words skipped by look-ahead`() {
        aligner.processFinal(listOf("україна", "суверенна"))
        assertEquals(2, aligner.confirmedIndex)
        assertEquals(2, aligner.matchedCount)
    }

    @Test
    fun `commitTentative accepts the latest partial`() {
        aligner.processFinal(listOf("україна"))
        aligner.processPartial(listOf("є", "суверенна"))
        aligner.commitTentative()
        assertEquals(2, aligner.confirmedIndex)
        assertEquals(-1, aligner.tentativeIndex)
        assertEquals(3, aligner.matchedCount)
    }

    @Test
    fun `commitTentative without partial changes nothing`() {
        aligner.processFinal(listOf("україна", "є"))
        aligner.commitTentative()
        assertEquals(1, aligner.confirmedIndex)
        assertEquals(2, aligner.matchedCount)
    }

    @Test
    fun `reset clears matched count`() {
        aligner.processFinal(listOf("україна", "є"))
        aligner.reset()
        assertEquals(0, aligner.matchedCount)
    }

    // --- children's reading ---

    private fun read(vararg finals: String) = finals.forEach { aligner.processFinal(it.split(" ")) }

    private fun assertCleanReading(lastIndex: Int) {
        assertEquals(lastIndex, aligner.confirmedIndex)
        assertEquals(lastIndex + 1, aligner.matchedCount)
        assertEquals(emptySet<Int>(), aligner.skippedIndices)
        assertEquals(emptySet<Int>(), aligner.retriedIndices)
    }

    @Test
    fun `repeated word is not an error`() {
        read("україна україна є є суверенна")
        assertCleanReading(2)
    }

    @Test
    fun `re-reading the last few words is not an error`() {
        read("україна є суверенна україна є суверенна і")
        assertCleanReading(3)
    }

    @Test
    fun `repeat in a separate final is not an error`() {
        read("україна є", "є", "суверенна")
        assertCleanReading(2)
    }

    @Test
    fun `word read by syllables is matched`() {
        read("україна є су ве рен на")
        assertCleanReading(2)
    }

    @Test
    fun `syllables split across finals are matched`() {
        read("україна є су ве", "рен на і")
        assertCleanReading(3)
    }

    @Test
    fun `syllables in a partial show the word once complete`() {
        read("україна є")
        aligner.processPartial(listOf("су", "ве"))
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(2))
        aligner.processPartial(listOf("су", "ве", "рен", "на"))
        assertEquals(WordStatus.TENTATIVE, aligner.getWordStatus(2))
    }

    @Test
    fun `partial word before the whole word is not an error`() {
        read("україна є су суверенна")
        assertCleanReading(2)
    }

    @Test
    fun `repeating the previous word by syllables is not an error`() {
        read("україна у кра ї на є")
        assertCleanReading(1)
    }

    @Test
    fun `self-correction counts as read with several attempts`() {
        read("україна є суворна суверенна і")
        assertEquals(3, aligner.confirmedIndex)
        assertEquals(4, aligner.matchedCount)
        assertEquals(setOf(2), aligner.retriedIndices)
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(2))
    }

    @Test
    fun `self-correction across finals counts as several attempts`() {
        read("україна є суворна", "суверенна")
        assertEquals(2, aligner.confirmedIndex)
        assertEquals(setOf(2), aligner.retriedIndices)
    }

    @Test
    fun `self-correction after a pause in a partial`() {
        read("україна є")
        aligner.processPartial(listOf("суворна", "суверенна"))
        aligner.commitTentative()
        assertEquals(setOf(2), aligner.retriedIndices)
        assertEquals(3, aligner.matchedCount)
    }

    @Test
    fun `wrong word followed by a skip does not mark several attempts`() {
        read("україна суворна суверенна")
        assertEquals(setOf(1), aligner.skippedIndices)
        assertEquals(emptySet<Int>(), aligner.retriedIndices)
    }

    @Test
    fun `skipped word is moved past and marked skipped`() {
        read("україна суверенна і")
        assertEquals(3, aligner.confirmedIndex)
        assertEquals(3, aligner.matchedCount)
        assertEquals(setOf(1), aligner.skippedIndices)
        assertEquals(WordStatus.SKIPPED, aligner.getWordStatus(1))
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(2))
    }

    @Test
    fun `two skipped words are accepted when the next word confirms the jump`() {
        read("україна і незалежна")
        assertEquals(4, aligner.confirmedIndex)
        assertEquals(setOf(1, 2), aligner.skippedIndices)
    }

    @Test
    fun `two skipped words confirmed by the next final`() {
        read("україна і", "незалежна")
        assertEquals(4, aligner.confirmedIndex)
        assertEquals(setOf(1, 2), aligner.skippedIndices)
    }

    @Test
    fun `chance match two words ahead does not jump`() {
        // "і" happens to be 2 words ahead of "є", but what follows does not continue the text there
        read("україна і демократична")
        assertEquals(0, aligner.confirmedIndex)
        assertEquals(emptySet<Int>(), aligner.skippedIndices)
    }

    @Test
    fun `chance match far ahead does not jump`() {
        read("україна держава")
        assertEquals(0, aligner.confirmedIndex)
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(1))
    }

    @Test
    fun `unconfirmed jump at the end of a partial is not shown`() {
        read("україна")
        aligner.processPartial(listOf("і"))
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(1))
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(3))
    }

    @Test
    fun `unread words after the last read word are not skipped`() {
        read("україна є")
        assertEquals(emptySet<Int>(), aligner.skippedIndices)
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(2))
    }

    @Test
    fun `reset clears skipped and retried words`() {
        read("україна суворна суверенна", "незалежнааа і")
        aligner.reset()
        assertEquals(emptySet<Int>(), aligner.skippedIndices)
        assertEquals(emptySet<Int>(), aligner.retriedIndices)
    }
}
