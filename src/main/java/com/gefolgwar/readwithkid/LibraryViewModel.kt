package com.gefolgwar.readwithkid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** A text being added ([id] 0) or edited. */
data class TextEditorState(
    val id: Long = 0,
    val createdAt: Long = 0,
    val rawText: String = "",
    /** Language the parent picked, or null while it follows the alphabet of the text. */
    val chosenLanguage: TextLanguage? = null
) {
    val title: String = TextPreparation.autoTitle(rawText)
    val warnings: TextWarnings = TextPreparation.findHardParts(rawText)
    val detectedLanguage: TextLanguage? = TextPreparation.detectLanguage(rawText)
    val language: TextLanguage get() = chosenLanguage ?: detectedLanguage ?: TextLanguage.UK
    val canSave: Boolean get() = title.isNotEmpty()
}

/** The readings of [text], newest first; [shown] is the reading whose result is open, or null for the list. */
data class TextHistory(
    val text: SavedText,
    val readings: List<ReadingRecord> = emptyList(),
    val shown: ReadingRecord? = null
)

data class LibraryUiState(
    /** Texts of the [activeChild]. */
    val texts: List<SavedText> = emptyList(),
    /** Open editor, or null when the library list is shown. */
    val editor: TextEditorState? = null,
    /** Open history of a text, or null. */
    val history: TextHistory? = null,
    /** The child whose library is open, or null until the children are loaded. */
    val activeChild: Child? = null,
    /** Locked for the child: texts can be read and their history viewed, nothing can be changed. */
    val childMode: Boolean = false,
    /** Question a grown-up answers to leave child mode, or null while not asked. */
    val unlockQuestion: UnlockQuestion? = null,
    val settingsOpen: Boolean = false
)

/** "How much is [a] × [b]?" — easy for a parent, hard for a 5–7-year-old. */
data class UnlockQuestion(val a: Int, val b: Int) {
    val answer: Int get() = a * b

    companion object {
        /** Each factor from 3 to 9. */
        fun random(random: Random) = UnlockQuestion(random.nextInt(3, 10), random.nextInt(3, 10))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: TextRepository,
    private val readings: ReadingRepository,
    private val children: ChildRepository,
    private val session: LibrarySession,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default
) : ViewModel() {

    /** The child who was active last time, or the first child if that one no longer exists; with their texts. */
    private val library: Flow<Pair<Child?, List<SavedText>>> = children.observeAll()
        .map { all -> all.find { it.id == session.activeChildId } ?: all.firstOrNull() }
        .distinctUntilChanged()
        .flatMapLatest { child ->
            if (child == null) flowOf(null to emptyList()) else repository.observeAll(child.id).map { child to it }
        }

    private val editor = MutableStateFlow<TextEditorState?>(null)
    /** Text whose history is open, and the id of the reading shown from it. */
    private val historyText = MutableStateFlow<SavedText?>(null)
    private val shownReadingId = MutableStateFlow<Long?>(null)

    private val history: Flow<TextHistory?> = combine(
        historyText.flatMapLatest { text ->
            if (text == null) flowOf(null) else readings.observeReadings(text.id).map { TextHistory(text, it) }
        },
        shownReadingId
    ) { history, shownId -> history?.copy(shown = history.readings.find { it.id == shownId }) }

    private val childMode = MutableStateFlow(session.childMode)
    private val unlockQuestion = MutableStateFlow<UnlockQuestion?>(null)
    private val settingsOpen = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> = combine(
        library, editor, history, childMode, combine(unlockQuestion, settingsOpen, ::Pair)
    ) { (child, texts), editor, history, childMode, (question, settingsOpen) ->
        LibraryUiState(texts, editor, history, child, childMode, question, settingsOpen)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    private val parentMode get() = !childMode.value

    private fun setChildMode(on: Boolean) {
        session.childMode = on
        childMode.value = on
    }

    /** One tap hands the phone to the child. */
    fun lock() = setChildMode(true)

    /** Asks a new question; only its correct answer leaves child mode. */
    fun askToUnlock() {
        if (childMode.value) unlockQuestion.value = UnlockQuestion.random(random)
    }

    /** A correct [answer] switches to parent mode; any other answer keeps child mode. Either way the question closes. */
    fun answerUnlock(answer: String) {
        val question = unlockQuestion.value ?: return
        unlockQuestion.value = null
        if (answer.trim().toIntOrNull() == question.answer) setChildMode(false)
    }

    fun dismissUnlock() {
        unlockQuestion.value = null
    }

    fun openSettings() {
        if (parentMode) settingsOpen.value = true
    }

    fun closeSettings() {
        settingsOpen.value = false
    }

    fun openHistory(text: SavedText) {
        shownReadingId.value = null
        historyText.value = text
    }

    fun closeHistory() {
        historyText.value = null
        shownReadingId.value = null
    }

    /** Opens the result of [reading], as shown at the end of that reading. */
    fun showReading(reading: ReadingRecord) {
        shownReadingId.value = reading.id
    }

    fun closeReading() {
        shownReadingId.value = null
    }

    fun deleteReading(reading: ReadingRecord) {
        if (!parentMode) return
        if (shownReadingId.value == reading.id) shownReadingId.value = null
        viewModelScope.launch { readings.deleteReading(reading.id) }
    }

    fun newText() {
        if (parentMode) editor.value = TextEditorState()
    }

    fun edit(text: SavedText) {
        if (!parentMode) return
        // A stored language the alphabet would suggest anyway keeps following the text.
        val chosen = text.language.takeIf { it != TextPreparation.detectLanguage(text.rawText) }
        editor.value = TextEditorState(text.id, text.createdAt, text.rawText, chosen)
    }

    fun chooseLanguage(language: TextLanguage) = editor.update { it?.copy(chosenLanguage = language) }

    fun updateRawText(raw: String) = editor.update { it?.copy(rawText = raw) }

    fun cancelEdit() {
        editor.value = null
    }

    fun save() {
        val current = editor.value ?: return
        if (!current.canSave || !parentMode) return
        // Texts are added and edited in the open library, so they belong to its child.
        val child = uiState.value.activeChild ?: return
        val text = SavedText(
            id = current.id,
            title = current.title,
            rawText = current.rawText.trim(),
            createdAt = if (current.id == 0L) clockMs() else current.createdAt,
            language = current.language,
            childId = child.id
        )
        editor.value = null
        viewModelScope.launch { repository.save(text) }
    }

    fun delete(text: SavedText) {
        if (!parentMode) return
        viewModelScope.launch { repository.delete(text.id) }
    }

    companion object {
        fun factory(
            createTexts: () -> TextRepository,
            createReadings: () -> ReadingRepository,
            createChildren: () -> ChildRepository,
            createSession: () -> LibrarySession
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { LibraryViewModel(createTexts(), createReadings(), createChildren(), createSession()) }
            }
    }
}
