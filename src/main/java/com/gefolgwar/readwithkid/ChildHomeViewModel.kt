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

data class ChildHomeUiState(
    val children: List<ChildProfile> = emptyList(),
    /** The child who picked their avatar, or null while avatars are shown. */
    val child: ChildProfile? = null,
    /** Texts assigned to [child], newest first. */
    val texts: List<SavedText> = emptyList()
)

/** Child side: pick your avatar, then see the texts assigned to you. */
class ChildHomeViewModel(texts: TextRepository, children: ChildRepository) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<ChildHomeUiState> = combine(
        texts.observeAll(),
        children.observeChildren(),
        children.observeAssignments(),
        selectedId
    ) { allTexts, allChildren, assignments, id ->
        val child = allChildren.find { it.id == id }
        val assigned = assignments.filter { it.childId == child?.id }.map { it.textId }.toSet()
        ChildHomeUiState(allChildren, child, allTexts.filter { it.id in assigned })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChildHomeUiState())

    fun select(child: ChildProfile) {
        selectedId.value = child.id
    }

    fun back() {
        selectedId.value = null
    }

    companion object {
        fun factory(createTexts: () -> TextRepository, createChildren: () -> ChildRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ChildHomeViewModel(createTexts(), createChildren()) }
            }
    }
}
