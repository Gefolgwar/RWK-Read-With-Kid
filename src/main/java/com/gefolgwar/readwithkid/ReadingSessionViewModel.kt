package com.gefolgwar.readwithkid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.ColumnInfo
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
import java.text.Normalizer
import kotlin.math.roundToInt

data class ReadingResult(
    val totalWords: Int,
    val correctWords: Int,
    /** Reading time without pauses. */
    val durationMs: Long,
    val skippedWords: List<String>,
    /** Words read correctly after one or more wrong attempts. */
    val retriedWords: List<String>,
    /** Words the child tapped to hear them said. */
    @ColumnInfo(defaultValue = "") val helpedWords: List<String> = emptyList()
) {
    /** Share of correctly read words, 0..1. */
    val accuracy: Float
        get() = if (totalWords == 0) 0f else correctWords.toFloat() / totalWords

    /** Words correct per minute (WCPM). */
    val wordsCorrectPerMinute: Int
        get() = if (durationMs <= 0) 0 else (correctWords * 60_000.0 / durationMs).roundToInt()

    /** The child's reward, 1..[MAX_STARS]: every reading earns at least one star. */
    val stars: Int
        get() = when {
            accuracy >= 0.9f -> 3
            accuracy >= 0.7f -> 2
            else -> 1
        }

    companion object {
        const val MAX_STARS = 3
    }
}

/** A read or misread word the child tapped: what the speech model [heard] in its place, "" for nothing. */
data class WordCheck(
    val index: Int,
    val heard: String,
    /** The child is reading the misread word again. */
    val retrying: Boolean = false
)

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
    val recognitionError: Boolean = false,
    /** Whether the child can tap a word to hear it: the phone has a voice for the language. */
    val helpAvailable: Boolean = false,
    /** The word tapped during reading, or null. */
    val wordCheck: WordCheck? = null
)

/**
 * Each text language has its own engine, created by [createEngine], and its own model, see [modelInstalled].
 * A missing model is downloaded by [startModelDownload] as soon as a text in its language is opened.
 * A finished reading of a saved text is saved to [readings], dated by [wallClockMs].
 * Words the child taps are said by [pronunciation]; without it there is no help.
 */
class ReadingSessionViewModel(
    private val createEngine: (TextLanguage) -> VoskEngine,
    private val mode: RecognitionMode = RecognitionMode.FULL,
    private val modelInstalled: (TextLanguage) -> Flow<Boolean> = { flowOf(true) },
    private val startModelDownload: (TextLanguage) -> Unit = {},
    private val readings: ReadingRepository? = null,
    private val pronunciation: PronunciationHelper? = null,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadingUiState())
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    /** The saved text being read; null for a text that is not saved, and then nothing is saved. */
    private var textReading: TextReading? = null
    private var aligner = WordAligner(emptyList())
    private var currentSentenceIdx = 0
    private var activeSinceMs = 0L
    private var elapsedMs = 0L
    /** Indices of the words said for the child during this reading. */
    private val helpedIndices = mutableSetOf<Int>()
    /** Words asked to be said and not finished yet; the microphone is deaf meanwhile. */
    private var wordsBeingSaid = 0
    /** The misread word being read again, or null; meanwhile what the microphone hears is taken for it. */
    private var retryIndex: Int? = null

    private val listener = object : VoskEngineListener {
        override fun onPartialResult(text: String) = handlePartial(text)
        override fun onResult(text: String) = handleFinal(text)
        override fun onError(message: String) = handleError()
        override fun onModelLoaded() = handleModelLoaded()
    }
    private lateinit var engine: VoskEngine
    private var modelJob: Job? = null
    private var voiceJob: Job? = null

    init {
        useLanguage(_uiState.value.language)
    }

    override fun onCleared() {
        engine.release()
        pronunciation?.release()
    }

    fun updateText(raw: String, language: TextLanguage = TextLanguage.UK, reading: TextReading? = null) {
        if (isActive()) return
        textReading = reading
        val languageChanged = language != _uiState.value.language
        _uiState.update {
            it.copy(
                rawText = raw,
                text = TextPreparation.prepare(raw),
                language = language,
                recognitionError = false
            )
        }
        if (languageChanged) {
            engine.release()
            useLanguage(language)
        } else {
            downloadModel()
        }
    }

    /** Downloads the missing model of the open text's language; also the retry after a failed download. */
    fun downloadModel() {
        val state = _uiState.value
        if (state.sessionState == SessionState.MODEL_MISSING && state.rawText.isNotEmpty()) startModelDownload(state.language)
    }

    /** Creates the engine for [language] and loads its model once the model is installed. */
    private fun useLanguage(language: TextLanguage) {
        modelJob?.cancel()
        engine = createEngine(language).apply { setListener(listener) }
        _uiState.update { it.copy(sessionState = SessionState.LOADING_MODEL, result = null) }
        modelJob = viewModelScope.launch {
            modelInstalled(language).collect { installed -> if (installed) loadModel() else handleModelMissing() }
        }
        voiceJob?.cancel()
        _uiState.update { it.copy(helpAvailable = false) }
        val helper = pronunciation ?: return
        voiceJob = viewModelScope.launch {
            helper.voiceAvailable(language).collect { available -> _uiState.update { it.copy(helpAvailable = available) } }
        }
    }

    fun start() {
        val state = _uiState.value
        if (state.sessionState != SessionState.READY || state.text.words.isEmpty()) return
        val words = state.text.words
        // Words the model cannot recognise are not the child's errors
        val unknown = words.indices.filterTo(mutableSetOf()) { !engine.knowsWord(vocabularySpelling(words[it])) }
        aligner = WordAligner(words.map { it.normalized }, unknown)
        elapsedMs = 0L
        helpedIndices.clear()
        retryIndex = null
        _uiState.update { it.copy(sessionState = SessionState.READING, result = null, recognitionError = false, wordCheck = null) }
        publishProgress()
        startEngine()
    }

    /** Stops the microphone; also used when the app goes to the background. */
    fun pause() {
        if (_uiState.value.sessionState != SessionState.READING) return
        stopRetry()
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
        stopRetry()
        if (state == SessionState.READING) stopEngine()
        aligner.commitTentative()
        publishProgress()
        val words = _uiState.value.text.words
        fun displayOf(indices: Set<Int>) = indices.sorted().map { bare(words[it]) }
        val result = ReadingResult(
            totalWords = aligner.knownCount,
            correctWords = aligner.matchedCount,
            durationMs = elapsedMs,
            skippedWords = displayOf(aligner.skippedIndices),
            retriedWords = displayOf(aligner.retriedIndices),
            helpedWords = displayOf(helpedIndices)
        )
        _uiState.update { it.copy(sessionState = SessionState.FINISHED, result = result) }
        val reading = textReading ?: return
        val record = ReadingRecord(
            textId = reading.textId,
            textTitle = reading.textTitle,
            readAt = wallClockMs(),
            result = result
        )
        viewModelScope.launch { readings?.saveReading(record) }
    }

    /** Ends the reading without a result: nothing is saved, e.g. for a reading started by mistake. */
    fun leave() {
        val state = _uiState.value.sessionState
        if (state != SessionState.READING && state != SessionState.PAUSED) return
        stopRetry()
        if (state == SessionState.READING) stopEngine()
        _uiState.update { it.copy(sessionState = SessionState.READY, wordCheck = null) }
    }

    /** Says the word at [index] for the child during reading; it is listed in the result. */
    fun sayWord(index: Int) {
        val state = _uiState.value
        val helper = pronunciation
        if (!isActive() || !state.helpAvailable || helper == null) return
        val word = state.text.words.getOrNull(index) ?: return
        helpedIndices.add(index)
        // The microphone must not take the spoken word for the child's reading.
        if (wordsBeingSaid++ == 0) engine.pause(true)
        helper.speak(bare(word), state.language) {
            if (--wordsBeingSaid == 0) engine.pause(false)
        }
    }

    /** Shows what the model heard in place of the word at [index] during reading; nothing for a word not read yet. */
    fun checkWord(index: Int) {
        if (!isActive()) return
        if (retryIndex != null) endRetry()
        val heard = aligner.heard(index)
        _uiState.update { it.copy(wordCheck = heard?.let { WordCheck(index, it) }) }
    }

    fun closeWordCheck() {
        if (retryIndex != null) endRetry()
        _uiState.update { it.copy(wordCheck = null) }
    }

    /**
     * The child reads the tapped misread word again: what the microphone hears next is taken for that word
     * until it is read correctly or the speech ends. Then reading the text goes on from where it was.
     */
    fun retryWord() {
        val check = _uiState.value.wordCheck ?: return
        if (!isActive() || check.retrying || aligner.getWordStatus(check.index) != WordStatus.SKIPPED) return
        // The microphone starts afresh, so speech heard before is not taken for the word.
        if (_uiState.value.sessionState == SessionState.READING) stopEngine()
        aligner.commitTentative()
        publishProgress()
        retryIndex = check.index
        _uiState.update { it.copy(sessionState = SessionState.READING, wordCheck = check.copy(retrying = true)) }
        startEngine()
    }

    fun closeResult() {
        if (_uiState.value.sessionState != SessionState.FINISHED) return
        _uiState.update { it.copy(sessionState = SessionState.READY, result = null) }
    }

    /** The word being read again has been heard: it is green when read correctly, else shows what was heard. */
    private fun retryHeard() {
        publishProgress()
        _uiState.update { s -> s.copy(wordCheck = s.wordCheck?.let { it.copy(heard = aligner.heard(it.index).orEmpty()) }) }
        endRetry()
    }

    /** Stops reading the word again; the microphone starts afresh, so the rest of that speech is not taken for the text. */
    private fun endRetry() {
        stopRetry()
        if (_uiState.value.sessionState == SessionState.READING) {
            stopEngine()
            startEngine()
        }
    }

    private fun stopRetry() {
        retryIndex = null
        _uiState.update { s -> s.copy(wordCheck = s.wordCheck?.copy(retrying = false)) }
    }

    private fun isActive(): Boolean {
        val state = _uiState.value.sessionState
        return state == SessionState.READING || state == SessionState.PAUSED
    }

    private fun startEngine() {
        activeSinceMs = clockMs()
        if (mode == RecognitionMode.GRAMMAR_CONSTRAINED) {
            currentSentenceIdx = sentenceOfConfirmedWord()
            val retrying = retryIndex?.let { spelling(_uiState.value.text.words[it]) }
            engine.start(mode, buildGrammar(currentSentenceIdx) + listOfNotNull(retrying))
        } else {
            engine.start(mode)
        }
        // Resumed while a word is still being said
        if (wordsBeingSaid > 0) engine.pause(true)
    }

    /** The word without surrounding punctuation, e.g. "раму." -> "раму". */
    private fun bare(word: Word) = word.display.trim { !it.isLetterOrDigit() }

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
        downloadModel()
    }

    private fun handleModelLoaded() {
        if (_uiState.value.sessionState != SessionState.LOADING_MODEL) return
        _uiState.update { it.copy(sessionState = SessionState.READY) }
    }

    private fun handlePartial(json: String) {
        if (_uiState.value.sessionState != SessionState.READING) return
        val text = extractVoskText(json, "partial")
        if (text.isBlank()) return
        val words = text.split(" ").filter { it.isNotBlank() }
        val retrying = retryIndex
        if (retrying != null) {
            if (aligner.retry(retrying, words, final = false)) retryHeard()
            return
        }
        aligner.processPartial(words)
        publishProgress()
    }

    private fun handleFinal(json: String) {
        if (_uiState.value.sessionState != SessionState.READING) return
        val text = extractVoskText(json, "text")
        if (text.isBlank()) return
        val words = text.split(" ").filter { it.isNotBlank() }
        val retrying = retryIndex
        if (retrying != null) {
            aligner.retry(retrying, words, final = true)
            retryHeard()
            return
        }
        aligner.processFinal(words)
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
        sentences.getOrNull(sentenceIndex)?.forEach { words.add(spelling(it)) }
        sentences.getOrNull(sentenceIndex + 1)?.forEach { words.add(spelling(it)) }
        return words.toList() + "[unk]"
    }

    // As written, not normalized: the recognizer only knows real spellings such as "schöne" or "будь-який".
    private fun spelling(word: Word) = word.display.lowercase().trim { !it.isLetterOrDigit() }

    /** The word as the model's vocabulary spells it: also without stress marks and with the apostrophe "'" ("п’ять" -> "п'ять"). */
    private fun vocabularySpelling(word: Word) =
        Normalizer.normalize(spelling(word), Normalizer.Form.NFC).replace(STRESS_MARKS, "").replace(APOSTROPHES, "'")

    companion object {
        // Combining marks left after NFC, e.g. the stress mark U+0301
        private val STRESS_MARKS = Regex("\\p{Mn}")
        private val APOSTROPHES = Regex("[ʼ’‘`]")

        fun factory(
            modelInstalled: (TextLanguage) -> Flow<Boolean> = { flowOf(true) },
            startModelDownload: (TextLanguage) -> Unit = {},
            createReadings: () -> ReadingRepository? = { null },
            createPronunciation: () -> PronunciationHelper? = { null },
            createEngine: (TextLanguage) -> VoskEngine
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReadingSessionViewModel(
                    createEngine,
                    modelInstalled = modelInstalled,
                    startModelDownload = startModelDownload,
                    readings = createReadings(),
                    pronunciation = createPronunciation()
                )
            }
        }

        fun extractVoskText(json: String, key: String): String = try {
            JSONObject(json).optString(key, "")
        } catch (e: JSONException) {
            ""
        }
    }
}
