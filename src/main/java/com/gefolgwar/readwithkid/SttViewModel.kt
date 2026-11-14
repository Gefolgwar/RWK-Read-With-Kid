package com.gefolgwar.readwithkid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONException
import org.json.JSONObject

data class SttUiState(
    val appState: AppState = AppState.LOADING_MODEL,
    val mode: RecognitionMode = RecognitionMode.FULL,
    val selectedExcerptIndex: Int = 0,
    val wordStatuses: List<WordStatus> = emptyList(),
    val stats: PerformanceTracker.StatsSnapshot = PerformanceTracker.StatsSnapshot(),
    val rawLog: List<String> = emptyList(),
    val wordsMatched: Int = 0,
    val totalWords: Int = 0
)

class SttViewModel(
    private val engine: VoskEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(SttUiState())
    val uiState: StateFlow<SttUiState> = _uiState.asStateFlow()

    private var aligner: WordAligner? = null
    val tracker = PerformanceTracker()
    private var currentSentenceIdx = 0
    private var lastPartialTimestampMs = 0L

    init {
        engine.setListener(object : VoskEngineListener {
            override fun onPartialResult(text: String) = handlePartial(text)
            override fun onResult(text: String) = handleFinal(text)
            override fun onError(message: String) = handleError(message)
            override fun onModelLoaded() = handleModelLoaded()
        })
        initAligner()
        engine.loadModel()
    }

    override fun onCleared() {
        engine.release()
    }

    fun selectMode(mode: RecognitionMode) {
        val state = _uiState.value.appState
        if (state != AppState.IDLE && state != AppState.STOPPED) return
        _uiState.update { it.copy(mode = mode) }
    }

    fun selectExcerpt(index: Int) {
        val state = _uiState.value.appState
        if (state != AppState.IDLE && state != AppState.STOPPED) return
        if (index < 0 || index >= TextExcerpts.excerpts.size) return
        _uiState.update { it.copy(selectedExcerptIndex = index) }
        initAligner()
    }

    fun start() {
        val state = _uiState.value
        if (state.appState != AppState.IDLE && state.appState != AppState.STOPPED) return
        resetForNewRun()
        _uiState.update { it.copy(appState = AppState.LISTENING) }

        if (state.mode == RecognitionMode.GRAMMAR_CONSTRAINED) {
            engine.start(state.mode, buildGrammar(0))
        } else {
            engine.start(state.mode)
        }
    }

    fun pause() {
        if (_uiState.value.appState != AppState.LISTENING) return
        engine.pause(true)
        _uiState.update { it.copy(appState = AppState.PAUSED) }
    }

    fun resume() {
        if (_uiState.value.appState != AppState.PAUSED) return
        engine.pause(false)
        _uiState.update { it.copy(appState = AppState.LISTENING) }
    }

    fun stop() {
        val state = _uiState.value.appState
        if (state != AppState.LISTENING && state != AppState.PAUSED) return
        engine.stop()
        _uiState.update { it.copy(appState = AppState.STOPPED) }
    }

    fun generateReport(): String {
        val state = _uiState.value
        val excerpt = TextExcerpts.excerpts[state.selectedExcerptIndex]
        return tracker.generateReport(state.mode, excerpt.title)
    }

    private fun handleModelLoaded() {
        if (_uiState.value.appState != AppState.LOADING_MODEL) return
        _uiState.update { it.copy(appState = AppState.IDLE) }
    }

    private fun handlePartial(json: String) {
        val text = extractVoskText(json, "partial")
        if (text.isBlank()) return

        val words = text.split(" ").filter { it.isNotBlank() }
        val callbackEntryNs = System.nanoTime()
        val nowMs = System.currentTimeMillis()

        tracker.recordPartial(nowMs)

        aligner?.processPartial(words)

        val highlightNs = System.nanoTime()
        tracker.recordCallbackEntry(callbackEntryNs)
        tracker.recordHighlightEmit(highlightNs)

        updateWordStatuses()
        appendRawLog("P: $text")
    }

    private fun handleFinal(json: String) {
        val text = extractVoskText(json, "text")
        if (text.isBlank()) return

        val words = text.split(" ").filter { it.isNotBlank() }
        val callbackEntryNs = System.nanoTime()
        val nowMs = System.currentTimeMillis()

        tracker.recordFinalStart(lastPartialTimestampMs)
        tracker.recordFinalEnd(nowMs)
        lastPartialTimestampMs = nowMs

        val prevConfirmed = aligner?.confirmedIndex ?: -1
        aligner?.processFinal(words)
        val newConfirmed = aligner?.confirmedIndex ?: -1

        for (i in (prevConfirmed + 1)..newConfirmed) {
            tracker.recordHighlightUpdate(i, System.nanoTime())
        }

        val highlightNs = System.nanoTime()
        tracker.recordCallbackEntry(callbackEntryNs)
        tracker.recordHighlightEmit(highlightNs)

        updateWordStatuses()
        appendRawLog("F: $text")

        checkSentenceBoundary()
    }

    private fun handleError(message: String) {
        appendRawLog("E: $message")
    }

    private fun appendRawLog(line: String) {
        _uiState.update { it.copy(rawLog = (it.rawLog + line).takeLast(MAX_RAW_LOG_LINES)) }
    }

    private fun checkSentenceBoundary() {
        if (_uiState.value.mode != RecognitionMode.GRAMMAR_CONSTRAINED) return
        val excerpt = TextExcerpts.excerpts[_uiState.value.selectedExcerptIndex]
        val confirmed = aligner?.confirmedIndex ?: -1

        var wordCount = 0
        for ((i, sentence) in excerpt.sentences.withIndex()) {
            wordCount += sentence.size
            if (confirmed < wordCount) {
                if (i != currentSentenceIdx) {
                    currentSentenceIdx = i
                    engine.updateGrammar(buildGrammar(i))
                }
                return
            }
        }
    }

    private fun buildGrammar(sentenceIndex: Int): List<String> {
        val excerpt = TextExcerpts.excerpts[_uiState.value.selectedExcerptIndex]
        val words = mutableSetOf<String>()
        if (sentenceIndex < excerpt.sentences.size) {
            excerpt.sentences[sentenceIndex].forEach { words.add(WordAligner.normalize(it)) }
        }
        if (sentenceIndex + 1 < excerpt.sentences.size) {
            excerpt.sentences[sentenceIndex + 1].forEach { words.add(WordAligner.normalize(it)) }
        }
        return words.toList() + "[unk]"
    }

    private fun initAligner() {
        val excerpt = TextExcerpts.excerpts[_uiState.value.selectedExcerptIndex]
        aligner = WordAligner(excerpt.words)
        _uiState.update {
            it.copy(
                wordStatuses = List(excerpt.totalWords) { WordStatus.UNREAD },
                totalWords = excerpt.totalWords,
                wordsMatched = 0
            )
        }
    }

    private fun resetForNewRun() {
        aligner?.reset()
        tracker.reset()
        currentSentenceIdx = 0
        lastPartialTimestampMs = 0L
        _uiState.update {
            it.copy(
                wordStatuses = List(it.totalWords) { WordStatus.UNREAD },
                wordsMatched = 0,
                stats = PerformanceTracker.StatsSnapshot(),
                rawLog = emptyList()
            )
        }
    }

    private fun updateWordStatuses() {
        val a = aligner ?: return
        val excerpt = TextExcerpts.excerpts[_uiState.value.selectedExcerptIndex]
        val statuses = List(excerpt.totalWords) { i -> a.getWordStatus(i) }
        val matched = statuses.count { it == WordStatus.CONFIRMED }
        _uiState.update {
            it.copy(
                wordStatuses = statuses,
                wordsMatched = matched,
                stats = tracker.getStats()
            )
        }
    }

    companion object {
        const val MAX_RAW_LOG_LINES = 200

        fun factory(createEngine: () -> VoskEngine): ViewModelProvider.Factory = viewModelFactory {
            initializer { SttViewModel(createEngine()) }
        }

        fun extractVoskText(json: String, key: String): String = try {
            JSONObject(json).optString(key, "")
        } catch (e: JSONException) {
            ""
        }
    }
}
