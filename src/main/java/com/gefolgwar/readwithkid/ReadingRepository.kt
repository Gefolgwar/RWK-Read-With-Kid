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
import org.json.JSONArray
import org.json.JSONObject

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

    /** Readings of any of the texts, in no particular order. */
    fun observeReadingsOf(textIds: List<Long>): Flow<List<ReadingRecord>>

    /** Inserts the reading. Returns its id. */
    suspend fun saveReading(record: ReadingRecord): Long

    suspend fun deleteReading(id: Long)
}

@Dao
abstract class ReadingDao : ReadingRepository {
    @Query("SELECT * FROM readings WHERE textId = :textId ORDER BY readAt DESC, id DESC")
    abstract override fun observeReadings(textId: Long): Flow<List<ReadingRecord>>

    @Query("SELECT * FROM readings WHERE textId IN (:textIds)")
    abstract override fun observeReadingsOf(textIds: List<Long>): Flow<List<ReadingRecord>>

    @Insert
    abstract override suspend fun saveReading(record: ReadingRecord): Long

    @Query("DELETE FROM readings WHERE id = :id")
    abstract override suspend fun deleteReading(id: Long)
}

/** Word lists are stored one word per line. */
class WordListConverter {
    @TypeConverter
    fun toText(words: List<String>): String = words.joinToString("\n")

    @TypeConverter
    fun toWords(text: String): List<String> = if (text.isEmpty()) emptyList() else text.split("\n")
}

/** Sentences of a result are stored as JSON; no sentences as "". */
class SentenceListConverter {
    @TypeConverter
    fun toText(sentences: List<ReadSentence>): String = if (sentences.isEmpty()) "" else JSONArray(
        sentences.map { sentence ->
            JSONObject()
                .put("tooManyErrors", sentence.tooManyErrors)
                .put("words", JSONArray(sentence.words.map { JSONObject().put("text", it.text).put("status", it.status.name).put("heard", it.heard) }))
        }
    ).toString()

    @TypeConverter
    fun toSentences(text: String): List<ReadSentence> {
        if (text.isEmpty()) return emptyList()
        val sentences = JSONArray(text)
        return (0 until sentences.length()).map { i ->
            val sentence = sentences.getJSONObject(i)
            val words = sentence.getJSONArray("words")
            ReadSentence(
                (0 until words.length()).map { w ->
                    val word = words.getJSONObject(w)
                    ReadWord(word.getString("text"), WordStatus.valueOf(word.getString("status")), word.optString("heard"))
                },
                sentence.getBoolean("tooManyErrors")
            )
        }
    }
}
