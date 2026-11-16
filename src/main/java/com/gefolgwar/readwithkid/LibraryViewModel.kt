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
    val activeChild: Child? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: TextRepository,
    private val readings: ReadingRepository,
    private val children: ChildRepository,
    private val session: LibrarySession,
    private val clockMs: () -> Long = System::currentTimeMillis
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

    val uiState: StateFlow<LibraryUiState> = combine(library, editor, history) { (child, texts), editor, history ->
        LibraryUiState(texts, editor, history, child)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

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
        if (shownReadingId.value == reading.id) shownReadingId.value = null
        viewModelScope.launch { readings.deleteReading(reading.id) }
    }

    fun newText() {
        editor.value = TextEditorState()
    }

    fun edit(text: SavedText) {
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
        if (!current.canSave) return
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
