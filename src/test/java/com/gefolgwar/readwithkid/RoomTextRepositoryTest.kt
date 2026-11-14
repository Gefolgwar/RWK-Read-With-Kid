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

    @Test
    fun `language of a text survives reopening the database`() {
        withRepository { repo ->
            repo.save(SavedText(title = "Katze", rawText = "Die Katze schläft.", createdAt = 1, language = TextLanguage.DE))
            repo.save(SavedText(title = "Кіт", rawText = "Кіт спить.", createdAt = 2))
        }

        val texts = withRepository { it.observeAll().first() }
        assertEquals(listOf(TextLanguage.UK, TextLanguage.DE), texts.map { it.language })
    }

    @Test
    fun `texts saved before languages existed are Ukrainian after the upgrade`() {
        // A database as version 2 of the app left it: texts without a language.
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE `texts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`rawText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE `children` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `avatar` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE `assignments` (`childId` INTEGER NOT NULL, `textId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`childId`, `textId`), " +
                    "FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`textId`) REFERENCES `texts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX `index_assignments_textId` ON `assignments` (`textId`)")
            db.execSQL("INSERT INTO texts (title, rawText, createdAt) VALUES ('Казка', 'Жив собі кіт.', 1)")
            db.version = 2
        }

        val text = withRepository { it.observeAll().first().single() }
        assertEquals("Казка", text.title)
        assertEquals(TextLanguage.UK, text.language)
    }
}
