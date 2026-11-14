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

/** The Room implementation behind [ReadingRepository]: saved readings and what deleting removes. */
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

    private fun reading(childId: Long, textId: Long, readAt: Long, result: ReadingResult) =
        ReadingRecord(childId = childId, textId = textId, textTitle = "Казка", readAt = readAt, result = result)

    @Test
    fun `readings with their word lists survive reopening the database, oldest first`() {
        val first = ReadingResult(6, 4, 30_000, listOf("раму", "будь-який"), listOf("читав"))
        val second = ReadingResult(6, 6, 20_000, emptyList(), emptyList())
        withDb { db ->
            val olya = db.children().saveChild(ChildProfile(name = "Оля", avatar = Avatar.RABBIT))
            val text = db.texts().save(SavedText(title = "Казка", rawText = "Мама мила раму.", createdAt = 1))
            db.readings().saveReading(reading(olya, text, readAt = 200, result = second))
            db.readings().saveReading(reading(olya, text, readAt = 100, result = first))
        }

        withDb { db ->
            assertEquals(listOf(first, second), db.readings().observeReadings().first().map { it.result })
        }
    }

    @Test
    fun `deleting a profile removes its readings, deleting a text keeps them`() {
        withDb { db ->
            val olya = db.children().saveChild(ChildProfile(name = "Оля", avatar = Avatar.RABBIT))
            val petro = db.children().saveChild(ChildProfile(name = "Петро", avatar = Avatar.ROCKET))
            val text = db.texts().save(SavedText(title = "Казка", rawText = "Мама мила раму.", createdAt = 1))
            val result = ReadingResult(3, 3, 3_000, emptyList(), emptyList())
            db.readings().saveReading(reading(olya, text, readAt = 1, result = result))
            db.readings().saveReading(reading(petro, text, readAt = 2, result = result))

            db.children().deleteChild(olya)
            db.texts().delete(text)

            val left = db.readings().observeReadings().first()
            assertEquals(listOf(petro to "Казка"), left.map { it.childId to it.textTitle })
        }
    }

    @Test
    fun `a database from before saved readings is upgraded and keeps its data`() {
        // A database as version 3 of the app left it.
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE `texts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`rawText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `language` TEXT NOT NULL DEFAULT 'UK')"
            )
            db.execSQL("CREATE TABLE `children` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `avatar` TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE `assignments` (`childId` INTEGER NOT NULL, `textId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`childId`, `textId`), " +
                    "FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`textId`) REFERENCES `texts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX `index_assignments_textId` ON `assignments` (`textId`)")
            db.execSQL("INSERT INTO children (name, avatar) VALUES ('Оля', 'RABBIT')")
            db.version = 3
        }

        withDb { db ->
            val olya = db.children().observeChildren().first().single()
            assertEquals("Оля", olya.name)
            db.readings().saveReading(reading(olya.id, 1, readAt = 1, result = ReadingResult(3, 2, 3_000, listOf("раму"), emptyList())))
            assertEquals(listOf(listOf("раму")), db.readings().observeReadings().first().map { it.result.skippedWords })
        }
    }
}
