package com.gefolgwar.readwithkid

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A text a parent saved in the library. [id] is 0 until the text is saved. */
@Entity(tableName = "texts")
data class SavedText(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawText: String,
    val createdAt: Long
)

interface TextRepository {
    /** All texts, newest first. */
    fun observeAll(): Flow<List<SavedText>>

    /** Inserts a text with id 0, otherwise updates it. Returns the text's id. */
    suspend fun save(text: SavedText): Long

    suspend fun delete(id: Long)
}

@Dao
abstract class TextDao : TextRepository {
    @Query("SELECT * FROM texts ORDER BY createdAt DESC, id DESC")
    abstract override fun observeAll(): Flow<List<SavedText>>

    @Insert
    protected abstract suspend fun insert(text: SavedText): Long

    @Update
    protected abstract suspend fun update(text: SavedText)

    override suspend fun save(text: SavedText): Long =
        if (text.id == 0L) insert(text) else text.id.also { update(text) }

    @Query("DELETE FROM texts WHERE id = :id")
    abstract override suspend fun delete(id: Long)
}

@Database(entities = [SavedText::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun texts(): TextDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context, "read_with_kid.db").also { instance = it }
        }

        fun build(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name).build()
    }
}
