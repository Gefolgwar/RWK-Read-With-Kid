package com.gefolgwar.readwithkid

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
        vm = ReadingSessionViewModel(engine, clockMs = { nowMs })
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
        val freshVm = ReadingSessionViewModel(freshEngine, modelInstalled = installed)
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
        val freshVm = ReadingSessionViewModel(freshEngine, modelInstalled = installed)
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
        val freshVm = ReadingSessionViewModel(freshEngine)
        assertEquals(SessionState.LOADING_MODEL, freshVm.uiState.value.sessionState)
        assertEquals(1, freshEngine.loadCount)
    }

    @Test
    fun `start before model is ready is ignored`() {
        val freshEngine = FakeVoskEngine()
        val freshVm = ReadingSessionViewModel(freshEngine)
        freshVm.updateText(text)
        freshVm.start()
        assertEquals(SessionState.LOADING_MODEL, freshVm.uiState.value.sessionState)
        assertEquals(0, freshEngine.startCount)
    }

    @Test
    fun `model load error shows MODEL_ERROR and blocks reading`() {
        val freshEngine = FakeVoskEngine()
        val freshVm = ReadingSessionViewModel(freshEngine)
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
        val grammarVm = ReadingSessionViewModel(engine, RecognitionMode.GRAMMAR_CONSTRAINED) { nowMs }
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
}
