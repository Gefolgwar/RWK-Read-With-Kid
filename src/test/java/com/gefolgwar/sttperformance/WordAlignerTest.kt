package com.gefolgwar.sttperformance

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
}
