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

data class LibraryUiState(
    val texts: List<SavedText> = emptyList(),
    /** Open editor, or null when the library list is shown. */
    val editor: TextEditorState? = null
)

class LibraryViewModel(
    private val repository: TextRepository,
    private val clockMs: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val editor = MutableStateFlow<TextEditorState?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(repository.observeAll(), editor, ::LibraryUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

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
        val text = SavedText(
            id = current.id,
            title = current.title,
            rawText = current.rawText.trim(),
            createdAt = if (current.id == 0L) clockMs() else current.createdAt,
            language = current.language
        )
        editor.value = null
        viewModelScope.launch { repository.save(text) }
    }

    fun delete(text: SavedText) {
        viewModelScope.launch { repository.delete(text.id) }
    }

    companion object {
        fun factory(createTexts: () -> TextRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { LibraryViewModel(createTexts()) }
            }
    }
}
