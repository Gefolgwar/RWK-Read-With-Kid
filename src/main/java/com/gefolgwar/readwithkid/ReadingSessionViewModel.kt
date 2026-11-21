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

/** How the text was read the first time; reading words again after the text does not change it, except as noted. */
data class ReadingResult(
    /** Words that count: without words the model cannot recognise or could not check ([uncertainWords]). */
    val totalWords: Int,
    /** Words read correctly, also uncertain words the child then read correctly after the text. */
    val correctWords: Int,
    /** Reading time without pauses, and without reading words again after the text. */
    val durationMs: Long,
    /** Words misread or skipped and not corrected after the text. */
    val skippedWords: List<String>,
    /** Words read correctly after one or more wrong attempts. */
    val retriedWords: List<String>,
    /** Words the child tapped to hear them said. */
    @ColumnInfo(defaultValue = "") val helpedWords: List<String> = emptyList(),
    /** Words misread or skipped that the child read correctly after the text; they still count as errors. */
    @ColumnInfo(defaultValue = "") val correctedWords: List<String> = emptyList(),
    /** Words the model was not sure of, also after the text: neither read nor errors. */
    @ColumnInfo(defaultValue = "") val uncertainWords: List<String> = emptyList()
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

/** After the text, the child reads the misread, skipped and uncertain words again, one at a time. */
data class Review(
    /** Indices of the words to read again, in text order. */
    val words: List<Int>,
    /** Position in [words] of the word being read now. */
    val position: Int = 0,
    /** Positions in [words] of the words read correctly. */
    val read: Set<Int> = emptySet(),
    /** Wrong attempts at the word being read now. */
    val wrongAttempts: Int = 0,
    /** The microphone is off, e.g. while the app is in the background. */
    val paused: Boolean = false
) {
    /** Index in the text of the word being read now. */
    val wordIndex: Int
        get() = words[position]
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
    val recognitionError: Boolean = false,
    /** Whether the child can tap a word to hear it: the phone has a voice for the language. */
    val helpAvailable: Boolean = false,
    /** Reading words again after the text, while [SessionState.REVIEWING]. */
    val review: Review? = null
)

/**
 * Each text language has its own engine, created by [createEngine], and its own model, see [modelInstalled].
 * A missing model is downloaded by [startModelDownload] as soon as a text in its language is opened.
 * A finished reading of a saved text is saved to [readings], dated by [wallClockMs].
 * Words the child taps are said by [pronunciation]; without it there is no help.
 */
class ReadingSessionViewModel(
    private val createEngine: (TextLanguage) -> VoskEngine,
    mode: RecognitionMode = RecognitionMode.FULL,
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
    /** How speech is recognised from the next reading on, see [setRecognitionMode]. */
    private var chosenMode = mode
    /** How speech is recognised in this reading. */
    private var mode = mode
    private var aligner = WordAligner(emptyList())
    private var currentSentenceIdx = 0
    private var activeSinceMs = 0L
    private var elapsedMs = 0L
    /** Indices of the words said for the child during this reading. */
    private val helpedIndices = mutableSetOf<Int>()
    /** Words asked to be said and not finished yet; the microphone is deaf meanwhile. */
    private var wordsBeingSaid = 0

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

    /**
     * [RecognitionMode.GRAMMAR_CONSTRAINED] recognises only the words of the text around the child's place, so fewer
     * words read correctly are taken for mistakes; [RecognitionMode.FULL] recognises any word. Applies from the next reading.
     */
    fun setRecognitionMode(mode: RecognitionMode) {
        chosenMode = mode
    }

    fun start() {
        val state = _uiState.value
        if (state.sessionState != SessionState.READY || state.text.words.isEmpty()) return
        mode = chosenMode
        val words = state.text.words
        // Words the model cannot recognise are not the child's errors
        val unknown = words.indices.filterTo(mutableSetOf()) { !engine.knowsWord(vocabularySpelling(words[it])) }
        aligner = WordAligner(words.map { it.normalized }, unknown)
        elapsedMs = 0L
        helpedIndices.clear()
        _uiState.update { it.copy(sessionState = SessionState.READING, result = null, recognitionError = false, review = null) }
        publishProgress()
        startEngine()
    }

    /** Stops the microphone; also used when the app goes to the background. */
    fun pause() {
        when (_uiState.value.sessionState) {
            SessionState.READING -> {
                stopEngine()
                aligner.commitTentative()
                publishProgress()
                _uiState.update { it.copy(sessionState = SessionState.PAUSED) }
            }
            SessionState.REVIEWING -> {
                val review = _uiState.value.review ?: return
                if (review.paused) return
                engine.stop()
                _uiState.update { it.copy(review = review.copy(paused = true)) }
            }
            else -> Unit
        }
    }

    fun resume() {
        when (_uiState.value.sessionState) {
            SessionState.PAUSED -> {
                _uiState.update { it.copy(sessionState = SessionState.READING) }
                startEngine()
            }
            SessionState.REVIEWING -> {
                val review = _uiState.value.review ?: return
                if (!review.paused) return
                _uiState.update { it.copy(review = review.copy(paused = false)) }
                startReviewWord()
            }
            else -> Unit
        }
    }

    /**
     * The child has read to the end, or stops early. Misread, skipped and uncertain words are then read again one at a
     * time, see [Review]; without any, the result is shown at once.
     */
    fun finish() {
        val state = _uiState.value.sessionState
        if (state != SessionState.READING && state != SessionState.PAUSED) return
        if (state == SessionState.READING) stopEngine()
        aligner.commitTentative()
        publishProgress()
        val words = aligner.reviewIndices
        if (words.isEmpty()) {
            complete()
        } else {
            _uiState.update { it.copy(sessionState = SessionState.REVIEWING, review = Review(words)) }
            startReviewWord()
        }
    }

    /** Goes on to the next word to read again, leaving the current one as it is; the result after the last one. */
    fun nextReviewWord() {
        if (_uiState.value.sessionState != SessionState.REVIEWING) return
        val review = _uiState.value.review ?: return
        if (!review.paused) engine.stop()
        val next = review.position + 1
        if (next == review.words.size) {
            complete()
            return
        }
        _uiState.update { it.copy(review = review.copy(position = next, wrongAttempts = 0, paused = false)) }
        startReviewWord()
    }

    /** Stops reading words again, leaving the rest as they are, and shows the result. */
    fun finishReview() {
        if (_uiState.value.sessionState != SessionState.REVIEWING) return
        if (_uiState.value.review?.paused == false) engine.stop()
        complete()
    }

    /** Shows the result of the reading and saves it for a saved text. */
    private fun complete() {
        val words = _uiState.value.text.words
        fun displayOf(indices: Collection<Int>) = indices.sorted().map { bare(words[it]) }
        val uncertain = aligner.uncertainIndices
        val result = ReadingResult(
            totalWords = aligner.knownCount - uncertain.size,
            correctWords = aligner.matchedCount,
            durationMs = elapsedMs,
            skippedWords = displayOf(aligner.skippedIndices),
            retriedWords = displayOf(aligner.retriedIndices),
            helpedWords = displayOf(helpedIndices),
            correctedWords = displayOf(aligner.correctedIndices),
            uncertainWords = displayOf(uncertain)
        )
        _uiState.update { it.copy(sessionState = SessionState.FINISHED, result = result, review = null) }
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
        if (state == SessionState.READING) stopEngine()
        _uiState.update { it.copy(sessionState = SessionState.READY) }
    }

    /** Says the word at [index] for the child during reading, also while reading words again; it is listed in the result. */
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

    fun closeResult() {
        if (_uiState.value.sessionState != SessionState.FINISHED) return
        _uiState.update { it.copy(sessionState = SessionState.READY, result = null) }
    }

    private fun isActive(): Boolean {
        val state = _uiState.value.sessionState
        return state == SessionState.READING || state == SessionState.PAUSED || state == SessionState.REVIEWING
    }

    private fun startEngine() {
        activeSinceMs = clockMs()
        if (mode == RecognitionMode.GRAMMAR_CONSTRAINED) {
            currentSentenceIdx = sentenceOfConfirmedWord()
            engine.start(mode, buildGrammar(currentSentenceIdx))
        } else {
            engine.start(mode)
        }
        // Resumed while a word is still being said
        if (wordsBeingSaid > 0) engine.pause(true)
    }

    /**
     * Listens afresh for the word being read again, so earlier speech is not taken for it. Only that word is
     * recognised, so the model checks it alone; any other speech is outside the grammar, a wrong attempt.
     */
    private fun startReviewWord() {
        val review = _uiState.value.review ?: return
        val word = _uiState.value.text.words[review.wordIndex]
        engine.start(RecognitionMode.GRAMMAR_CONSTRAINED, listOf(spelling(word), "[unk]"))
        if (wordsBeingSaid > 0) engine.pause(true)
    }

    /** The word being read again was heard: read, the next word follows; after [REVIEW_ATTEMPTS] wrong attempts too. */
    private fun reviewHeard(words: List<String>, final: Boolean) {
        val review = _uiState.value.review?.takeIf { !it.paused } ?: return
        when (aligner.review(review.wordIndex, words, final)) {
            ReviewOutcome.READ -> {
                _uiState.update { it.copy(review = review.copy(read = review.read + review.position)) }
                nextReviewWord()
            }
            ReviewOutcome.WRONG ->
                if (review.wrongAttempts + 1 >= REVIEW_ATTEMPTS) nextReviewWord()
                else _uiState.update { it.copy(review = review.copy(wrongAttempts = review.wrongAttempts + 1)) }
            ReviewOutcome.LISTENING -> Unit
        }
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
        val state = _uiState.value.sessionState
        if (state != SessionState.READING && state != SessionState.REVIEWING) return
        val text = extractVoskText(json, "partial")
        if (text.isBlank()) return
        val words = text.split(" ").filter { it.isNotBlank() }
        if (state == SessionState.REVIEWING) {
            reviewHeard(words, final = false)
            return
        }
        aligner.processPartial(words)
        publishProgress()
        checkSentenceBoundary()
    }

    private fun handleFinal(json: String) {
        val state = _uiState.value.sessionState
        if (state != SessionState.READING && state != SessionState.REVIEWING) return
        val (words, unsure) = finalWords(json)
        if (words.isEmpty()) return
        if (state == SessionState.REVIEWING) {
            reviewHeard(words, final = true)
            return
        }
        aligner.processFinal(words, unsure)
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
            // The text is read: its result is shown, without the words not read again yet
            SessionState.REVIEWING -> finishReview()
            else -> Unit
        }
    }

    private fun publishProgress() {
        val count = _uiState.value.text.words.size
        val statuses = List(count) { aligner.getWordStatus(it) }
        val next = maxOf(aligner.confirmedIndex, aligner.tentativeIndex) + 1
        _uiState.update { it.copy(wordStatuses = statuses, currentWordIndex = if (next < count) next else -1) }
    }

    /**
     * Moves the grammar on once the child reads into the next sentence, also in a partial result, so reading on
     * without a pause does not run out of the grammar. Only forward: a revised partial result does not move it back.
     */
    private fun checkSentenceBoundary() {
        if (mode != RecognitionMode.GRAMMAR_CONSTRAINED) return
        val sentence = sentenceOf(maxOf(aligner.confirmedIndex, aligner.tentativeIndex))
        if (sentence > currentSentenceIdx) {
            currentSentenceIdx = sentence
            engine.updateGrammar(buildGrammar(sentence))
        }
    }

    private fun sentenceOfConfirmedWord(): Int = sentenceOf(aligner.confirmedIndex)

    /** The sentence of the word at [index]; the first one before any word is read. */
    private fun sentenceOf(index: Int): Int {
        val sentences = _uiState.value.text.sentences
        var wordCount = 0
        for ((i, sentence) in sentences.withIndex()) {
            wordCount += sentence.size
            if (index < wordCount) return i
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

        /** Below this confidence the model is not sure of a recognised word. A first guess, to be tuned on children's reading. */
        const val UNSURE_CONFIDENCE = 0.7

        /** Wrong attempts at a word read again after the text before the next word follows. */
        const val REVIEW_ATTEMPTS = 2

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

        /**
         * The words of a final result, and the indices of those the model was not sure of: below [UNSURE_CONFIDENCE].
         * Without the words' confidence (no "result" list) the model counts as sure of every word.
         */
        fun finalWords(json: String): Pair<List<String>, Set<Int>> = try {
            val result = JSONObject(json).optJSONArray("result")
            if (result == null) {
                extractVoskText(json, "text").split(" ").filter { it.isNotBlank() } to emptySet()
            } else {
                val words = (0 until result.length()).map { result.getJSONObject(it) }
                words.map { it.optString("word") } to
                    words.indices.filterTo(mutableSetOf()) { words[it].optDouble("conf", 1.0) < UNSURE_CONFIDENCE }
            }
        } catch (e: JSONException) {
            emptyList<String>() to emptySet()
        }
    }
}
