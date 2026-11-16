package com.gefolgwar.readwithkid

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.DateFormat
import java.util.Date

/** The lock in the top bar, and what the library offers in parent mode and child mode. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val text = SavedText(1, "Мама мила раму", "Мама мила раму.", 0, TextLanguage.UK, childId = 1)
    private val events = mutableListOf<String>()

    private fun string(id: Int) = compose.activity.getString(id)

    private fun open(childMode: Boolean, question: UnlockQuestion? = null) = compose.setContent {
        ReadWithKidTheme {
            LibraryScreen(
                listOf(text),
                Child(1, "Оля"),
                onAdd = {},
                onRead = { events += "read" },
                onEdit = {},
                onDelete = {},
                onHistory = { events += "history" },
                onSettings = {},
                childMode = childMode,
                unlockQuestion = question,
                onLock = { events += "lock" },
                onUnlock = { events += "unlock" },
                onAnswerUnlock = { events += "answer $it" }
            )
        }
    }

    @Test
    fun `parent mode offers adding, the text menu and settings, and the open lock locks`() {
        open(childMode = false)
        compose.onNodeWithText(string(R.string.add_text), useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription(string(R.string.more_actions)).assertExists()
        compose.onNodeWithContentDescription(string(R.string.settings_title)).assertExists()

        compose.onNodeWithContentDescription(string(R.string.lock_parent_mode)).performClick()
        assertEquals(listOf("lock"), events)
    }

    @Test
    fun `child mode hides adding, the text menu and settings, but texts can be read and their history opened`() {
        open(childMode = true)
        compose.onNodeWithText(string(R.string.add_text), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.more_actions)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.settings_title)).assertDoesNotExist()

        compose.onNodeWithText(text.title).performClick()
        compose.onNodeWithContentDescription(string(R.string.history_title)).performClick()
        compose.onNodeWithContentDescription(string(R.string.lock_child_mode)).performClick()
        assertEquals(listOf("read", "history", "unlock"), events)
    }

    @Test
    fun `readings can be deleted only when deleting is offered`() {
        val reading = ReadingRecord(1, text.id, text.title, 0, ReadingResult(3, 3, 60_000, emptyList(), emptyList()))
        var deleteOffered by mutableStateOf(true)
        compose.setContent {
            ReadWithKidTheme {
                HistoryScreen(TextHistory(text, listOf(reading)), onShow = {}, onDelete = if (deleteOffered) ({}) else null, onBack = {})
            }
        }
        compose.onNodeWithContentDescription(string(R.string.delete)).assertIsDisplayed()
        deleteOffered = false
        compose.onNodeWithContentDescription(string(R.string.delete)).assertDoesNotExist()
    }

    @Test
    fun `a history row tells its score, accuracy and duration instead of stars`() {
        val readAt = 1_763_000_000_000
        // 31 of 34 words in 52 seconds: 36 words correct per minute, 91 %
        val reading = ReadingRecord(1, text.id, text.title, readAt, ReadingResult(34, 31, 52_000, emptyList(), emptyList()))
        compose.setContent {
            ReadWithKidTheme {
                HistoryScreen(TextHistory(text, listOf(reading)), onShow = {}, onDelete = null, onBack = {})
            }
        }
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(readAt))
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.history_reading_description, date, 36, 91, "0:52"))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.result_stars, 3, ReadingResult.MAX_STARS))
            .assertDoesNotExist()
    }

    @Test
    fun `the info button opens the reading norms in child mode too, and they close`() {
        compose.setContent {
            ReadWithKidTheme {
                HistoryScreen(TextHistory(text, emptyList()), onShow = {}, onDelete = null, onBack = {})
            }
        }
        compose.onNodeWithContentDescription(string(R.string.norms_title)).performClick()
        compose.onNodeWithText(string(R.string.norms_ukraine_speed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.norms_germany_source)).assertExists()
        compose.onNodeWithText(string(R.string.norms_close)).performClick()
        compose.onNodeWithText(string(R.string.norms_ukraine_speed)).assertDoesNotExist()
    }

    @Test
    fun `the typed product is given as the answer`() {
        open(childMode = true, question = UnlockQuestion(7, 8))
        compose.onNodeWithText(compose.activity.getString(R.string.unlock_question, 7, 8)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.unlock_answer)).performTextInput("56")
        compose.onNodeWithText(string(R.string.unlock_confirm)).performClick()
        assertEquals(listOf("answer 56"), events)
    }
}
