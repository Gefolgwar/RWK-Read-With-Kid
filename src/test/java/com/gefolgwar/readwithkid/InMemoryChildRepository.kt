package com.gefolgwar.readwithkid

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class InMemoryChildRepository : ChildRepository {
    private val children = MutableStateFlow<Map<Long, ChildProfile>>(emptyMap())
    private val assignments = MutableStateFlow<Set<TextAssignment>>(emptySet())
    private var nextId = 1L

    override fun observeChildren(): Flow<List<ChildProfile>> = children.map { all -> all.values.sortedBy { it.id } }

    override suspend fun saveChild(child: ChildProfile): Long {
        val saved = if (child.id == 0L) child.copy(id = nextId++) else child
        children.update { it + (saved.id to saved) }
        return saved.id
    }

    override suspend fun deleteChild(id: Long) {
        assignments.update { all -> all.filterTo(mutableSetOf()) { it.childId != id } }
        children.update { it - id }
    }

    override fun observeAssignments(): Flow<List<TextAssignment>> = assignments.map { it.toList() }

    override suspend fun assignText(textId: Long, childIds: Set<Long>) {
        assignments.update { all -> all.filterTo(mutableSetOf()) { it.textId != textId } + childIds.map { TextAssignment(it, textId) } }
    }
}
