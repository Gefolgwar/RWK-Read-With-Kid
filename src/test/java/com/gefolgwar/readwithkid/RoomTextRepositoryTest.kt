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

/** The Room implementation behind [TextRepository]: texts are stored in a database file and survive reopening it. */
@RunWith(RobolectricTestRunner::class)
class RoomTextRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "texts-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun <T> withRepository(block: suspend (TextRepository) -> T): T {
        val db = AppDatabase.build(context, dbName)
        try {
            return runBlocking { block(db.texts()) }
        } finally {
            db.close()
        }
    }

    @Test
    fun `texts survive reopening the database`() {
        withRepository { repo ->
            repo.save(SavedText(title = "Перший", rawText = "Один.", createdAt = 1))
            repo.save(SavedText(title = "Другий", rawText = "Два.", createdAt = 2))
        }

        val texts = withRepository { it.observeAll().first() }
        assertEquals(listOf("Другий", "Перший"), texts.map { it.title })
        assertEquals("Один.", texts[1].rawText)
    }

    @Test
    fun `save updates an existing text and delete removes it`() {
        withRepository { repo ->
            val id = repo.save(SavedText(title = "Стара", rawText = "Старий.", createdAt = 1))
            val otherId = repo.save(SavedText(title = "Інша", rawText = "Інший.", createdAt = 2))
            assertEquals(id, repo.save(SavedText(id = id, title = "Нова", rawText = "Новий.", createdAt = 1)))
            repo.delete(otherId)
        }

        val texts = withRepository { it.observeAll().first() }
        assertEquals(listOf(SavedText(texts.single().id, "Нова", "Новий.", 1)), texts)
    }
}
