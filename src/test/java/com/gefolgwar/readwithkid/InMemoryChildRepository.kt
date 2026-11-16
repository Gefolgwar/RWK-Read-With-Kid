package com.gefolgwar.readwithkid

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Starts like a new database: with one child who has no name yet. Deleting a child does not delete their texts here. */
class InMemoryChildRepository : ChildRepository {
    private val children = MutableStateFlow(mapOf(1L to Child(1)))
    private var nextId = 2L

    override fun observeAll(): Flow<List<Child>> = children.map { all -> all.values.sortedBy { it.id } }

    override suspend fun save(child: Child): Long {
        val saved = if (child.id == 0L) child.copy(id = nextId++) else child
        children.update { it + (saved.id to saved) }
        return saved.id
    }

    override suspend fun delete(id: Long) {
        children.update { it - id }
    }
}

class FakeLibrarySession(override var activeChildId: Long? = null) : LibrarySession
