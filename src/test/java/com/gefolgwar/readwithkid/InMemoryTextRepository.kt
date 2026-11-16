package com.gefolgwar.readwithkid

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryTextRepository : TextRepository {
    private val texts = MutableStateFlow<Map<Long, SavedText>>(emptyMap())
    private var nextId = 1L

    override fun observeAll(childId: Long): Flow<List<SavedText>> =
        texts.map { all ->
            all.values.filter { it.childId == childId }
                .sortedWith(compareByDescending<SavedText> { it.createdAt }.thenByDescending { it.id })
        }

    override suspend fun save(text: SavedText): Long {
        val saved = if (text.id == 0L) text.copy(id = nextId++) else text
        texts.update { it + (saved.id to saved) }
        return saved.id
    }

    override suspend fun delete(id: Long) {
        texts.update { it - id }
    }
}
