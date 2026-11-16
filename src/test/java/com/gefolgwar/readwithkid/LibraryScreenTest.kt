package com.gefolgwar.readwithkid

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
    fun `a text card tells its word count, and today's words only once something was read today`() {
        var stats by mutableStateOf(TextStats(wordCount = 3, wordsReadToday = 0))
        compose.setContent {
            ReadWithKidTheme {
                LibraryScreen(listOf(text), Child(1, "Оля"), {}, {}, {}, {}, {}, {}, textStats = mapOf(text.id to stats))
            }
        }
        val words = compose.activity.resources.getQuantityString(R.plurals.text_word_count, 3, 3)
        compose.onNodeWithText(words).assertIsDisplayed()

        stats = stats.copy(wordsReadToday = 2)
        compose.onNodeWithText("$words · ${compose.activity.getString(R.string.text_read_today, 2)}").assertIsDisplayed()
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
    fun `the progress chart shows from two readings on and tells the first and latest score`() {
        // A minute each: the score is the correct words.
        fun reading(id: Long, correct: Int) =
            ReadingRecord(id, text.id, text.title, id, ReadingResult(40, correct, 60_000, emptyList(), emptyList()))
        var readings by mutableStateOf(emptyList<ReadingRecord>())
        compose.setContent {
            ReadWithKidTheme {
                HistoryScreen(TextHistory(text, readings), onShow = {}, onDelete = null, onBack = {})
            }
        }
        val chart = compose.activity.getString(R.string.history_chart_description, 21, 34)
        compose.onNodeWithContentDescription(chart).assertDoesNotExist()

        readings = listOf(reading(1, 21))
        compose.onNodeWithContentDescription(chart).assertDoesNotExist()

        // Newest first, as the history lists them.
        readings = listOf(reading(3, 34), reading(2, 30), reading(1, 21))
        compose.onNodeWithContentDescription(chart).assertIsDisplayed()
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
        assertNormsOpenAndClose()
    }

    @Test
    fun `the main screen opens the reading norms in child mode too`() {
        open(childMode = true)
        assertNormsOpenAndClose()
    }

    /** The norms give speed and words per day by grade, and say in small print that they are only a guide. */
    private fun assertNormsOpenAndClose() {
        compose.onNodeWithContentDescription(string(R.string.norms_title)).performClick()
        compose.onNodeWithText(string(R.string.norms_speed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.norms_daily)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.norms_grade, 1)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.norms_note)).assertExists()
        compose.onNodeWithText(string(R.string.norms_close)).performClick()
        compose.onNodeWithText(string(R.string.norms_speed)).assertDoesNotExist()
    }

    @Test
    fun `the main screen tells the words read today across all texts`() {
        val other = text.copy(id = 2, title = "Кіт")
        var stats by mutableStateOf(mapOf(text.id to TextStats(3, 0), other.id to TextStats(5, 0)))
        compose.setContent {
            ReadWithKidTheme {
                LibraryScreen(listOf(text, other), Child(1, "Оля"), {}, {}, {}, {}, {}, {}, textStats = stats)
            }
        }
        fun today(words: Int) = compose.activity.resources.getQuantityString(R.plurals.words_read_today, words, words)
        compose.onNodeWithText(today(0)).assertIsDisplayed()

        stats = mapOf(text.id to TextStats(3, 3), other.id to TextStats(5, 4))
        compose.onNodeWithText(today(7)).assertIsDisplayed()
    }

    @Test
    fun `the typed product is given as the answer`() {
        open(childMode = true, question = UnlockQuestion(7, 8))
        compose.onNodeWithText(compose.activity.getString(R.string.unlock_question, 7, 8)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.unlock_answer)).performTextInput("56")
        compose.onNodeWithText(string(R.string.unlock_confirm)).performClick()
        assertEquals(listOf("answer 56"), events)
    }

    // --- children list ---

    private val olya = Child(1, "Оля")
    private val unnamed = Child(2)

    private fun openChildren(
        childMode: Boolean = false,
        childrenOpen: Boolean = false,
        children: List<Child> = listOf(olya, unnamed)
    ) = compose.setContent {
        ReadWithKidTheme {
            LibraryScreen(
                listOf(text), olya, {}, {}, {}, {}, {}, {},
                childMode = childMode,
                children = children,
                childrenOpen = childrenOpen,
                onOpenChildren = { events += "children" },
                onSwitchChild = { events += "switch ${it.id}" },
                onAddChild = { events += "add '$it'" },
                onRenameChild = { child, name -> events += "rename ${child.id} '$name'" },
                onDeleteChild = { events += "delete ${it.id}" }
            )
        }
    }

    @Test
    fun `in parent mode tapping the active child opens the children list`() {
        openChildren()
        compose.onNodeWithText(olya.name).performClick()
        assertEquals(listOf("children"), events)
    }

    @Test
    fun `in child mode the active child is not tappable`() {
        openChildren(childMode = true)
        compose.onNodeWithText(olya.name).assertHasNoClickAction()
    }

    @Test
    fun `choosing a child in the list switches to them`() {
        openChildren(childrenOpen = true)
        compose.onNodeWithText(string(R.string.children_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.child_name_placeholder)).performClick()
        assertEquals(listOf("switch 2"), events)
    }

    @Test
    fun `a new child is added with the typed name, or with none`() {
        openChildren(childrenOpen = true)
        compose.onNodeWithText(string(R.string.add_child)).performClick()
        compose.onNodeWithText(string(R.string.child_name_placeholder)).performTextInput("Петрик")
        compose.onNodeWithText(string(R.string.add_child_confirm)).performClick()

        compose.onNodeWithText(string(R.string.add_child)).performClick()
        compose.onNodeWithText(string(R.string.add_child_confirm)).performClick()
        assertEquals(listOf("add 'Петрик'", "add ''"), events)
    }

    @Test
    fun `cancelling the name goes back to the children list`() {
        openChildren(childrenOpen = true)
        compose.onNodeWithText(string(R.string.add_child)).performClick()
        compose.onNodeWithText(string(R.string.cancel)).performClick()
        compose.onNodeWithText(string(R.string.children_title)).assertIsDisplayed()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `a child is renamed starting from their name, and cancelling goes back to the list`() {
        openChildren(childrenOpen = true)
        compose.onAllNodesWithContentDescription(string(R.string.rename_child))[0].performClick()
        compose.onNodeWithText(string(R.string.cancel)).performClick()
        compose.onNodeWithText(string(R.string.children_title)).assertIsDisplayed()

        compose.onAllNodesWithContentDescription(string(R.string.rename_child))[0].performClick()
        compose.onNodeWithText(string(R.string.rename_child)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.child_name_placeholder)).assertTextContains(olya.name).performTextReplacement("Ольга")
        compose.onNodeWithText(string(R.string.save)).performClick()
        assertEquals(listOf("rename 1 'Ольга'"), events)
    }

    @Test
    fun `deleting a child asks first, naming the child and their texts and results`() {
        openChildren(childrenOpen = true)
        compose.onAllNodesWithContentDescription(string(R.string.delete_child))[1].performClick()
        compose.onNodeWithText(string(R.string.delete_child_message_unnamed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cancel)).performClick()
        compose.onNodeWithText(string(R.string.children_title)).assertIsDisplayed()

        compose.onAllNodesWithContentDescription(string(R.string.delete_child))[0].performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.delete_child_message, olya.name)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.delete)).performClick()
        assertEquals(listOf("delete 1"), events)
    }

    @Test
    fun `the last child can be renamed but not deleted`() {
        openChildren(childrenOpen = true, children = listOf(olya))
        compose.onNodeWithContentDescription(string(R.string.rename_child)).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.delete_child)).assertDoesNotExist()
    }
}
