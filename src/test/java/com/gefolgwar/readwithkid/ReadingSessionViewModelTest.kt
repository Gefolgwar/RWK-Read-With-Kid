package com.gefolgwar.readwithkid

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingSessionViewModelTest {

    private val text = "Мама мила раму. Тато читав книжку."

    private lateinit var engine: FakeVoskEngine
    private var nowMs = 0L
    private lateinit var vm: ReadingSessionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        engine = FakeVoskEngine()
        nowMs = 0L
        vm = ReadingSessionViewModel({ engine }, clockMs = { nowMs })
        engine.simulateModelLoaded()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = vm.uiState.value

    // --- model lifecycle ---

    @Test
    fun `missing model blocks reading and is loaded once downloaded`() {
        val installed = MutableStateFlow(false)
        val freshEngine = FakeVoskEngine()
        val freshVm = ReadingSessionViewModel({ freshEngine }, modelInstalled = { installed })
        assertEquals(SessionState.MODEL_MISSING, freshVm.uiState.value.sessionState)
        assertEquals(0, freshEngine.loadCount)

        freshVm.updateText(text)
        freshVm.start()
        assertEquals(0, freshEngine.startCount)

        installed.value = true
        assertEquals(SessionState.LOADING_MODEL, freshVm.uiState.value.sessionState)
        assertEquals(1, freshEngine.loadCount)
        freshEngine.simulateModelLoaded()
        assertEquals(SessionState.READY, freshVm.uiState.value.sessionState)
    }

    @Test
    fun `deleted model blocks reading until it is downloaded again`() {
        val installed = MutableStateFlow(true)
        val freshEngine = FakeVoskEngine()
        val freshVm = ReadingSessionViewModel({ freshEngine }, modelInstalled = { installed })
        freshEngine.simulateModelLoaded()
        freshVm.updateText(text)

        installed.value = false
        assertEquals(SessionState.MODEL_MISSING, freshVm.uiState.value.sessionState)
        freshVm.start()
        assertEquals(0, freshEngine.startCount)

        installed.value = true
        assertEquals(2, freshEngine.loadCount)
        freshEngine.simulateModelLoaded()
        freshVm.start()
        assertEquals(SessionState.READING, freshVm.uiState.value.sessionState)
    }

    @Test
    fun `initial state is LOADING_MODEL and model load requested once`() {
        val freshEngine = FakeVoskEngine()
        val freshVm = ReadingSessionViewModel({ freshEngine })
        assertEquals(SessionState.LOADING_MODEL, freshVm.uiState.value.sessionState)
        assertEquals(1, freshEngine.loadCount)
    }

    @Test
    fun `start before model is ready is ignored`() {
        val freshEngine = FakeVoskEngine()
        val freshVm = ReadingSessionViewModel({ freshEngine })
        freshVm.updateText(text)
        freshVm.start()
        assertEquals(SessionState.LOADING_MODEL, freshVm.uiState.value.sessionState)
        assertEquals(0, freshEngine.startCount)
    }

    @Test
    fun `model load error shows MODEL_ERROR and blocks reading`() {
        val freshEngine = FakeVoskEngine()
        val freshVm = ReadingSessionViewModel({ freshEngine })
        freshEngine.simulateError("Model load failed: no such dir")
        assertEquals(SessionState.MODEL_ERROR, freshVm.uiState.value.sessionState)

        freshVm.updateText(text)
        freshVm.start()
        assertEquals(SessionState.MODEL_ERROR, freshVm.uiState.value.sessionState)
        assertEquals(0, freshEngine.startCount)

        // A late "loaded" callback must not hide the error
        freshEngine.simulateModelLoaded()
        assertEquals(SessionState.MODEL_ERROR, freshVm.uiState.value.sessionState)
    }

    @Test
    fun `viewmodel is reused across recreation and model loads once`() {
        val store = ViewModelStore()
        var enginesCreated = 0
        val freshEngine = FakeVoskEngine()
        val factory = ReadingSessionViewModel.factory { enginesCreated++; freshEngine }

        val first = ViewModelProvider(store, factory)[ReadingSessionViewModel::class.java]
        val second = ViewModelProvider(store, factory)[ReadingSessionViewModel::class.java]

        assertSame(first, second)
        assertEquals(1, enginesCreated)
        assertEquals(1, freshEngine.loadCount)
    }

    @Test
    fun `clearing viewmodel releases engine`() {
        val store = ViewModelStore()
        val freshEngine = FakeVoskEngine()
        ViewModelProvider(store, ReadingSessionViewModel.factory { freshEngine })[ReadingSessionViewModel::class.java]
        store.clear()
        assertTrue(freshEngine.released)
    }

    // --- text ---

    @Test
    fun `updateText prepares sentences and words`() {
        vm.updateText(text)
        assertEquals(text, state.rawText)
        assertEquals(2, state.text.sentences.size)
        assertEquals(6, state.text.words.size)
    }

    @Test
    fun `start with empty text is ignored`() {
        vm.updateText("   ")
        vm.start()
        assertEquals(SessionState.READY, state.sessionState)
        assertEquals(0, engine.startCount)
    }

    @Test
    fun `text cannot change while reading`() {
        vm.updateText(text)
        vm.start()
        vm.updateText("Інший текст")
        assertEquals(text, state.rawText)
    }

    // --- full cycle ---

    @Test
    fun `full cycle text to reading to result`() {
        vm.updateText(text)
        vm.start()
        assertEquals(SessionState.READING, state.sessionState)
        assertEquals(RecognitionMode.FULL, engine.lastMode)
        assertTrue(engine.started)
        assertEquals(0, state.currentWordIndex)
        assertTrue(state.wordStatuses.all { it == WordStatus.UNREAD })

        nowMs = 2_000
        engine.simulatePartial("мама мила")
        assertEquals(WordStatus.TENTATIVE, state.wordStatuses[0])
        assertEquals(2, state.currentWordIndex)

        nowMs = 4_000
        engine.simulateFinal("мама мила раму")
        assertEquals(WordStatus.CONFIRMED, state.wordStatuses[2])
        assertEquals(3, state.currentWordIndex)
        assertEquals(SessionState.READING, state.sessionState)

        nowMs = 6_000
        engine.simulateFinal("тато читав книжку")

        assertEquals(SessionState.FINISHED, state.sessionState)
        assertFalse(engine.started)
        assertEquals(-1, state.currentWordIndex)
        val result = state.result!!
        assertEquals(6, result.totalWords)
        assertEquals(6, result.correctWords)
        assertEquals(1f, result.accuracy, 0.0001f)
        assertEquals(6_000L, result.durationMs)
        assertEquals(60, result.wordsCorrectPerMinute)
    }

    @Test
    fun `skipped word is marked skipped, lowers accuracy and is listed in result`() {
        vm.updateText(text)
        vm.start()
        engine.simulateFinal("мама мила тато") // "раму." skipped
        assertEquals(WordStatus.SKIPPED, state.wordStatuses[2])
        assertEquals(WordStatus.CONFIRMED, state.wordStatuses[3])
        assertEquals(4, state.currentWordIndex)

        nowMs = 30_000
        engine.simulateFinal("читав книжку")

        val result = state.result!!
        assertEquals(5, result.correctWords)
        assertEquals(5f / 6, result.accuracy, 0.0001f)
        assertEquals(10, result.wordsCorrectPerMinute)
        assertEquals(listOf("раму"), result.skippedWords)
        assertEquals(emptyList<String>(), result.retriedWords)
    }

    @Test
    fun `repeats, syllables and self-correction keep accuracy at 100 percent`() {
        vm.updateText(text)
        vm.start()
        engine.simulateFinal("мама мама мила ра му") // repeat, syllables
        engine.simulateFinal("тато читала читав") // self-correction
        engine.simulateFinal("читав книжку") // repeat after a pause

        assertEquals(SessionState.FINISHED, state.sessionState)
        val result = state.result!!
        assertEquals(6, result.correctWords)
        assertEquals(1f, result.accuracy, 0.0001f)
        assertEquals(emptyList<String>(), result.skippedWords)
        assertEquals(listOf("читав"), result.retriedWords)
    }

    @Test
    fun `closing result returns to text and allows reading again`() {
        vm.updateText(text)
        vm.start()
        engine.simulateFinal("мама мила раму тато читав книжку")
        vm.closeResult()
        assertEquals(SessionState.READY, state.sessionState)
        assertNull(state.result)
        assertEquals(text, state.rawText)

        vm.start()
        assertEquals(SessionState.READING, state.sessionState)
        assertTrue(state.wordStatuses.all { it == WordStatus.UNREAD })
        assertEquals(2, engine.startCount)
    }

    // --- saving results ---

    private fun savingVm(readings: ReadingRepository, dateMs: Long): ReadingSessionViewModel {
        val savingEngine = FakeVoskEngine()
        engine = savingEngine
        return ReadingSessionViewModel({ savingEngine }, readings = readings, wallClockMs = { dateMs }) { nowMs }
            .also { savingEngine.simulateModelLoaded() }
    }

    @Test
    fun `a finished reading of a saved text is saved with its metrics`() {
        val readings = InMemoryReadingRepository()
        vm = savingVm(readings, dateMs = 1_700_000_000_000)
        vm.updateText(text, TextLanguage.UK, TextReading(textId = 3, textTitle = "Казка"))
        vm.start()
        engine.simulateFinal("мама мила тато читала") // "раму." skipped, wrong attempt at "читав"
        nowMs = 30_000
        engine.simulateFinal("читав книжку")

        val saved = readings.all.single()
        assertEquals(3L, saved.textId)
        assertEquals("Казка", saved.textTitle)
        assertEquals(1_700_000_000_000, saved.readAt)
        assertEquals(state.result, saved.result)
        assertEquals(6, saved.result.totalWords)
        assertEquals(5, saved.result.correctWords)
        assertEquals(30_000L, saved.result.durationMs)
        assertEquals(10, saved.result.wordsCorrectPerMinute)
        assertEquals(listOf("раму"), saved.result.skippedWords)
        assertEquals(listOf("читав"), saved.result.retriedWords)
    }

    @Test
    fun `an early finish is saved too, and each reading again adds a record`() {
        val readings = InMemoryReadingRepository()
        vm = savingVm(readings, dateMs = 1)
        vm.updateText(text, TextLanguage.UK, TextReading(textId = 3, textTitle = "Казка"))
        vm.start()
        engine.simulateFinal("мама мила")
        vm.finish()
        vm.closeResult()
        vm.start()
        engine.simulateFinal("мама мила раму тато читав книжку")

        assertEquals(listOf(2, 6), readings.all.map { it.result.correctWords })
    }

    @Test
    fun `reading a text that is not in the library is not saved`() {
        val readings = InMemoryReadingRepository()
        vm = savingVm(readings, dateMs = 1)
        vm.updateText(text, TextLanguage.UK, TextReading(textId = 3, textTitle = "Казка"))
        vm.updateText(text, TextLanguage.UK)
        vm.start()
        engine.simulateFinal("мама мила раму тато читав книжку")

        assertEquals(SessionState.FINISHED, state.sessionState)
        assertTrue(readings.all.isEmpty())
    }

    @Test
    fun `a saved text shows its earlier readings, newest first, with the one just finished`() {
        val readings = InMemoryReadingRepository()
        fun record(textId: Long, readAt: Long) =
            ReadingRecord(textId = textId, textTitle = "Казка", readAt = readAt, result = ReadingResult(6, 3, 60_000, emptyList(), emptyList()))
        runBlocking {
            readings.saveReading(record(textId = 3, readAt = 1))
            readings.saveReading(record(textId = 4, readAt = 2))
        }
        vm = savingVm(readings, dateMs = 5)
        vm.updateText(text, TextLanguage.UK, TextReading(textId = 3, textTitle = "Казка"))
        assertEquals(listOf(1L), state.history.map { it.readAt })

        vm.start()
        engine.simulateFinal("мама мила раму тато читав книжку")
        vm.closeResult()
        assertEquals(listOf(5L, 1L), state.history.map { it.readAt })
        assertEquals(6, state.history.first().result.correctWords)

        vm.updateText("Кіт спав.", TextLanguage.UK, TextReading(textId = 4, textTitle = "Кіт"))
        assertEquals(listOf(2L), state.history.map { it.readAt })
        vm.updateText("Кіт спав.")
        assertTrue(state.history.isEmpty())
    }

    // --- pause / resume ---

    @Test
    fun `pause stops microphone and resume restarts it`() {
        vm.updateText(text)
        vm.start()
        vm.pause()
        assertEquals(SessionState.PAUSED, state.sessionState)
        assertFalse(engine.started)
        assertEquals(1, engine.stopCount)

        vm.resume()
        assertEquals(SessionState.READING, state.sessionState)
        assertTrue(engine.started)
        assertEquals(2, engine.startCount)
    }

    @Test
    fun `results arriving while paused are ignored`() {
        vm.updateText(text)
        vm.start()
        vm.pause()
        engine.simulateFinal("мама мила")
        assertTrue(state.wordStatuses.all { it == WordStatus.UNREAD })
    }

    @Test
    fun `pause keeps progress and commits the last partial`() {
        vm.updateText(text)
        vm.start()
        engine.simulateFinal("мама")
        engine.simulatePartial("мила")
        vm.pause()
        assertEquals(WordStatus.CONFIRMED, state.wordStatuses[1])
        assertEquals(2, state.currentWordIndex)

        vm.resume()
        engine.simulateFinal("раму")
        assertEquals(WordStatus.CONFIRMED, state.wordStatuses[2])
    }

    @Test
    fun `paused time is not counted in duration`() {
        vm.updateText(text)
        vm.start()
        nowMs = 10_000
        vm.pause()
        nowMs = 100_000
        vm.resume()
        nowMs = 110_000
        engine.simulateFinal("мама мила раму тато читав книжку")
        assertEquals(20_000L, state.result!!.durationMs)
        assertEquals(18, state.result!!.wordsCorrectPerMinute)
    }

    @Test
    fun `pause and resume ignored in wrong state`() {
        vm.updateText(text)
        vm.pause()
        vm.resume()
        assertEquals(SessionState.READY, state.sessionState)
        assertEquals(0, engine.stopCount)
        assertEquals(0, engine.startCount)
    }

    // --- early finish ---

    @Test
    fun `early finish shows partial result`() {
        vm.updateText(text)
        vm.start()
        nowMs = 6_000
        engine.simulateFinal("мама мила")
        engine.simulatePartial("раму")
        vm.finish()

        assertEquals(SessionState.FINISHED, state.sessionState)
        assertFalse(engine.started)
        val result = state.result!!
        assertEquals(3, result.correctWords)
        assertEquals(0.5f, result.accuracy, 0.0001f)
        assertEquals(30, result.wordsCorrectPerMinute)
        assertEquals(6_000L, result.durationMs)
    }

    @Test
    fun `finish while paused does not stop engine twice`() {
        vm.updateText(text)
        vm.start()
        nowMs = 5_000
        vm.pause()
        nowMs = 50_000
        vm.finish()
        assertEquals(SessionState.FINISHED, state.sessionState)
        assertEquals(1, engine.stopCount)
        assertEquals(5_000L, state.result!!.durationMs)
        assertEquals(0, state.result!!.correctWords)
        assertEquals(0, state.result!!.wordsCorrectPerMinute)
    }

    @Test
    fun `finish ignored when not reading`() {
        vm.updateText(text)
        vm.finish()
        assertEquals(SessionState.READY, state.sessionState)
        assertNull(state.result)
    }

    // --- errors during reading ---

    @Test
    fun `recognition error while reading stops and returns to text`() {
        vm.updateText(text)
        vm.start()
        engine.simulateError("AudioRecord init failed")
        assertEquals(SessionState.READY, state.sessionState)
        assertTrue(state.recognitionError)
        assertFalse(engine.started)

        vm.start()
        assertFalse(state.recognitionError)
    }

    // --- grammar fallback mode ---

    @Test
    fun `grammar mode updates grammar at sentence boundary without restarting audio`() {
        engine = FakeVoskEngine()
        val grammarVm = ReadingSessionViewModel({ engine }, RecognitionMode.GRAMMAR_CONSTRAINED) { nowMs }
        engine.simulateModelLoaded()
        grammarVm.updateText("Мама мила раму. Тато читав книжку. Кіт спав.")
        grammarVm.start()
        assertEquals(listOf("мама", "мила", "раму", "тато", "читав", "книжку", "[unk]"), engine.lastGrammar)

        engine.simulateFinal("мама мила раму тато")

        assertEquals(1, engine.grammarUpdateCount)
        assertEquals(1, engine.startCount)
        assertEquals(0, engine.stopCount)
        assertEquals(listOf("тато", "читав", "книжку", "кіт", "спав", "[unk]"), engine.lastGrammar)
    }

    @Test
    fun `full mode never sends grammar`() {
        vm.updateText(text)
        vm.start()
        engine.simulateFinal("мама мила раму тато")
        assertNull(engine.lastGrammar)
        assertEquals(0, engine.grammarUpdateCount)
    }

    // --- Vosk JSON ---

    @Test
    fun `extractVoskText parses partial and final json`() {
        assertEquals("hello world", ReadingSessionViewModel.extractVoskText("""{"partial":"hello world"}""", "partial"))
        assertEquals("hello", ReadingSessionViewModel.extractVoskText("""{"text":"hello"}""", "text"))
    }

    @Test
    fun `extractVoskText returns empty for missing key or malformed json`() {
        assertEquals("", ReadingSessionViewModel.extractVoskText("""{"partial":"hello"}""", "text"))
        assertEquals("", ReadingSessionViewModel.extractVoskText("""{"text":""", "text"))
    }

    @Test
    fun `extractVoskText handles multiline vosk result with escaped quotes`() {
        val json = """
            {
              "result" : [{"conf" : 1.0, "end" : 1.2, "start" : 0.6, "word" : "a"}],
              "text" : "a \"b\""
            }
        """.trimIndent()
        assertEquals("a \"b\"", ReadingSessionViewModel.extractVoskText(json, "text"))
    }

    // --- language of the text ---

    private val germanText = "Die kleine Katze läuft über die Straße. Sie heißt Mimi."

    @Test
    fun `German text starts a new engine with the German model and releases the Ukrainian one`() {
        val engines = mutableMapOf<TextLanguage, FakeVoskEngine>()
        val installed = mapOf(TextLanguage.UK to MutableStateFlow(true), TextLanguage.DE to MutableStateFlow(false))
        val freshVm = ReadingSessionViewModel(
            { FakeVoskEngine().also { engine -> engines[it] = engine } },
            modelInstalled = { installed.getValue(it) }
        )
        val uk = engines.getValue(TextLanguage.UK)
        uk.simulateModelLoaded()

        freshVm.updateText(germanText, TextLanguage.DE)
        assertTrue(uk.released)
        assertEquals(TextLanguage.DE, freshVm.uiState.value.language)
        // The German model is not downloaded yet, even though the Ukrainian one is
        assertEquals(SessionState.MODEL_MISSING, freshVm.uiState.value.sessionState)

        installed.getValue(TextLanguage.DE).value = true
        val de = engines.getValue(TextLanguage.DE)
        assertEquals(1, de.loadCount)
        de.simulateModelLoaded()
        assertEquals(SessionState.READY, freshVm.uiState.value.sessionState)

        // The Ukrainian model's availability no longer affects the session
        installed.getValue(TextLanguage.UK).value = false
        assertEquals(SessionState.READY, freshVm.uiState.value.sessionState)
    }

    @Test
    fun `text in the same language keeps the engine and its model`() {
        var created = 0
        val freshEngine = FakeVoskEngine()
        val freshVm = ReadingSessionViewModel({ created++; freshEngine })
        freshVm.updateText(text, TextLanguage.UK)
        freshVm.updateText("Кіт спав.", TextLanguage.UK)
        assertEquals(1, created)
        assertEquals(1, freshEngine.loadCount)
        assertFalse(freshEngine.released)
    }

    @Test
    fun `clearing viewmodel releases the engine of the current language`() {
        val store = ViewModelStore()
        val engines = mutableListOf<FakeVoskEngine>()
        val freshVm = ViewModelProvider(store, ReadingSessionViewModel.factory { FakeVoskEngine().also(engines::add) })[
            ReadingSessionViewModel::class.java
        ]
        freshVm.updateText(germanText, TextLanguage.DE)
        store.clear()
        assertTrue(engines.all { it.released })
    }

    @Test
    fun `German reading with capitals, ß, umlauts, repeats and a skip gives a result`() {
        val engines = mutableMapOf<TextLanguage, FakeVoskEngine>()
        val freshVm = ReadingSessionViewModel({ FakeVoskEngine().also { engine -> engines[it] = engine } }) { nowMs }
        freshVm.updateText(germanText, TextLanguage.DE)
        val de = engines.getValue(TextLanguage.DE)
        de.simulateModelLoaded()
        freshVm.start()

        de.simulateFinal("die die katze lauft läuft ü ber die strasse") // repeat, skip "kleine", self-correction, syllables, ss
        nowMs = 30_000
        de.simulateFinal("sie heisst mimi")

        val s = freshVm.uiState.value
        assertEquals(SessionState.FINISHED, s.sessionState)
        val result = s.result!!
        assertEquals(10, result.totalWords)
        assertEquals(9, result.correctWords)
        assertEquals(listOf("kleine"), result.skippedWords)
        assertEquals(listOf("läuft"), result.retriedWords)
        assertEquals(18, result.wordsCorrectPerMinute)
    }

    // --- pronunciation help ---

    private fun helpVm(
        pronunciation: PronunciationHelper,
        readings: ReadingRepository? = null,
        engines: MutableMap<TextLanguage, FakeVoskEngine> = mutableMapOf()
    ): ReadingSessionViewModel {
        val helpVm = ReadingSessionViewModel(
            { FakeVoskEngine().also { engine -> engines[it] = engine; this.engine = engine } },
            readings = readings,
            pronunciation = pronunciation
        ) { nowMs }
        engine.simulateModelLoaded()
        return helpVm
    }

    @Test
    fun `a tapped word is said in the language of the text and saved with the reading`() {
        val readings = InMemoryReadingRepository()
        val helper = FakePronunciationHelper(TextLanguage.UK, TextLanguage.DE)
        vm = helpVm(helper, readings)
        vm.updateText(text, TextLanguage.UK, TextReading(textId = 3, textTitle = "Казка"))
        vm.start()
        assertTrue(state.helpAvailable)

        engine.simulateFinal("мама мила")
        vm.sayWord(2) // "раму."
        helper.finishSaying()
        vm.sayWord(5) // "книжку."
        helper.finishSaying()
        vm.sayWord(2) // again
        helper.finishSaying()
        engine.simulateFinal("раму тато читав книжку")

        assertEquals(listOf("раму" to TextLanguage.UK, "книжку" to TextLanguage.UK, "раму" to TextLanguage.UK), helper.said)
        assertEquals(listOf("раму", "книжку"), state.result!!.helpedWords)
        assertEquals(listOf("раму", "книжку"), readings.all.single().result.helpedWords)
        // Help does not count against the reading
        assertEquals(6, readings.all.single().result.correctWords)
    }

    @Test
    fun `a German word is said in German`() {
        val helper = FakePronunciationHelper(TextLanguage.UK, TextLanguage.DE)
        val engines = mutableMapOf<TextLanguage, FakeVoskEngine>()
        vm = helpVm(helper, engines = engines)
        vm.updateText(germanText, TextLanguage.DE)
        engines.getValue(TextLanguage.DE).simulateModelLoaded()
        vm.start()
        vm.sayWord(6) // "Straße."
        assertEquals(listOf("Straße" to TextLanguage.DE), helper.said)
    }

    @Test
    fun `the microphone does not hear the word while it is being said`() {
        val helper = FakePronunciationHelper(TextLanguage.UK)
        vm = helpVm(helper)
        vm.updateText(text)
        vm.start()

        vm.sayWord(0)
        assertTrue(engine.paused)
        vm.sayWord(1) // a second tap before the first word is finished
        helper.finishSaying()
        assertFalse(engine.paused)
        assertTrue(engine.started)
    }

    @Test
    fun `resuming while a word is being said keeps the microphone deaf until it is finished`() {
        val helper = FakePronunciationHelper(TextLanguage.UK)
        vm = helpVm(helper)
        vm.updateText(text)
        vm.start()
        vm.pause()
        vm.sayWord(0)
        vm.resume()
        assertTrue(engine.paused)
        helper.finishSaying()
        assertFalse(engine.paused)
    }

    @Test
    fun `help is hidden and does nothing when the phone has no voice for the language`() {
        val helper = FakePronunciationHelper(TextLanguage.UK)
        val engines = mutableMapOf<TextLanguage, FakeVoskEngine>()
        vm = helpVm(helper, engines = engines)
        vm.updateText(text)
        assertTrue(state.helpAvailable)

        vm.updateText(germanText, TextLanguage.DE)
        assertFalse(state.helpAvailable)
        engines.getValue(TextLanguage.DE).simulateModelLoaded()
        vm.start()
        vm.sayWord(0)
        assertTrue(helper.said.isEmpty())
        assertFalse(engines.getValue(TextLanguage.DE).paused)

        // A voice installed later shows the help
        helper.voices.getValue(TextLanguage.DE).value = true
        assertTrue(state.helpAvailable)
    }

    @Test
    fun `without a speech service there is no help`() {
        vm.updateText(text)
        vm.start()
        vm.sayWord(0)
        assertFalse(state.helpAvailable)
        assertFalse(engine.paused)
    }

    @Test
    fun `words are said only during reading, and a new reading starts without help`() {
        val helper = FakePronunciationHelper(TextLanguage.UK)
        vm = helpVm(helper)
        vm.updateText(text)
        vm.sayWord(0)
        assertTrue(helper.said.isEmpty())

        vm.start()
        vm.sayWord(0)
        helper.finishSaying()
        vm.finish()
        vm.sayWord(1)
        assertEquals(1, helper.said.size)

        vm.closeResult()
        vm.start()
        vm.finish()
        assertEquals(emptyList<String>(), state.result!!.helpedWords)
    }

    @Test
    fun `clearing viewmodel releases the speech service`() {
        val store = ViewModelStore()
        val helper = FakePronunciationHelper(TextLanguage.UK)
        ViewModelProvider(store, ReadingSessionViewModel.factory(createPronunciation = { helper }) { FakeVoskEngine() })[
            ReadingSessionViewModel::class.java
        ]
        store.clear()
        assertTrue(helper.released)
    }

    @Test
    fun `grammar uses words as written, not their normalized form`() {
        val engines = mutableMapOf<TextLanguage, FakeVoskEngine>()
        val grammarVm = ReadingSessionViewModel(
            { FakeVoskEngine().also { engine -> engines[it] = engine } },
            RecognitionMode.GRAMMAR_CONSTRAINED
        ) { nowMs }
        grammarVm.updateText("Die große Straße in Baden-Württemberg.", TextLanguage.DE)
        engines.getValue(TextLanguage.DE).simulateModelLoaded()
        grammarVm.start()
        assertEquals(
            listOf("die", "große", "straße", "in", "baden-württemberg", "[unk]"),
            engines.getValue(TextLanguage.DE).lastGrammar
        )
    }
}
