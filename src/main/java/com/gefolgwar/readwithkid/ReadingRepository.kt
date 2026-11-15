package com.gefolgwar.readwithkid

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import kotlinx.coroutines.flow.Flow

/** A saved text whose reading is saved when it is finished. */
data class TextReading(val textId: Long, val textTitle: String)

/** One finished reading of a text. Removed together with the text; stores the title the text had when it was read. */
@Entity(
    tableName = "readings",
    foreignKeys = [
        ForeignKey(entity = SavedText::class, parentColumns = ["id"], childColumns = ["textId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("textId")]
)
data class ReadingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textId: Long,
    val textTitle: String,
    /** When the reading was finished, ms since the epoch. */
    val readAt: Long,
    @Embedded val result: ReadingResult
)

interface ReadingRepository {
    /** Readings of the text, newest first. */
    fun observeReadings(textId: Long): Flow<List<ReadingRecord>>

    /** Inserts the reading. Returns its id. */
    suspend fun saveReading(record: ReadingRecord): Long
}

@Dao
abstract class ReadingDao : ReadingRepository {
    @Query("SELECT * FROM readings WHERE textId = :textId ORDER BY readAt DESC, id DESC")
    abstract override fun observeReadings(textId: Long): Flow<List<ReadingRecord>>

    @Insert
    abstract override suspend fun saveReading(record: ReadingRecord): Long
}

/** Word lists are stored one word per line. */
class WordListConverter {
    @TypeConverter
    fun toText(words: List<String>): String = words.joinToString("\n")

    @TypeConverter
    fun toWords(text: String): List<String> = if (text.isEmpty()) emptyList() else text.split("\n")
}
