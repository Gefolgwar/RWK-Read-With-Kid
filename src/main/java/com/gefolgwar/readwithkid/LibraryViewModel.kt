package com.gefolgwar.readwithkid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A text being added ([id] 0) or edited. */
data class TextEditorState(
    val id: Long = 0,
    val createdAt: Long = 0,
    val title: String = "",
    val rawText: String = "",
    /** Children the text is assigned to. */
    val childIds: Set<Long> = emptySet()
) {
    val preview: PreparedText = TextPreparation.prepare(rawText)
    val warnings: TextWarnings = TextPreparation.findHardParts(rawText)
    val canSave: Boolean get() = title.isNotBlank() && preview.words.isNotEmpty()
}

data class LibraryUiState(
    val texts: List<SavedText> = emptyList(),
    val children: List<ChildProfile> = emptyList(),
    /** Children each text is assigned to, by text id. */
    val assignedTo: Map<Long, List<ChildProfile>> = emptyMap(),
    /** Open editor, or null when the library list is shown. */
    val editor: TextEditorState? = null
)

class LibraryViewModel(
    private val repository: TextRepository,
    private val childRepository: ChildRepository,
    private val clockMs: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val editor = MutableStateFlow<TextEditorState?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observeAll(),
        childRepository.observeChildren(),
        childRepository.observeAssignments(),
        editor
    ) { texts, children, assignments, editor ->
        val byId = children.associateBy { it.id }
        // The two flows update separately, so an assignment can briefly outlive its child.
        val assignedTo = assignments.mapNotNull { a -> byId[a.childId]?.let { a.textId to it } }
            .groupBy({ it.first }, { it.second })
        LibraryUiState(texts, children, assignedTo, editor)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    fun newText() {
        editor.value = TextEditorState()
    }

    fun edit(text: SavedText) {
        val childIds = uiState.value.assignedTo[text.id].orEmpty().map { it.id }.toSet()
        editor.value = TextEditorState(text.id, text.createdAt, text.title, text.rawText, childIds)
    }

    /** Assigns the edited text to the child, or takes it away again. */
    fun toggleChild(child: ChildProfile) = editor.update { current ->
        current?.copy(childIds = if (child.id in current.childIds) current.childIds - child.id else current.childIds + child.id)
    }

    fun updateTitle(title: String) = editor.update { it?.copy(title = title) }

    fun updateRawText(raw: String) = editor.update { it?.copy(rawText = raw) }

    /** Replaces the text with a .txt file's content; the file name becomes the title unless one was typed. */
    fun importTxt(fileName: String?, content: String) = editor.update { current ->
        current?.copy(
            rawText = content,
            title = current.title.ifBlank { fileName?.substringBeforeLast('.')?.trim().orEmpty() }
        )
    }

    fun cancelEdit() {
        editor.value = null
    }

    fun save() {
        val current = editor.value ?: return
        if (!current.canSave) return
        val text = SavedText(
            id = current.id,
            title = current.title.trim(),
            rawText = current.rawText.trim(),
            createdAt = if (current.id == 0L) clockMs() else current.createdAt
        )
        editor.value = null
        viewModelScope.launch {
            val id = repository.save(text)
            childRepository.assignText(id, current.childIds)
        }
    }

    fun delete(text: SavedText) {
        viewModelScope.launch { repository.delete(text.id) }
    }

    companion object {
        fun factory(createTexts: () -> TextRepository, createChildren: () -> ChildRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { LibraryViewModel(createTexts(), createChildren()) }
            }
    }
}
