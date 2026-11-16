package com.gefolgwar.readwithkid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Room implementation behind [ChildRepository]: children are stored in the database file and survive reopening it. */
@RunWith(RobolectricTestRunner::class)
class RoomChildRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "children-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun <T> withRepository(block: suspend (ChildRepository) -> T): T {
        val db = AppDatabase.build(context, dbName)
        try {
            return runBlocking { block(db.children()) }
        } finally {
            db.close()
        }
    }

    @Test
    fun `a new database has one child without a name`() {
        assertEquals(listOf(Child(1, "")), withRepository { it.observeAll().first() })
    }

    @Test
    fun `added, renamed and deleted children survive reopening the database, in the order they were added`() {
        withRepository { repo ->
            val olya = repo.save(Child(name = "Оля"))
            val petro = repo.save(Child(name = "Петро"))
            assertEquals(1L, repo.save(Child(1, "Марко")))
            repo.save(Child(olya, "Ольга"))
            repo.delete(petro)
        }

        assertEquals(listOf("Марко", "Ольга"), withRepository { it.observeAll().first() }.map { it.name })
    }

    @Test
    fun `deleting a child deletes their texts and readings, and keeps the other children's`() {
        val db = AppDatabase.build(context, dbName)
        try {
            runBlocking {
                val olya = db.children().save(Child(name = "Оля"))
                val kit = db.texts().save(SavedText(title = "Кіт", rawText = "Кіт спав.", createdAt = 1, childId = 1))
                val katze = db.texts().save(SavedText(title = "Katze", rawText = "Die Katze schläft.", createdAt = 2, childId = olya))
                val result = ReadingResult(2, 2, 2_000, emptyList(), emptyList())
                db.readings().saveReading(ReadingRecord(textId = kit, textTitle = "Кіт", readAt = 1, result = result))
                db.readings().saveReading(ReadingRecord(textId = katze, textTitle = "Katze", readAt = 2, result = result))

                db.children().delete(olya)

                assertEquals(listOf(1L), db.children().observeAll().first().map { it.id })
                assertEquals(emptyList<SavedText>(), db.texts().observeAll(olya).first())
                assertEquals(emptyList<ReadingRecord>(), db.readings().observeReadings(katze).first())
                assertEquals(listOf("Кіт"), db.texts().observeAll(1).first().map { it.title })
                assertEquals(listOf(1L), db.readings().observeReadings(kit).first().map { it.readAt })
            }
        } finally {
            db.close()
        }
    }
}
