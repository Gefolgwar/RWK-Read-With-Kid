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

/** A child's text whose reading is saved when it is finished. */
data class ChildReading(val childId: Long, val textId: Long, val textTitle: String)

/**
 * One finished reading of a text by a child. Removed together with the child; kept when the text is
 * deleted or renamed, so it stores the title the text had when it was read.
 */
@Entity(
    tableName = "readings",
    foreignKeys = [
        ForeignKey(entity = ChildProfile::class, parentColumns = ["id"], childColumns = ["childId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("childId")]
)
data class ReadingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val textId: Long,
    val textTitle: String,
    /** When the reading was finished, ms since the epoch. */
    val readAt: Long,
    @Embedded val result: ReadingResult
)

interface ReadingRepository {
    /** All readings, oldest first. */
    fun observeReadings(): Flow<List<ReadingRecord>>

    /** Inserts the reading. Returns its id. */
    suspend fun saveReading(record: ReadingRecord): Long
}

@Dao
abstract class ReadingDao : ReadingRepository {
    @Query("SELECT * FROM readings ORDER BY readAt, id")
    abstract override fun observeReadings(): Flow<List<ReadingRecord>>

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
