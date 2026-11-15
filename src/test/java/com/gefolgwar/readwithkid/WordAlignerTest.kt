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

    // --- German children's reading ---

    private val german = TextPreparation.prepare("Die kleine Katze läuft über die große Straße nach Baden-Württemberg.")
        .words.map { it.display }

    private fun readGerman(vararg finals: String) = WordAligner(german).apply { finals.forEach { processFinal(it.split(" ")) } }

    private fun WordAligner.assertClean(lastIndex: Int) {
        assertEquals(lastIndex, confirmedIndex)
        assertEquals(lastIndex + 1, matchedCount)
        assertEquals(emptySet<Int>(), skippedIndices)
        assertEquals(emptySet<Int>(), retriedIndices)
    }

    @Test
    fun `German nouns written with capitals match lowercase speech`() {
        readGerman("die kleine katze läuft").assertClean(3)
        readGerman("DIE KLEINE KATZE").assertClean(2)
    }

    @Test
    fun `German ß and ss are the same`() {
        readGerman("die kleine katze läuft über die grosse strasse").assertClean(7)
        WordAligner(listOf("Strasse", "heisst", "STRAẞE")).apply { processFinal(listOf("straße", "heißt", "straße")) }.assertClean(2)
    }

    @Test
    fun `German umlauts match composed, decomposed and ae-oe-ue spelling`() {
        // Text with decomposed umlauts (a + U+0308), speech with composed ones
        WordAligner(listOf("läuft", "über", "größe")).apply { processFinal(listOf("läuft", "über", "größe")) }
            .assertClean(2)
        // Text typed without umlauts
        WordAligner(listOf("laeuft", "ueber", "groesse")).apply { processFinal(listOf("läuft", "über", "größe")) }.assertClean(2)
        readGerman("die kleine katze laeuft ueber").assertClean(4)
    }

    @Test
    fun `umlaut and plain vowel stay different words`() {
        val aligner = WordAligner(listOf("schön", "Tag"))
        aligner.processFinal(listOf("schon"))
        assertEquals(-1, aligner.confirmedIndex)
    }

    @Test
    fun `German hyphenated word matches whole or as its spoken parts`() {
        val upToNach = "die kleine katze läuft über die große straße nach"
        readGerman("$upToNach baden württemberg").assertClean(9)
        readGerman("$upToNach baden-württemberg").assertClean(9)
        readGerman("$upToNach badenwürttemberg").assertClean(9)
    }

    @Test
    fun `German repeats and syllables are not errors`() {
        readGerman("die die kleine kat ze katze läuft ü ber", "über die gro ße").assertClean(6)
    }

    @Test
    fun `German self-correction and skip are recorded`() {
        val aligner = readGerman("die kleine katze lauft läuft die große")
        assertEquals(6, aligner.confirmedIndex)
        assertEquals(setOf(3), aligner.retriedIndices)
        assertEquals(setOf(4), aligner.skippedIndices)
    }

    // --- words the model does not know ---

    // "замісила"(1) and "вистигав"(4) are not in the model's vocabulary
    private val kolobok = listOf("Баба", "замісила", "тісто.", "Колобок", "вистигав", "на", "вікні.")
    private val kolobokUnknown = setOf(1, 4)

    @Test
    fun `unknown words read correctly are passed with their neighbours and are not errors`() {
        val aligner = WordAligner(kolobok, kolobokUnknown)
        // The model says something else in place of an unknown word
        aligner.processFinal(listOf("баба", "за", "місила"))
        assertEquals(0, aligner.confirmedIndex)
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(1))

        aligner.processFinal(listOf("тісто", "колобок", "вистиг", "на", "вікні"))
        assertEquals(kolobok.lastIndex, aligner.confirmedIndex)
        assertTrue(kolobok.indices.all { aligner.getWordStatus(it) == WordStatus.CONFIRMED })
        assertEquals(5, aligner.knownCount)
        assertEquals(5, aligner.matchedCount)
        assertEquals(emptySet<Int>(), aligner.skippedIndices)
        assertEquals(emptySet<Int>(), aligner.retriedIndices)
    }

    @Test
    fun `unknown word is passed as soon as the next known word is heard`() {
        val aligner = WordAligner(kolobok, kolobokUnknown)
        aligner.processPartial(listOf("баба", "тісто"))
        assertEquals(2, aligner.tentativeIndex)
        assertEquals(WordStatus.TENTATIVE, aligner.getWordStatus(1))
    }

    @Test
    fun `skipped known word next to an unknown one is a skip`() {
        val aligner = WordAligner(kolobok, kolobokUnknown)
        aligner.processFinal(listOf("баба", "колобок", "на", "вікні")) // "тісто" skipped
        assertEquals(kolobok.lastIndex, aligner.confirmedIndex)
        assertEquals(setOf(2), aligner.skippedIndices)
        assertEquals(WordStatus.SKIPPED, aligner.getWordStatus(2))
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(1))
        assertEquals(4, aligner.matchedCount)
    }

    @Test
    fun `unknown word at the end is passed with the last known word`() {
        val aligner = WordAligner(listOf("Колобок", "пирхав"), setOf(1))
        aligner.processFinal(listOf("колобок"))
        assertEquals(1, aligner.confirmedIndex)
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(1))
        assertEquals(1, aligner.knownCount)
    }

    @Test
    fun `wrong attempt at a known word is still recorded when no unknown word stands before it`() {
        val aligner = WordAligner(kolobok, kolobokUnknown)
        aligner.processFinal(listOf("баба", "тісто", "колобка", "колобок"))
        assertEquals(setOf(3), aligner.retriedIndices)
    }

    // --- what was heard, and reading a skipped word again ---

    @Test
    fun `a read word was heard as itself, also when read by syllables`() {
        read("укра їна є")
        assertEquals("україна", aligner.heard(0))
        assertEquals("є", aligner.heard(1))
    }

    @Test
    fun `a misread word was heard as what was said in its place`() {
        read("україна є сувора і")
        assertEquals(WordStatus.SKIPPED, aligner.getWordStatus(2))
        assertEquals("сувора", aligner.heard(2))
    }

    @Test
    fun `a silently skipped word was heard as nothing`() {
        read("україна суверенна")
        assertEquals(WordStatus.SKIPPED, aligner.getWordStatus(1))
        assertEquals("", aligner.heard(1))
    }

    @Test
    fun `words not passed yet and words the model cannot recognise have nothing heard`() {
        val aligner = WordAligner(kolobok, kolobokUnknown)
        aligner.processFinal(listOf("баба", "за", "місила", "тісто"))
        assertNull(aligner.heard(1))
        assertNull(aligner.heard(3))
    }

    @Test
    fun `what was heard keeps the model's spelling`() {
        val aligner = WordAligner(listOf("schöne", "Straße", "heute", "ist"))
        aligner.processFinal(listOf("schöne", "strasse", "hoite", "ist"))
        assertEquals("schöne", aligner.heard(0))
        assertEquals("strasse", aligner.heard(1))
        assertEquals("hoite", aligner.heard(2))
    }

    @Test
    fun `a skipped word read again correctly counts as read after a wrong attempt`() {
        read("україна є сувора і")
        assertTrue(aligner.retry(2, listOf("суверенна"), final = true))
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(2))
        assertEquals("суверенна", aligner.heard(2))
        assertEquals(4, aligner.matchedCount)
        assertEquals(emptySet<Int>(), aligner.skippedIndices)
        assertEquals(setOf(2), aligner.retriedIndices)
        // Reading goes on from where it was
        assertEquals(3, aligner.confirmedIndex)
    }

    @Test
    fun `a skipped word read again by syllables is recognised on a partial result`() {
        read("україна є сувора і")
        assertTrue(aligner.retry(2, listOf("су", "ве", "рен", "на"), final = false))
        assertEquals(WordStatus.CONFIRMED, aligner.getWordStatus(2))
    }

    @Test
    fun `a wrong attempt at a skipped word is heard once the result is final`() {
        read("україна є сувора і")
        assertFalse(aligner.retry(2, listOf("сові"), final = false))
        assertEquals("сувора", aligner.heard(2))
        assertFalse(aligner.retry(2, listOf("соверенна"), final = true))
        assertEquals("соверенна", aligner.heard(2))
        assertEquals(WordStatus.SKIPPED, aligner.getWordStatus(2))
        assertEquals(setOf(2), aligner.skippedIndices)
    }

    @Test
    fun `only a skipped word can be read again`() {
        read("україна є")
        assertFalse(aligner.retry(1, listOf("є"), final = true))
        assertFalse(aligner.retry(2, listOf("суверенна"), final = true))
        assertEquals(WordStatus.UNREAD, aligner.getWordStatus(2))
        assertEquals(emptySet<Int>(), aligner.retriedIndices)
    }
}
