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
import java.util.Calendar
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
) {
    /** Score (words correct per minute) of each reading, oldest first, for the progress chart. */
    val scores: List<Int> = readings.asReversed().map { it.result.wordsCorrectPerMinute }
}

/** Shown on a text's card: [wordCount] as the reading session counts them, and the words read correctly today. */
data class TextStats(val wordCount: Int, val wordsReadToday: Int)

data class LibraryUiState(
    /** Texts of the [activeChild]. */
    val texts: List<SavedText> = emptyList(),
    /** Stats of each of the [texts], by text id. */
    val textStats: Map<Long, TextStats> = emptyMap(),
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
    val settingsOpen: Boolean = false,
    /** All children, in the order they were added. */
    val children: List<Child> = emptyList(),
    /** The list of children to switch between or add to is open. */
    val childrenOpen: Boolean = false
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

    /** The child chosen last, kept across app starts. */
    private val activeChildId = MutableStateFlow(session.activeChildId)

    /** The active child, or the first child if that one no longer exists; with their texts and their stats. */
    private val library: Flow<Triple<Child?, List<SavedText>, Map<Long, TextStats>>> =
        combine(children.observeAll(), activeChildId) { all, id -> all.find { it.id == id } ?: all.firstOrNull() }
        .distinctUntilChanged()
        .flatMapLatest { child ->
            if (child == null) {
                flowOf(Triple(null, emptyList(), emptyMap()))
            } else {
                repository.observeAll(child.id).flatMapLatest { texts ->
                    readings.observeReadingsOf(texts.map { it.id }).map { Triple(child, texts, statsOf(texts, it)) }
                }
            }
        }

    private fun statsOf(texts: List<SavedText>, readings: List<ReadingRecord>): Map<Long, TextStats> {
        // Today is the phone's calendar day, from its local midnight to the next one.
        val today = Calendar.getInstance().apply {
            timeInMillis = clockMs()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = today.timeInMillis
        val end = today.apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        val readToday = readings.filter { it.readAt in start until end }.groupBy { it.textId }
        return texts.associate { text ->
            text.id to TextStats(
                wordCount = TextPreparation.prepare(text.rawText).words.size,
                wordsReadToday = readToday[text.id].orEmpty().sumOf { it.result.correctWords }
            )
        }
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
    private val childrenOpen = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> = combine(
        library,
        editor,
        history,
        combine(childMode, unlockQuestion, settingsOpen, ::Triple),
        combine(children.observeAll(), childrenOpen, ::Pair)
    ) { (child, texts, stats), editor, history, (childMode, question, settingsOpen), (allChildren, childrenOpen) ->
        LibraryUiState(texts, stats, editor, history, child, childMode, question, settingsOpen, allChildren, childrenOpen)
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

    fun openChildren() {
        if (parentMode) childrenOpen.value = true
    }

    fun closeChildren() {
        childrenOpen.value = false
    }

    /** Opens [child]'s library and remembers it for the next app start. */
    fun switchChild(child: Child) {
        if (!parentMode) return
        childrenOpen.value = false
        makeActive(child.id)
    }

    /** Adds a child, named [name] or not named yet when blank, and opens their (empty) library. */
    fun addChild(name: String) {
        if (!parentMode) return
        childrenOpen.value = false
        viewModelScope.launch { makeActive(children.save(Child(name = name.trim()))) }
    }

    /** Gives [child] the [name]; a blank name shows the placeholder again. */
    fun renameChild(child: Child, name: String) {
        if (!parentMode) return
        viewModelScope.launch { children.save(child.copy(name = name.trim())) }
    }

    /** Deletes [child] with their texts and readings. The last child stays; deleting the active child opens another child's library. */
    fun deleteChild(child: Child) {
        if (!parentMode) return
        val others = uiState.value.children.filter { it.id != child.id }
        if (others.isEmpty()) return
        if (child.id == uiState.value.activeChild?.id) makeActive(others.first().id)
        viewModelScope.launch { children.delete(child.id) }
    }

    private fun makeActive(id: Long) {
        session.activeChildId = id
        activeChildId.value = id
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
