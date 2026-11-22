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

/**
 * How the text was read: each sentence as the child read it the first time; reading a sentence again does not change
 * it, except as noted. Sentences not read count in none of it.
 */
data class ReadingResult(
    /** Words that count: without words the model cannot recognise or could not check ([uncertainWords]). */
    val totalWords: Int,
    /** Words read correctly. */
    val correctWords: Int,
    /** Reading time without pauses. */
    val durationMs: Long,
    /** Words misread or skipped and not read correctly when their sentence was read again. */
    val skippedWords: List<String>,
    /** Words read correctly after one or more wrong attempts. */
    val retriedWords: List<String>,
    /** Words the child tapped to hear them said. */
    @ColumnInfo(defaultValue = "") val helpedWords: List<String> = emptyList(),
    /** Words misread or skipped that the child read correctly when reading their sentence again; they still count as errors. */
    @ColumnInfo(defaultValue = "") val correctedWords: List<String> = emptyList(),
    /** Words the model was not sure of: neither read nor errors. */
    @ColumnInfo(defaultValue = "") val uncertainWords: List<String> = emptyList(),
    /** Sentences with misread or skipped words, in text order. */
    @ColumnInfo(defaultValue = "") val errorSentences: List<ReadSentence> = emptyList()
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

/** A sentence with misread or skipped words as the child read it the first time, for the result. */
data class ReadSentence(val words: List<ReadWord>, val tooManyErrors: Boolean)

/**
 * A word of a [ReadSentence] as written in the text: [WordStatus.READ], [WordStatus.MISREAD] with what the model
 * [heard] in its place ("" for nothing), or [WordStatus.UNCHECKED].
 */
data class ReadWord(val text: String, val status: WordStatus, val heard: String = "")

data class ReadingUiState(
    val sessionState: SessionState = SessionState.LOADING_MODEL,
    val rawText: String = "",
    val text: PreparedText = PreparedText(emptyList()),
    /** Language of the text; the session uses its speech model. */
    val language: TextLanguage = TextLanguage.UK,
    val wordStatuses: List<WordStatus> = emptyList(),
    /** What the model heard in place of the misread words of read sentences, by word. */
    val heard: Map<Int, String> = emptyMap(),
    /** How each sentence was read last. */
    val sentenceStatuses: List<SentenceStatus> = emptyList(),
    /** Word the child is expected to read next, or -1. */
    val currentWordIndex: Int = -1,
    /** The sentence of [currentWordIndex], or -1. */
    val currentSentence: Int = -1,
    val result: ReadingResult? = null,
    val recognitionError: Boolean = false,
    /** Whether the child can tap a word to hear it: the phone has a voice for the language. */
    val helpAvailable: Boolean = false
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
     * [RecognitionMode.GRAMMAR_CONSTRAINED] recognises only the words of the text, so fewer words read correctly are
     * taken for mistakes; [RecognitionMode.FULL] recognises any word. Applies from the next reading.
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
        aligner = WordAligner(words.map { it.normalized }, unknown, state.text.sentenceOf)
        elapsedMs = 0L
        helpedIndices.clear()
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

    /**
     * The child stops reading, or has read every sentence well. A sentence being read is left as it is: its words
     * not read yet are skipped. Shows the result and saves it for a saved text.
     */
    fun finish() {
        val state = _uiState.value.sessionState
        if (state != SessionState.READING && state != SessionState.PAUSED) return
        if (state == SessionState.READING) stopEngine()
        aligner.stop()
        publishProgress()
        val text = _uiState.value.text
        fun displayOf(indices: Collection<Int>) = indices.sorted().map { bare(text.words[it]) }
        val sentences = aligner.firstReadings.toSortedMap()
        val firsts = sentences.values
        val misread = firsts.flatMap { it.misread.keys }
        val corrected = misread.filter { aligner.getWordStatus(it) == WordStatus.READ }
        val result = ReadingResult(
            totalWords = firsts.sumOf { it.read.size + it.misread.size },
            correctWords = firsts.sumOf { it.read.size },
            durationMs = elapsedMs,
            skippedWords = displayOf(misread - corrected.toSet()),
            retriedWords = displayOf(firsts.flatMap { it.retried }),
            helpedWords = displayOf(helpedIndices),
            correctedWords = displayOf(corrected),
            uncertainWords = displayOf(firsts.flatMap { it.uncertain }),
            errorSentences = sentences.filterValues { it.misread.isNotEmpty() }.map { (sentence, reading) ->
                ReadSentence(
                    text.words.indices.filter { text.sentenceOf[it] == sentence }.map { i ->
                        when (i) {
                            in reading.read -> ReadWord(text.words[i].display, WordStatus.READ)
                            in reading.misread -> ReadWord(text.words[i].display, WordStatus.MISREAD, reading.misread.getValue(i))
                            else -> ReadWord(text.words[i].display, WordStatus.UNCHECKED)
                        }
                    },
                    reading.tooManyErrors
                )
            }
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
        if (state == SessionState.READING) stopEngine()
        _uiState.update { it.copy(sessionState = SessionState.READY) }
    }

    /**
     * The child taps the word at [index]: a word of the sentence to read now is said ([sayWord]); a word of another
     * sentence chooses that sentence to read next, also a sentence read already, to read it again.
     */
    fun tapWord(index: Int) {
        val state = _uiState.value
        if (!isActive()) return
        val sentence = state.text.sentenceOf.getOrNull(index) ?: return
        if (sentence == state.currentSentence) {
            sayWord(index)
            return
        }
        aligner.startAt(sentence)
        publishProgress()
        // What the model heard so far belongs to the sentence left: it listens afresh for the chosen one.
        if (state.sessionState == SessionState.READING) {
            stopEngine()
            startEngine()
        }
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
        engine.start(mode, if (mode == RecognitionMode.GRAMMAR_CONSTRAINED) grammar() else null)
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
        aligner.processPartial(text.split(" ").filter { it.isNotBlank() })
        publishProgress()
    }

    /**
     * Once every sentence is read well, reading is finished. At the end of the text reading goes on with the first
     * sentence not read yet or read with too many errors.
     */
    private fun handleFinal(json: String) {
        if (_uiState.value.sessionState != SessionState.READING) return
        val (words, unsure) = finalWords(json)
        if (words.isEmpty()) return
        aligner.processFinal(words, unsure)
        val toRead = aligner.sentenceToRead
        when {
            toRead < 0 -> finish()
            aligner.nextIndex == _uiState.value.text.words.size -> aligner.startAt(toRead)
        }
        publishProgress()
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
        val text = _uiState.value.text
        val count = text.words.size
        val next = aligner.nextIndex
        _uiState.update {
            it.copy(
                wordStatuses = List(count) { i -> aligner.getWordStatus(i) },
                heard = (0 until count).mapNotNull { i -> aligner.heard(i)?.let { heard -> i to heard } }.toMap(),
                sentenceStatuses = List(text.sentences.size) { s -> aligner.sentenceStatus(s) },
                currentWordIndex = if (next < count) next else -1,
                currentSentence = if (next < count) text.sentenceOf[next] else -1
            )
        }
    }

    /** All the words of the text, so the model follows the child anywhere in it. */
    private fun grammar(): List<String> = _uiState.value.text.words.mapTo(mutableSetOf()) { spelling(it) }.toList() + "[unk]"

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
