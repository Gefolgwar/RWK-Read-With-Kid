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
import kotlin.math.roundToInt

data class ReadingResult(
    val totalWords: Int,
    val correctWords: Int,
    /** Reading time without pauses. */
    val durationMs: Long
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
    val wordStatuses: List<WordStatus> = emptyList(),
    /** Word the child is expected to read next, or -1. */
    val currentWordIndex: Int = -1,
    val result: ReadingResult? = null,
    val recognitionError: Boolean = false
)

class ReadingSessionViewModel(
    private val engine: VoskEngine,
    private val mode: RecognitionMode = RecognitionMode.FULL,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    private var aligner = WordAligner(emptyList())
    private var currentSentenceIdx = 0
    private var activeSinceMs = 0L
    private var elapsedMs = 0L

    init {
        engine.setListener(object : VoskEngineListener {
            override fun onPartialResult(text: String) = handlePartial(text)
            override fun onResult(text: String) = handleFinal(text)
            override fun onError(message: String) = handleError()
            override fun onModelLoaded() = handleModelLoaded()
        })
        engine.loadModel()
    }

    override fun onCleared() {
        engine.release()
    }

    fun updateText(raw: String) {
        if (isActive()) return
        _uiState.update { it.copy(rawText = raw, text = TextPreparation.prepare(raw), recognitionError = false) }
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
        val result = ReadingResult(
            totalWords = _uiState.value.text.words.size,
            correctWords = aligner.matchedCount,
            durationMs = elapsedMs
        )
        _uiState.update { it.copy(sessionState = SessionState.FINISHED, result = result) }
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
        val words = mutableSetOf<String>()
        sentences.getOrNull(sentenceIndex)?.forEach { words.add(it.normalized) }
        sentences.getOrNull(sentenceIndex + 1)?.forEach { words.add(it.normalized) }
        return words.toList() + "[unk]"
    }

    companion object {
        fun factory(createEngine: () -> VoskEngine): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReadingSessionViewModel(createEngine()) }
        }

        fun extractVoskText(json: String, key: String): String = try {
            JSONObject(json).optString(key, "")
        } catch (e: JSONException) {
            ""
        }
    }
}
