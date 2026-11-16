package com.gefolgwar.readwithkid

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A child whose texts and readings are kept apart from the other children's. An empty [name] means not named yet. */
@Entity(tableName = "children")
data class Child(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = ""
)

interface ChildRepository {
    /** All children, in the order they were added. */
    fun observeAll(): Flow<List<Child>>

    /** Inserts a child with id 0, otherwise renames it. Returns the child's id. */
    suspend fun save(child: Child): Long

    /** Deletes the child together with their texts and readings. */
    suspend fun delete(id: Long)
}

@Dao
abstract class ChildDao : ChildRepository {
    @Query("SELECT * FROM children ORDER BY id")
    abstract override fun observeAll(): Flow<List<Child>>

    @Insert
    protected abstract suspend fun insert(child: Child): Long

    @Update
    protected abstract suspend fun update(child: Child)

    override suspend fun save(child: Child): Long =
        if (child.id == 0L) insert(child) else child.id.also { update(child) }

    @Query("DELETE FROM children WHERE id = :id")
    abstract override suspend fun delete(id: Long)
}
