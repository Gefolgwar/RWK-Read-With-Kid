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
}
