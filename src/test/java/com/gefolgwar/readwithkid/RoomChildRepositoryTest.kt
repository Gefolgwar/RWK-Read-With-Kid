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

/** The Room implementation behind [ChildRepository]: profiles, assignments and what deleting removes. */
@RunWith(RobolectricTestRunner::class)
class RoomChildRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "children-test.db"

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

    @Test
    fun `profiles and assignments survive reopening the database`() {
        withDb { db ->
            val olya = db.children().saveChild(ChildProfile(name = "Оля", avatar = Avatar.RABBIT))
            val petro = db.children().saveChild(ChildProfile(name = "Петро", avatar = Avatar.ROCKET))
            val text = db.texts().save(SavedText(title = "Казка", rawText = "Жив собі кіт.", createdAt = 1))
            db.children().assignText(text, setOf(olya, petro))
        }

        withDb { db ->
            val children = db.children().observeChildren().first()
            assertEquals(listOf("Оля" to Avatar.RABBIT, "Петро" to Avatar.ROCKET), children.map { it.name to it.avatar })
            assertEquals(children.map { it.id }.toSet(), db.children().observeAssignments().first().map { it.childId }.toSet())
        }
    }

    @Test
    fun `assigning replaces the previous children of a text`() {
        withDb { db ->
            val olya = db.children().saveChild(ChildProfile(name = "Оля", avatar = Avatar.RABBIT))
            val petro = db.children().saveChild(ChildProfile(name = "Петро", avatar = Avatar.ROCKET))
            val text = db.texts().save(SavedText(title = "Казка", rawText = "Жив собі кіт.", createdAt = 1))
            db.children().assignText(text, setOf(olya))
            db.children().assignText(text, setOf(petro))
            assertEquals(listOf(TextAssignment(petro, text)), db.children().observeAssignments().first())
        }
    }

    @Test
    fun `deleting a profile removes its assignments but keeps the texts`() {
        withDb { db ->
            val olya = db.children().saveChild(ChildProfile(name = "Оля", avatar = Avatar.RABBIT))
            val petro = db.children().saveChild(ChildProfile(name = "Петро", avatar = Avatar.ROCKET))
            val text = db.texts().save(SavedText(title = "Казка", rawText = "Жив собі кіт.", createdAt = 1))
            db.children().assignText(text, setOf(olya, petro))

            db.children().deleteChild(olya)

            assertEquals(listOf("Петро"), db.children().observeChildren().first().map { it.name })
            assertEquals(listOf(TextAssignment(petro, text)), db.children().observeAssignments().first())
            assertEquals(listOf("Казка"), db.texts().observeAll().first().map { it.title })
        }
    }

    @Test
    fun `deleting a text removes its assignments`() {
        withDb { db ->
            val olya = db.children().saveChild(ChildProfile(name = "Оля", avatar = Avatar.RABBIT))
            val text = db.texts().save(SavedText(title = "Казка", rawText = "Жив собі кіт.", createdAt = 1))
            db.children().assignText(text, setOf(olya))

            db.texts().delete(text)

            assertEquals(emptyList<TextAssignment>(), db.children().observeAssignments().first())
        }
    }

    @Test
    fun `texts saved before profiles existed survive the upgrade`() {
        // A database as version 1 of the app left it: only the texts table.
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE `texts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`rawText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
            db.execSQL("INSERT INTO texts (title, rawText, createdAt) VALUES ('Казка', 'Жив собі кіт.', 1)")
            db.version = 1
        }

        withDb { db ->
            val text = db.texts().observeAll().first().single()
            assertEquals("Казка", text.title)
            val olya = db.children().saveChild(ChildProfile(name = "Оля", avatar = Avatar.RABBIT))
            db.children().assignText(text.id, setOf(olya))
            assertEquals(listOf(TextAssignment(olya, text.id)), db.children().observeAssignments().first())
        }
    }
}
