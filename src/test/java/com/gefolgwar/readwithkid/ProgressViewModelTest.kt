package com.gefolgwar.readwithkid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** A child's progress, built from readings saved by [ReadingSessionViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private lateinit var children: InMemoryChildRepository
    private lateinit var readings: InMemoryReadingRepository
    private lateinit var vm: ProgressViewModel
    private lateinit var olya: ChildProfile
    private lateinit var petro: ChildProfile
    private var dateMs = 1_000L

    private val state get() = vm.uiState.value

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        children = InMemoryChildRepository()
        readings = InMemoryReadingRepository()
        vm = ProgressViewModel(children, readings)
        olya = runBlocking { ChildProfile(name = "Оля", avatar = Avatar.RABBIT).let { it.copy(id = children.saveChild(it)) } }
        petro = runBlocking { ChildProfile(name = "Петро", avatar = Avatar.ROCKET).let { it.copy(id = children.saveChild(it)) } }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Reads [text] through the reading session: [finals] are what the recognizer hears, one per pause. */
    private fun read(child: ChildProfile, textId: Long, title: String, text: String, durationMs: Long, vararg finals: String) {
        val engine = FakeVoskEngine()
        var nowMs = 0L
        val session = ReadingSessionViewModel({ engine }, readings = readings, wallClockMs = { dateMs++ }) { nowMs }
        engine.simulateModelLoaded()
        session.updateText(text, TextLanguage.UK, ChildReading(child.id, textId, title))
        session.start()
        nowMs = durationMs
        finals.forEach(engine::simulateFinal)
        session.finish()
    }

    private val cat = "Кіт спить на печі."
    private val mom = "Мама мила раму. Тато читав книжку."

    @Test
    fun `a child without readings has an empty history`() {
        read(petro, 1, "Кіт", cat, 10_000, "кіт спить на печі")
        vm.open(olya)
        assertEquals(olya, state.child)
        assertTrue(state.readings.isEmpty())
        assertTrue(state.repeatedTexts.isEmpty())
        assertTrue(state.problemWords.isEmpty())
    }

    @Test
    fun `history lists only the child's readings, oldest first, with accuracy and speed`() {
        read(olya, 1, "Кіт", cat, 12_000, "кіт на печі") // "спить" skipped
        read(petro, 2, "Мама", mom, 10_000, "мама мила раму тато читав книжку")
        read(olya, 2, "Мама", mom, 6_000, "мама мила раму тато читав книжку")

        vm.open(olya)

        assertEquals(listOf("Кіт", "Мама"), state.readings.map { it.textTitle })
        assertEquals(listOf(0.75f, 1f), state.readings.map { it.result.accuracy })
        assertEquals(listOf(15, 60), state.readings.map { it.result.wordsCorrectPerMinute })
        assertTrue(state.readings[0].readAt < state.readings[1].readAt)
    }

    @Test
    fun `repeated readings of one text are grouped for comparison`() {
        read(olya, 2, "Мама", mom, 12_000, "мама мила тато читав книжку")
        read(olya, 1, "Кіт", cat, 10_000, "кіт спить на печі")
        read(olya, 2, "Мама", mom, 6_000, "мама мила раму тато читав книжку")

        vm.open(olya)

        val repeated = state.repeatedTexts.single()
        assertEquals("Мама", repeated.textTitle)
        assertEquals(listOf(25, 60), repeated.readings.map { it.result.wordsCorrectPerMinute })
        assertEquals(listOf(5, 6), repeated.readings.map { it.result.correctWords })
    }

    @Test
    fun `most frequent problem words come first and include self-corrected words`() {
        read(olya, 2, "Мама", mom, 10_000, "мама мила тато читала", "читав книжку") // skip "раму", retry "читав"
        read(olya, 2, "Мама", mom, 10_000, "мама мила тато читав книжку") // skip "раму"
        read(olya, 1, "Кіт", cat, 10_000, "кіт на печі") // skip "спить"
        read(petro, 1, "Кіт", cat, 10_000, "кіт на печі")

        vm.open(olya)

        assertEquals(
            listOf(ProblemWord("раму", 2), ProblemWord("спить", 1), ProblemWord("читав", 1)),
            state.problemWords
        )
    }

    @Test
    fun `problem words are counted regardless of capitals and limited to the top ten`() {
        repeat(12) { i ->
            runBlocking {
                readings.saveReading(
                    ReadingRecord(
                        childId = olya.id, textId = 1, textTitle = "Слова", readAt = i.toLong(),
                        result = ReadingResult(20, 18, 10_000, listOf("Слово$i", "слово$i"), emptyList())
                    )
                )
            }
        }
        vm.open(olya)
        assertEquals(ProgressViewModel.MAX_PROBLEM_WORDS, state.problemWords.size)
        assertTrue(state.problemWords.all { it.count == 2 && it.word == it.word.lowercase() })
    }

    @Test
    fun `closing hides the progress, and so does deleting the child`() {
        read(olya, 1, "Кіт", cat, 10_000, "кіт спить на печі")
        vm.open(olya)
        vm.close()
        assertNull(state.child)
        assertTrue(state.readings.isEmpty())

        vm.open(olya)
        runBlocking { children.deleteChild(olya.id) }
        assertNull(state.child)
    }
}
