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

/** A profile being added ([id] 0) or edited. */
data class ProfileEditorState(
    val id: Long = 0,
    val name: String = "",
    val avatar: Avatar = Avatar.entries.first()
) {
    val canSave: Boolean get() = name.isNotBlank()
}

data class ProfilesUiState(
    val children: List<ChildProfile> = emptyList(),
    /** Open editor, or null when the profile list is shown. */
    val editor: ProfileEditorState? = null
)

/** Parent side: create, edit and delete child profiles. */
class ProfilesViewModel(private val repository: ChildRepository) : ViewModel() {

    private val editor = MutableStateFlow<ProfileEditorState?>(null)

    val uiState: StateFlow<ProfilesUiState> = combine(repository.observeChildren(), editor, ::ProfilesUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfilesUiState())

    fun newProfile() {
        editor.value = ProfileEditorState()
    }

    fun edit(child: ChildProfile) {
        editor.value = ProfileEditorState(child.id, child.name, child.avatar)
    }

    fun updateName(name: String) = editor.update { it?.copy(name = name) }

    fun updateAvatar(avatar: Avatar) = editor.update { it?.copy(avatar = avatar) }

    fun cancelEdit() {
        editor.value = null
    }

    fun save() {
        val current = editor.value ?: return
        if (!current.canSave) return
        editor.value = null
        viewModelScope.launch { repository.saveChild(ChildProfile(current.id, current.name.trim(), current.avatar)) }
    }

    /** Deletes the profile together with its data. */
    fun delete(child: ChildProfile) {
        viewModelScope.launch { repository.deleteChild(child.id) }
    }

    companion object {
        fun factory(createRepository: () -> ChildRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfilesViewModel(createRepository()) }
        }
    }
}
