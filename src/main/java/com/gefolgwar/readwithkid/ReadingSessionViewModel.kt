package com.gefolgwar.readwithkid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

data class ReadingResult(
    val totalWords: Int,
    val correctWords: Int,
    /** Reading time without pauses. */
    val durationMs: Long,
    val skippedWords: List<String>,
    /** Words read correctly after one or more wrong attempts. */
    val retriedWords: List<String>
) {
    /** Share of correctly read words, 0..1. */
    val accuracy: Float
        get() = if (totalWords == 0) 0f else correctWords.toFloat() / totalWords

    /** Words correct per minute (WCPM). */
    val wordsCorrectPerMinute: Int
        get() = if (durationMs <= 0) 0 else (correctWords * 60_000.0 / durationMs).roundToInt()
}

data class ReadingUiState(
    val sessionState: SessionState = SessionState.LOADING_MODEL,
    val rawText: String = "",
    val text: PreparedText = PreparedText(emptyList()),
    /** Language of the text; the session uses its speech model. */
    val language: TextLanguage = TextLanguage.UK,
    val wordStatuses: List<WordStatus> = emptyList(),
    /** Word the child is expected to read next, or -1. */
    val currentWordIndex: Int = -1,
    val result: ReadingResult? = null,
    val recognitionError: Boolean = false
)

/**
 * Each text language has its own engine, created by [createEngine], and its own model, see [modelInstalled].
 * A finished reading of a child's text is saved to [readings], dated by [wallClockMs].
 */
class ReadingSessionViewModel(
    private val createEngine: (TextLanguage) -> VoskEngine,
    private val mode: RecognitionMode = RecognitionMode.FULL,
    private val modelInstalled: (TextLanguage) -> Flow<Boolean> = { flowOf(true) },
    private val readings: ReadingRepository? = null,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    /** Whose reading this is; null when a parent reads from the library, and then nothing is saved. */
    private var childReading: ChildReading? = null
    private var aligner = WordAligner(emptyList())
    private var currentSentenceIdx = 0
    private var activeSinceMs = 0L
    private var elapsedMs = 0L

    private val listener = object : VoskEngineListener {
        override fun onPartialResult(text: String) = handlePartial(text)
        override fun onResult(text: String) = handleFinal(text)
        override fun onError(message: String) = handleError()
        override fun onModelLoaded() = handleModelLoaded()
    }
    private lateinit var engine: VoskEngine
    private var modelJob: Job? = null

    init {
        useLanguage(_uiState.value.language)
    }

    override fun onCleared() {
        engine.release()
    }

    fun updateText(raw: String, language: TextLanguage = TextLanguage.UK, reading: ChildReading? = null) {
        if (isActive()) return
        childReading = reading
        val languageChanged = language != _uiState.value.language
        _uiState.update {
            it.copy(rawText = raw, text = TextPreparation.prepare(raw), language = language, recognitionError = false)
        }
        if (languageChanged) {
            engine.release()
            useLanguage(language)
        }
    }

    /** Creates the engine for [language] and loads its model once the model is installed. */
    private fun useLanguage(language: TextLanguage) {
        modelJob?.cancel()
        engine = createEngine(language).apply { setListener(listener) }
        _uiState.update { it.copy(sessionState = SessionState.LOADING_MODEL, result = null) }
        modelJob = viewModelScope.launch {
            modelInstalled(language).collect { installed -> if (installed) loadModel() else handleModelMissing() }
        }
    }

    fun start() {
        val state = _uiState.value
        if (state.sessionState != SessionState.READY || state.text.words.isEmpty()) return
        aligner = WordAligner(state.text.words.map { it.normalized })
        elapsedMs = 0L
        _uiState.update { it.copy(sessionState = SessionState.READING, result = null, recognitionError = false) }
        publishProgress()
        startEngine()
    }

    /** Stops the microphone; also used when the app goes to the background. */
    fun pause() {
        if (_uiState.value.sessionState != SessionState.READING) return
        stopEngine()
        aligner.commitTentative()
        publishProgress()
        _uiState.update { it.copy(sessionState = SessionState.PAUSED) }
    }

    fun resume() {
        if (_uiState.value.sessionState != SessionState.PAUSED) return
        _uiState.update { it.copy(sessionState = SessionState.READING) }
        startEngine()
    }

    fun finish() {
        val state = _uiState.value.sessionState
        if (state != SessionState.READING && state != SessionState.PAUSED) return
        if (state == SessionState.READING) stopEngine()
        aligner.commitTentative()
        publishProgress()
        val words = _uiState.value.text.words
        // Without surrounding punctuation, e.g. "раму." -> "раму"
        fun displayOf(indices: Set<Int>) = indices.sorted().map { words[it].display.trim { c -> !c.isLetterOrDigit() } }
        val result = ReadingResult(
            totalWords = words.size,
            correctWords = aligner.matchedCount,
            durationMs = elapsedMs,
            skippedWords = displayOf(aligner.skippedIndices),
            retriedWords = displayOf(aligner.retriedIndices)
        )
        _uiState.update { it.copy(sessionState = SessionState.FINISHED, result = result) }
        val reading = childReading ?: return
        val record = ReadingRecord(
            childId = reading.childId,
            textId = reading.textId,
            textTitle = reading.textTitle,
            readAt = wallClockMs(),
            result = result
        )
        viewModelScope.launch { readings?.saveReading(record) }
    }

    fun closeResult() {
        if (_uiState.value.sessionState != SessionState.FINISHED) return
        _uiState.update { it.copy(sessionState = SessionState.READY, result = null) }
    }

    private fun isActive(): Boolean {
        val state = _uiState.value.sessionState
        return state == SessionState.READING || state == SessionState.PAUSED
    }

    private fun startEngine() {
        activeSinceMs = clockMs()
        if (mode == RecognitionMode.GRAMMAR_CONSTRAINED) {
            currentSentenceIdx = sentenceOfConfirmedWord()
            engine.start(mode, buildGrammar(currentSentenceIdx))
        } else {
            engine.start(mode)
        }
    }

    private fun stopEngine() {
        engine.stop()
        elapsedMs += clockMs() - activeSinceMs
    }

    private fun loadModel() {
        val state = _uiState.value.sessionState
        if (state != SessionState.LOADING_MODEL && state != SessionState.MODEL_MISSING) return
        _uiState.update { it.copy(sessionState = SessionState.LOADING_MODEL) }
        engine.loadModel()
    }

    private fun handleModelMissing() {
        if (isActive()) return
        _uiState.update { it.copy(sessionState = SessionState.MODEL_MISSING, result = null) }
    }

    private fun handleModelLoaded() {
        if (_uiState.value.sessionState != SessionState.LOADING_MODEL) return
        _uiState.update { it.copy(sessionState = SessionState.READY) }
    }

    private fun handlePartial(json: String) {
        if (_uiState.value.sessionState != SessionState.READING) return
        val text = extractVoskText(json, "partial")
        if (text.isBlank()) return
        aligner.processPartial(text.split(" ").filter { it.isNotBlank() })
        publishProgress()
    }

    private fun handleFinal(json: String) {
        if (_uiState.value.sessionState != SessionState.READING) return
        val text = extractVoskText(json, "text")
        if (text.isBlank()) return
        aligner.processFinal(text.split(" ").filter { it.isNotBlank() })
        publishProgress()
        if (aligner.confirmedIndex == _uiState.value.text.words.lastIndex) {
            finish()
        } else {
            checkSentenceBoundary()
        }
    }

    private fun handleError() {
        when (_uiState.value.sessionState) {
            SessionState.LOADING_MODEL ->
                _uiState.update { it.copy(sessionState = SessionState.MODEL_ERROR) }
            SessionState.READING, SessionState.PAUSED -> {
                engine.stop()
                _uiState.update { it.copy(sessionState = SessionState.READY, recognitionError = true) }
            }
            else -> Unit
        }
    }

    private fun publishProgress() {
        val count = _uiState.value.text.words.size
        val statuses = List(count) { aligner.getWordStatus(it) }
        val next = maxOf(aligner.confirmedIndex, aligner.tentativeIndex) + 1
        _uiState.update {
            it.copy(wordStatuses = statuses, currentWordIndex = if (next < count) next else -1)
        }
    }

    private fun checkSentenceBoundary() {
        if (mode != RecognitionMode.GRAMMAR_CONSTRAINED) return
        val sentence = sentenceOfConfirmedWord()
        if (sentence != currentSentenceIdx) {
            currentSentenceIdx = sentence
            engine.updateGrammar(buildGrammar(sentence))
        }
    }

    private fun sentenceOfConfirmedWord(): Int {
        val sentences = _uiState.value.text.sentences
        var wordCount = 0
        for ((i, sentence) in sentences.withIndex()) {
            wordCount += sentence.size
            if (aligner.confirmedIndex < wordCount) return i
        }
        return sentences.lastIndex
    }

    private fun buildGrammar(sentenceIndex: Int): List<String> {
        val sentences = _uiState.value.text.sentences
        // As written, not normalized: the recognizer only knows real spellings such as "schöne" or "будь-який".
        fun spelling(word: Word) = word.display.lowercase().trim { !it.isLetterOrDigit() }
        val words = mutableSetOf<String>()
        sentences.getOrNull(sentenceIndex)?.forEach { words.add(spelling(it)) }
        sentences.getOrNull(sentenceIndex + 1)?.forEach { words.add(spelling(it)) }
        return words.toList() + "[unk]"
    }

    companion object {
        fun factory(
            modelInstalled: (TextLanguage) -> Flow<Boolean> = { flowOf(true) },
            createReadings: () -> ReadingRepository? = { null },
            createEngine: (TextLanguage) -> VoskEngine
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReadingSessionViewModel(createEngine, modelInstalled = modelInstalled, readings = createReadings()) }
        }

        fun extractVoskText(json: String, key: String): String = try {
            JSONObject(json).optString(key, "")
        } catch (e: JSONException) {
            ""
        }
    }
}
