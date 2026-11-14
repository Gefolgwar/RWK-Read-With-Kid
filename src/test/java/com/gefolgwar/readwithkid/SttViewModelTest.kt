package com.gefolgwar.readwithkid

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SttViewModelTest {

    private lateinit var engine: FakeVoskEngine
    private lateinit var store: ViewModelStore
    private lateinit var vm: SttViewModel

    @Before
    fun setup() {
        engine = FakeVoskEngine()
        store = ViewModelStore()
        vm = ViewModelProvider(store, SttViewModel.factory { engine })[SttViewModel::class.java]
        engine.simulateModelLoaded()
    }

    @Test
    fun `initial state is LOADING_MODEL and model load requested once`() {
        val freshEngine = FakeVoskEngine()
        val freshVm = SttViewModel(freshEngine)
        assertEquals(AppState.LOADING_MODEL, freshVm.uiState.value.appState)
        assertEquals(1, freshEngine.loadCount)
    }

    @Test
    fun `start before model is ready is ignored`() {
        val freshEngine = FakeVoskEngine()
        val freshVm = SttViewModel(freshEngine)
        freshVm.start()
        assertEquals(AppState.LOADING_MODEL, freshVm.uiState.value.appState)
        assertEquals(0, freshEngine.startCount)
    }

    @Test
    fun `start allowed once model is loaded`() {
        val freshEngine = FakeVoskEngine()
        val freshVm = SttViewModel(freshEngine)
        freshEngine.simulateModelLoaded()
        assertEquals(AppState.IDLE, freshVm.uiState.value.appState)
        freshVm.start()
        assertEquals(AppState.LISTENING, freshVm.uiState.value.appState)
        assertEquals(1, freshEngine.startCount)
    }

    @Test
    fun `late model loaded callback does not interrupt listening`() {
        vm.start()
        engine.simulateModelLoaded()
        assertEquals(AppState.LISTENING, vm.uiState.value.appState)
    }

    @Test
    fun `viewmodel is reused across recreation and model loads once`() {
        val freshStore = ViewModelStore()
        var enginesCreated = 0
        val freshEngine = FakeVoskEngine()
        val factory = SttViewModel.factory { enginesCreated++; freshEngine }

        val first = ViewModelProvider(freshStore, factory)[SttViewModel::class.java]
        // Simulates configuration change: a new provider over the same retained store
        val second = ViewModelProvider(freshStore, factory)[SttViewModel::class.java]

        assertSame(first, second)
        assertEquals(1, enginesCreated)
        assertEquals(1, freshEngine.loadCount)
    }

    @Test
    fun `clearing viewmodel releases engine`() {
        vm.start()
        assertFalse(engine.released)
        store.clear()
        assertTrue(engine.released)
    }

    @Test
    fun `raw log is bounded`() {
        vm.start()
        repeat(SttViewModel.MAX_RAW_LOG_LINES + 50) { engine.simulateError("err$it") }
        val log = vm.uiState.value.rawLog
        assertEquals(SttViewModel.MAX_RAW_LOG_LINES, log.size)
        assertEquals("E: err${SttViewModel.MAX_RAW_LOG_LINES + 49}", log.last())
    }

    @Test
    fun `initial state is IDLE with first excerpt selected after model load`() {
        val state = vm.uiState.value
        assertEquals(AppState.IDLE, state.appState)
        assertEquals(RecognitionMode.FULL, state.mode)
        assertEquals(0, state.selectedExcerptIndex)
        assertEquals(9, state.totalWords) // first excerpt has 9 words
        assertTrue(state.wordStatuses.all { it == WordStatus.UNREAD })
    }

    @Test
    fun `selectMode changes mode when idle`() {
        vm.selectMode(RecognitionMode.GRAMMAR_CONSTRAINED)
        assertEquals(RecognitionMode.GRAMMAR_CONSTRAINED, vm.uiState.value.mode)
    }

    @Test
    fun `selectMode blocked while listening`() {
        vm.start()
        vm.selectMode(RecognitionMode.GRAMMAR_CONSTRAINED)
        assertEquals(RecognitionMode.FULL, vm.uiState.value.mode)
    }

    @Test
    fun `selectExcerpt changes excerpt and reinits aligner`() {
        vm.selectExcerpt(1) // Shevchenko
        assertEquals(1, vm.uiState.value.selectedExcerptIndex)
        assertEquals(27, vm.uiState.value.totalWords) // 13 + 14
    }

    @Test
    fun `selectExcerpt blocked while listening`() {
        vm.start()
        vm.selectExcerpt(1)
        assertEquals(0, vm.uiState.value.selectedExcerptIndex)
    }

    @Test
    fun `start transitions to LISTENING`() {
        vm.start()
        assertEquals(AppState.LISTENING, vm.uiState.value.appState)
        assertTrue(engine.started)
        assertEquals(RecognitionMode.FULL, engine.lastMode)
    }

    @Test
    fun `start in grammar mode passes grammar`() {
        vm.selectMode(RecognitionMode.GRAMMAR_CONSTRAINED)
        vm.start()
        assertNotNull(engine.lastGrammar)
        assertTrue(engine.lastGrammar!!.contains("[unk]"))
    }

    @Test
    fun `pause transitions to PAUSED`() {
        vm.start()
        vm.pause()
        assertEquals(AppState.PAUSED, vm.uiState.value.appState)
        assertTrue(engine.paused)
    }

    @Test
    fun `resume transitions back to LISTENING`() {
        vm.start()
        vm.pause()
        vm.resume()
        assertEquals(AppState.LISTENING, vm.uiState.value.appState)
        assertFalse(engine.paused)
    }

    @Test
    fun `stop transitions to STOPPED`() {
        vm.start()
        vm.stop()
        assertEquals(AppState.STOPPED, vm.uiState.value.appState)
    }

    @Test
    fun `partial result creates tentative highlighting`() {
        vm.start()
        engine.simulatePartial("україна є")
        val state = vm.uiState.value
        assertEquals(WordStatus.TENTATIVE, state.wordStatuses[0])
        assertEquals(WordStatus.TENTATIVE, state.wordStatuses[1])
        assertEquals(WordStatus.UNREAD, state.wordStatuses[2])
    }

    @Test
    fun `final result creates confirmed highlighting`() {
        vm.start()
        engine.simulateFinal("україна є")
        val state = vm.uiState.value
        assertEquals(WordStatus.CONFIRMED, state.wordStatuses[0])
        assertEquals(WordStatus.CONFIRMED, state.wordStatuses[1])
        assertEquals(2, state.wordsMatched)
    }

    @Test
    fun `sequential finals advance progressively`() {
        vm.start()
        engine.simulateFinal("україна")
        assertEquals(1, vm.uiState.value.wordsMatched)

        engine.simulateFinal("є суверенна")
        assertEquals(3, vm.uiState.value.wordsMatched)
    }

    @Test
    fun `raw log accumulates partials and finals`() {
        vm.start()
        engine.simulatePartial("україна")
        engine.simulateFinal("україна є")
        val log = vm.uiState.value.rawLog
        assertEquals(2, log.size)
        assertTrue(log[0].startsWith("P:"))
        assertTrue(log[1].startsWith("F:"))
    }

    @Test
    fun `error logged in raw log`() {
        vm.start()
        engine.simulateError("test error")
        assertTrue(vm.uiState.value.rawLog.any { it.contains("test error") })
    }

    @Test
    fun `mode and excerpt selectable after stop`() {
        vm.start()
        vm.stop()
        vm.selectMode(RecognitionMode.GRAMMAR_CONSTRAINED)
        assertEquals(RecognitionMode.GRAMMAR_CONSTRAINED, vm.uiState.value.mode)
        vm.selectExcerpt(2)
        assertEquals(2, vm.uiState.value.selectedExcerptIndex)
    }

    @Test
    fun `grammar updated at sentence boundary without restarting audio`() {
        vm.selectExcerpt(1) // Shevchenko: 2 sentences, 13+14 words
        vm.selectMode(RecognitionMode.GRAMMAR_CONSTRAINED)
        vm.start()

        val initialStartCount = engine.startCount

        // Recognize all words of first sentence + first word of second sentence
        // This crosses the sentence boundary and should trigger a grammar update
        engine.simulateFinal("як умру то поховайте мене на могилі серед степу широкого на вкраїні милій щоб")

        assertEquals(1, engine.grammarUpdateCount)
        assertEquals(initialStartCount, engine.startCount)
        assertEquals(0, engine.stopCount)
        assertTrue(engine.started)
        assertEquals(AppState.LISTENING, vm.uiState.value.appState)
        val grammar = engine.lastGrammar!!
        assertTrue(grammar.contains("ревучий"))
        assertFalse(grammar.contains("поховайте"))
        assertTrue(grammar.contains("[unk]"))
    }

    @Test
    fun `grammar not rebuilt in full mode`() {
        vm.selectExcerpt(1)
        vm.start()
        val startCount = engine.startCount

        engine.simulateFinal("як умру то поховайте мене на могилі серед степу широкого на вкраїні милій")

        assertEquals(startCount, engine.startCount)
        assertEquals(0, engine.grammarUpdateCount)
    }

    @Test
    fun `start after stop resets state`() {
        vm.start()
        engine.simulateFinal("україна є")
        assertEquals(2, vm.uiState.value.wordsMatched)

        vm.stop()
        vm.start()
        assertEquals(0, vm.uiState.value.wordsMatched)
        assertTrue(vm.uiState.value.rawLog.isEmpty())
        assertTrue(vm.uiState.value.wordStatuses.all { it == WordStatus.UNREAD })
    }

    @Test
    fun `extractVoskText parses partial json`() {
        assertEquals("hello world", SttViewModel.extractVoskText("""{"partial":"hello world"}""", "partial"))
    }

    @Test
    fun `extractVoskText parses final json`() {
        assertEquals("hello", SttViewModel.extractVoskText("""{"text":"hello"}""", "text"))
    }

    @Test
    fun `extractVoskText returns empty for missing key`() {
        assertEquals("", SttViewModel.extractVoskText("""{"partial":"hello"}""", "text"))
    }

    @Test
    fun `extractVoskText handles multiline vosk result with escaped quotes`() {
        val json = """
            {
              "result" : [{"conf" : 1.0, "end" : 1.2, "start" : 0.6, "word" : "a"}],
              "text" : "a \"b\""
            }
        """.trimIndent()
        assertEquals("a \"b\"", SttViewModel.extractVoskText(json, "text"))
    }

    @Test
    fun `extractVoskText returns empty for malformed json`() {
        assertEquals("", SttViewModel.extractVoskText("""{"text":""", "text"))
    }
}
