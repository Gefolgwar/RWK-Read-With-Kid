package com.gefolgwar.readwithkid

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Built-in avatars; stored by name, drawn in the UI. */
enum class Avatar { CAT, RABBIT, BEE, BIRD, ROCKET, STAR, SUN, TREE, BALL, CAR, MUSIC, CASTLE }

/** A child's profile. [id] is 0 until the profile is saved. */
@Entity(tableName = "children")
data class ChildProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatar: Avatar
)

/** A text assigned to a child. Removed together with the child or the text. */
@Entity(
    tableName = "assignments",
    primaryKeys = ["childId", "textId"],
    foreignKeys = [
        ForeignKey(entity = ChildProfile::class, parentColumns = ["id"], childColumns = ["childId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SavedText::class, parentColumns = ["id"], childColumns = ["textId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("textId")]
)
data class TextAssignment(val childId: Long, val textId: Long)

interface ChildRepository {
    /** All profiles in the order they were created. */
    fun observeChildren(): Flow<List<ChildProfile>>

    /** Inserts a profile with id 0, otherwise updates it. Returns the profile's id. */
    suspend fun saveChild(child: ChildProfile): Long

    /** Deletes the profile together with everything stored for it. */
    suspend fun deleteChild(id: Long)

    fun observeAssignments(): Flow<List<TextAssignment>>

    /** Makes [childIds] exactly the children the text is assigned to. */
    suspend fun assignText(textId: Long, childIds: Set<Long>)
}

@Dao
abstract class ChildDao : ChildRepository {
    @Query("SELECT * FROM children ORDER BY id")
    abstract override fun observeChildren(): Flow<List<ChildProfile>>

    @Insert
    protected abstract suspend fun insert(child: ChildProfile): Long

    @Update
    protected abstract suspend fun update(child: ChildProfile)

    override suspend fun saveChild(child: ChildProfile): Long =
        if (child.id == 0L) insert(child) else child.id.also { update(child) }

    // Assignments go with the profile through the foreign key's ON DELETE CASCADE.
    @Query("DELETE FROM children WHERE id = :id")
    abstract override suspend fun deleteChild(id: Long)

    @Query("SELECT * FROM assignments")
    abstract override fun observeAssignments(): Flow<List<TextAssignment>>

    @Query("DELETE FROM assignments WHERE textId = :textId")
    protected abstract suspend fun clearAssignments(textId: Long)

    @Insert
    protected abstract suspend fun insertAssignments(assignments: List<TextAssignment>)

    @Transaction
    override suspend fun assignText(textId: Long, childIds: Set<Long>) {
        clearAssignments(textId)
        insertAssignments(childIds.map { TextAssignment(it, textId) })
    }
}
