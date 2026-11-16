package com.gefolgwar.readwithkid

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Room implementation behind [ReadingRepository]: saved readings, what deleting removes and upgrades of older databases. */
@RunWith(RobolectricTestRunner::class)
class RoomReadingRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "readings-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun <T> withDb(block: suspend (AppDatabase) -> T): T {
        val db = AppDatabase.build(context, dbName)
        try {
            return runBlocking { block(db) }
        } finally {
            db.close()
        }
    }

    private fun reading(textId: Long, readAt: Long, result: ReadingResult) =
        ReadingRecord(textId = textId, textTitle = "Казка", readAt = readAt, result = result)

    @Test
    fun `readings of a text with their word lists survive reopening the database, newest first`() {
        val first = ReadingResult(6, 4, 30_000, listOf("раму", "будь-який"), listOf("читав"), listOf("книжку", "раму"))
        val second = ReadingResult(6, 6, 20_000, emptyList(), emptyList())
        val text = withDb { db ->
            val text = db.texts().save(SavedText(title = "Казка", rawText = "Мама мила раму.", createdAt = 1, childId = 1))
            val other = db.texts().save(SavedText(title = "Інша", rawText = "Кіт спав.", createdAt = 2, childId = 1))
            db.readings().saveReading(reading(text, readAt = 100, result = first))
            db.readings().saveReading(reading(other, readAt = 150, result = first))
            db.readings().saveReading(reading(text, readAt = 200, result = second))
            text
        }

        withDb { db ->
            assertEquals(listOf(second, first), db.readings().observeReadings(text).first().map { it.result })
        }
    }

    @Test
    fun `deleting a text removes its readings`() {
        withDb { db ->
            val text = db.texts().save(SavedText(title = "Казка", rawText = "Мама мила раму.", createdAt = 1, childId = 1))
            val other = db.texts().save(SavedText(title = "Інша", rawText = "Кіт спав.", createdAt = 2, childId = 1))
            val result = ReadingResult(3, 3, 3_000, emptyList(), emptyList())
            db.readings().saveReading(reading(text, readAt = 1, result = result))
            db.readings().saveReading(reading(other, readAt = 2, result = result))

            db.texts().delete(text)

            assertEquals(emptyList<ReadingRecord>(), db.readings().observeReadings(text).first())
            assertEquals(listOf(2L), db.readings().observeReadings(other).first().map { it.readAt })
        }
    }

    @Test
    fun `deleting a reading removes only that reading`() {
        withDb { db ->
            val text = db.texts().save(SavedText(title = "Казка", rawText = "Мама мила раму.", createdAt = 1, childId = 1))
            val result = ReadingResult(3, 3, 3_000, emptyList(), emptyList())
            val first = db.readings().saveReading(reading(text, readAt = 1, result = result))
            db.readings().saveReading(reading(text, readAt = 2, result = result))

            db.readings().deleteReading(first)

            assertEquals(listOf(2L), db.readings().observeReadings(text).first().map { it.readAt })
            assertEquals(1, db.texts().observeAll(1).first().size)
        }
    }

    /** The tables as version 3 of the app left them: texts with their language, profiles and assignments. */
    private fun SQLiteDatabase.createV3Tables() {
        execSQL(
            "CREATE TABLE `texts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`rawText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `language` TEXT NOT NULL DEFAULT 'UK')"
        )
        execSQL("CREATE TABLE `children` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `avatar` TEXT NOT NULL)")
        execSQL(
            "CREATE TABLE `assignments` (`childId` INTEGER NOT NULL, `textId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`childId`, `textId`), " +
                "FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`textId`) REFERENCES `texts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        execSQL("CREATE INDEX `index_assignments_textId` ON `assignments` (`textId`)")
    }

    /** The tables as version 4 of the app left them: readings are kept per child. */
    private fun SQLiteDatabase.createV4Tables() {
        createV3Tables()
        execSQL(
            "CREATE TABLE `readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`childId` INTEGER NOT NULL, `textId` INTEGER NOT NULL, `textTitle` TEXT NOT NULL, " +
                "`readAt` INTEGER NOT NULL, `totalWords` INTEGER NOT NULL, `correctWords` INTEGER NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, `skippedWords` TEXT NOT NULL, `retriedWords` TEXT NOT NULL, " +
                "FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        execSQL("CREATE INDEX `index_readings_childId` ON `readings` (`childId`)")
    }

    @Test
    fun `a database from before saved readings is upgraded and keeps its texts`() {
        // A database as version 3 of the app left it.
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.createV3Tables()
            db.execSQL("INSERT INTO texts (title, rawText, createdAt) VALUES ('Казка', 'Мама мила раму.', 1)")
            db.execSQL("INSERT INTO children (name, avatar) VALUES ('Оля', 'RABBIT')")
            db.version = 3
        }

        withDb { db ->
            val text = db.texts().observeAll(1).first().single()
            assertEquals("Казка", text.title)
            db.readings().saveReading(reading(text.id, readAt = 1, result = ReadingResult(3, 2, 3_000, listOf("раму"), emptyList())))
            assertEquals(listOf(listOf("раму")), db.readings().observeReadings(text.id).first().map { it.result.skippedWords })
        }
    }

    @Test
    fun `a database from before pronunciation help is upgraded, its readings had no help`() {
        // A database as version 4 of the app left it.
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.createV4Tables()
            db.execSQL("INSERT INTO texts (title, rawText, createdAt) VALUES ('Казка', 'Мама мила раму.', 1)")
            db.execSQL("INSERT INTO children (name, avatar) VALUES ('Оля', 'RABBIT')")
            db.execSQL(
                "INSERT INTO readings (childId, textId, textTitle, readAt, totalWords, correctWords, durationMs, " +
                    "skippedWords, retriedWords) VALUES (1, 1, 'Казка', 1, 3, 2, 3000, 'раму', '')"
            )
            db.version = 4
        }

        withDb { db ->
            val old = db.readings().observeReadings(1).first().single()
            assertEquals(ReadingResult(3, 2, 3_000, listOf("раму"), emptyList(), emptyList()), old.result)
            val helped = ReadingResult(3, 3, 3_000, emptyList(), emptyList(), listOf("раму"))
            db.readings().saveReading(reading(1, readAt = 2, result = helped))
            assertEquals(listOf(listOf("раму"), emptyList()), db.readings().observeReadings(1).first().map { it.result.helpedWords })
        }
    }

    @Test
    fun `upgrading from profiles keeps texts and their readings and removes profiles and assignments`() {
        // A database as version 5 of the app left it: two children read text 1; text 2 is assigned; text 9 was deleted.
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.createV4Tables()
            db.execSQL("ALTER TABLE `readings` ADD COLUMN `helpedWords` TEXT NOT NULL DEFAULT ''")
            db.execSQL("INSERT INTO texts (id, title, rawText, createdAt, language) VALUES (1, 'Казка', 'Мама мила раму.', 1, 'UK')")
            db.execSQL("INSERT INTO texts (id, title, rawText, createdAt, language) VALUES (2, 'Katze', 'Die Katze schläft.', 2, 'DE')")
            db.execSQL("INSERT INTO children (id, name, avatar) VALUES (1, 'Оля', 'RABBIT'), (2, 'Петро', 'ROCKET')")
            db.execSQL("INSERT INTO assignments (childId, textId) VALUES (1, 1), (2, 2)")
            db.execSQL(
                "INSERT INTO readings (childId, textId, textTitle, readAt, totalWords, correctWords, durationMs, " +
                    "skippedWords, retriedWords, helpedWords) VALUES " +
                    "(1, 1, 'Казка', 10, 3, 2, 3000, 'раму', '', 'мила'), " +
                    "(2, 1, 'Казка', 20, 3, 3, 2000, '', 'мама', ''), " +
                    "(1, 9, 'Видалений', 30, 3, 3, 2000, '', '', '')"
            )
            db.version = 5
        }

        withDb { db ->
            val texts = db.texts().observeAll(1).first()
            assertEquals(listOf("Katze" to TextLanguage.DE, "Казка" to TextLanguage.UK), texts.map { it.title to it.language })

            val history = db.readings().observeReadings(1).first()
            assertEquals(listOf(20L, 10L), history.map { it.readAt })
            assertEquals(
                listOf(
                    ReadingResult(3, 3, 2_000, emptyList(), listOf("мама"), emptyList()),
                    ReadingResult(3, 2, 3_000, listOf("раму"), emptyList(), listOf("мила"))
                ),
                history.map { it.result }
            )
            assertEquals(emptyList<ReadingRecord>(), db.readings().observeReadings(9).first())

            val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
            }
            assertFalse("assignments" in tables)
            // The old profiles are gone; the texts belong to the one unnamed child of the later upgrade.
            assertEquals(listOf(""), db.children().observeAll().first().map { it.name })
        }
    }
}
