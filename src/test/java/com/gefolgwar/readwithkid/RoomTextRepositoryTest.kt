package com.gefolgwar.readwithkid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Room implementation behind [TextRepository]: texts are stored in a database file and survive reopening it. */
@RunWith(RobolectricTestRunner::class)
class RoomTextRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "texts-test.db"

    /** The child a new database starts with. */
    private val firstChild = 1L

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
            repo.save(SavedText(title = "Перший", rawText = "Один.", createdAt = 1, childId = firstChild))
            repo.save(SavedText(title = "Другий", rawText = "Два.", createdAt = 2, childId = firstChild))
        }

        val texts = withRepository { it.observeAll(firstChild).first() }
        assertEquals(listOf("Другий", "Перший"), texts.map { it.title })
        assertEquals("Один.", texts[1].rawText)
    }

    @Test
    fun `save updates an existing text and delete removes it`() {
        withRepository { repo ->
            val id = repo.save(SavedText(title = "Стара", rawText = "Старий.", createdAt = 1, childId = firstChild))
            val otherId = repo.save(SavedText(title = "Інша", rawText = "Інший.", createdAt = 2, childId = firstChild))
            assertEquals(id, repo.save(SavedText(id = id, title = "Нова", rawText = "Новий.", createdAt = 1, childId = firstChild)))
            repo.delete(otherId)
        }

        val texts = withRepository { it.observeAll(firstChild).first() }
        assertEquals(listOf(SavedText(texts.single().id, "Нова", "Новий.", 1, childId = firstChild)), texts)
    }

    @Test
    fun `language of a text survives reopening the database`() {
        withRepository { repo ->
            repo.save(SavedText(title = "Katze", rawText = "Die Katze schläft.", createdAt = 1, language = TextLanguage.DE, childId = firstChild))
            repo.save(SavedText(title = "Кіт", rawText = "Кіт спить.", createdAt = 2, childId = firstChild))
        }

        val texts = withRepository { it.observeAll(firstChild).first() }
        assertEquals(listOf(TextLanguage.UK, TextLanguage.DE), texts.map { it.language })
    }

    @Test
    fun `a new app database gives the first child a short Ukrainian and a German sample to try`() {
        val db = AppDatabase.build(context, dbName, withSamples = true)
        try {
            val texts = runBlocking { db.texts().observeAll(firstChild).first() }
            assertEquals(listOf(TextLanguage.UK, TextLanguage.DE), texts.map { it.language })
            texts.forEach { text ->
                assertTrue(text.sample)
                assertEquals(TextPreparation.autoTitle(text.rawText), text.title)
                assertEquals(text.language, TextPreparation.detectLanguage(text.rawText))
                // Nothing speech recognition handles poorly, and a few sentences only
                assertTrue(TextPreparation.findHardParts(text.rawText).isEmpty)
                assertTrue(TextPreparation.prepare(text.rawText).sentences.size in 2..4)
            }
        } finally {
            db.close()
        }
        // The samples are added once; deleted ones do not come back when the app starts again.
        withRepository { repo -> repo.observeAll(firstChild).first().forEach { repo.delete(it.id) } }
        val db2 = AppDatabase.build(context, dbName, withSamples = true)
        try {
            assertEquals(emptyList<SavedText>(), runBlocking { db2.texts().observeAll(firstChild).first() })
        } finally {
            db2.close()
        }
    }

    @Test
    fun `texts saved before samples existed are the parent's own after the upgrade`() {
        // A database as version 7 of the app left it: texts without the sample mark.
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE `children` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE `texts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`rawText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `language` TEXT NOT NULL DEFAULT 'UK', " +
                    "`childId` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX `index_texts_childId` ON `texts` (`childId`)")
            db.execSQL(
                "CREATE TABLE `readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`textId` INTEGER NOT NULL, `textTitle` TEXT NOT NULL, `readAt` INTEGER NOT NULL, " +
                    "`totalWords` INTEGER NOT NULL, `correctWords` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                    "`skippedWords` TEXT NOT NULL, `retriedWords` TEXT NOT NULL, `helpedWords` TEXT NOT NULL DEFAULT '', " +
                    "FOREIGN KEY(`textId`) REFERENCES `texts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX `index_readings_textId` ON `readings` (`textId`)")
            db.execSQL("INSERT INTO children (name) VALUES ('Оля')")
            db.execSQL("INSERT INTO texts (title, rawText, createdAt, language, childId) VALUES ('Казка', 'Жив собі кіт.', 1, 'UK', 1)")
            db.version = 7
        }

        val text = withRepository { it.observeAll(firstChild).first().single() }
        assertEquals("Казка", text.title)
        assertFalse(text.sample)
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

        val text = withRepository { it.observeAll(firstChild).first().single() }
        assertEquals("Казка", text.title)
        assertEquals(TextLanguage.UK, text.language)
    }

    @Test
    fun `upgrading to child profiles gives every text and its readings to one unnamed child`() {
        // A database as version 6 of the app left it: texts and their readings, no children.
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE `texts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`rawText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `language` TEXT NOT NULL DEFAULT 'UK')"
            )
            db.execSQL(
                "CREATE TABLE `readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`textId` INTEGER NOT NULL, `textTitle` TEXT NOT NULL, `readAt` INTEGER NOT NULL, " +
                    "`totalWords` INTEGER NOT NULL, `correctWords` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                    "`skippedWords` TEXT NOT NULL, `retriedWords` TEXT NOT NULL, `helpedWords` TEXT NOT NULL DEFAULT '', " +
                    "FOREIGN KEY(`textId`) REFERENCES `texts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX `index_readings_textId` ON `readings` (`textId`)")
            db.execSQL("INSERT INTO texts (id, title, rawText, createdAt, language) VALUES (3, 'Казка', 'Мама мила раму.', 1, 'UK')")
            db.execSQL("INSERT INTO texts (id, title, rawText, createdAt, language) VALUES (7, 'Katze', 'Die Katze schläft.', 2, 'DE')")
            db.execSQL(
                "INSERT INTO readings (textId, textTitle, readAt, totalWords, correctWords, durationMs, " +
                    "skippedWords, retriedWords, helpedWords) VALUES " +
                    "(3, 'Казка', 10, 3, 2, 3000, 'раму', '', 'мила'), (7, 'Katze', 20, 3, 3, 2000, '', '', '')"
            )
            db.version = 6
        }

        val db = AppDatabase.build(context, dbName)
        try {
            runBlocking {
                val child = db.children().observeAll().first().single()
                assertEquals("", child.name)
                val texts = db.texts().observeAll(child.id).first()
                assertEquals(
                    listOf(Triple(7L, "Katze", TextLanguage.DE), Triple(3L, "Казка", TextLanguage.UK)),
                    texts.map { Triple(it.id, it.title, it.language) }
                )
                assertEquals(
                    ReadingResult(3, 2, 3_000, listOf("раму"), emptyList(), listOf("мила")),
                    db.readings().observeReadings(3).first().single().result
                )
                assertEquals(1, db.readings().observeReadings(7).first().size)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `deleting a child deletes their texts and readings, and the other children keep theirs`() {
        val db = AppDatabase.build(context, dbName)
        try {
            runBlocking {
                val olya = db.children().save(Child(name = "Оля"))
                val mine = db.texts().save(SavedText(title = "Кіт", rawText = "Кіт.", createdAt = 1, childId = firstChild))
                val hers = db.texts().save(SavedText(title = "Пес", rawText = "Пес.", createdAt = 1, childId = olya))
                val result = ReadingResult(1, 1, 1_000, emptyList(), emptyList())
                db.readings().saveReading(ReadingRecord(textId = mine, textTitle = "Кіт", readAt = 1, result = result))
                db.readings().saveReading(ReadingRecord(textId = hers, textTitle = "Пес", readAt = 1, result = result))

                db.children().delete(olya)

                assertEquals(listOf(Child(firstChild, "")), db.children().observeAll().first())
                assertEquals(emptyList<SavedText>(), db.texts().observeAll(olya).first())
                assertEquals(emptyList<ReadingRecord>(), db.readings().observeReadings(hers).first())
                assertEquals(listOf("Кіт"), db.texts().observeAll(firstChild).first().map { it.title })
                assertEquals(1, db.readings().observeReadings(mine).first().size)
            }
        } finally {
            db.close()
        }
    }
}
