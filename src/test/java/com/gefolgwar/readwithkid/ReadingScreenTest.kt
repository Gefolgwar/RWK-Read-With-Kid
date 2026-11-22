package com.gefolgwar.readwithkid

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The child makes the reading text smaller or bigger, step by step, before and during reading. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val chosen = mutableListOf<Int>()

    private fun string(id: Int) = compose.activity.getString(id)

    private val events = mutableListOf<String>()

    private fun open(textSize: Int = 30, sessionState: SessionState = SessionState.READING) = compose.setContent {
        ReadWithKidTheme {
            val text = TextPreparation.prepare("Мама мила раму.")
            ReadingScreen(
                ReadingUiState(sessionState, text = text, wordStatuses = List(text.words.size) { WordStatus.UNREAD }),
                "Мама мила раму",
                Magnets.forText(1),
                syllableColors = false,
                onWordTap = {},
                onPause = { events += "pause" },
                onResume = { events += "resume" },
                onFinish = {},
                onLeave = {},
                textSize = textSize,
                onTextSizeChange = { chosen += it }
            )
        }
    }

    @Test
    fun `before reading starts the text gets one step bigger or smaller too`() {
        compose.setContent {
            ReadWithKidTheme {
                ReadyToReadScreen(
                    "Мама мила раму",
                    Magnets.forText(1),
                    ReadingUiState(SessionState.READY, rawText = "Мама мила раму.", text = TextPreparation.prepare("Мама мила раму.")),
                    ModelState.Installed,
                    onStart = {},
                    onRetryModel = {},
                    onConfirmModel = {},
                    onOpenAppSettings = {},
                    onBack = {},
                    textSize = 30,
                    onTextSizeChange = { chosen += it }
                )
            }
        }
        compose.onNodeWithContentDescription(string(R.string.text_bigger)).performClick()
        compose.onNodeWithContentDescription(string(R.string.text_smaller)).performClick()
        assertEquals(listOf(34, 26), chosen)
    }

    @Test
    fun `the text gets one step bigger or smaller`() {
        open(textSize = 30)
        compose.onNodeWithContentDescription(string(R.string.text_bigger)).performClick()
        compose.onNodeWithContentDescription(string(R.string.text_smaller)).performClick()
        assertEquals(listOf(34, 26), chosen)
    }

    @Test
    fun `the help button explains what to do, and reading waits while it is open and goes on after`() {
        open()
        compose.onNodeWithContentDescription(string(R.string.reading_guide_title)).performClick()
        compose.onNodeWithText(string(R.string.reading_guide_read_aloud)).assertIsDisplayed()
        assertEquals(listOf("pause"), events)

        compose.onNodeWithText(string(R.string.reading_guide_close)).performClick()
        compose.onNodeWithText(string(R.string.reading_guide_read_aloud)).assertDoesNotExist()
        assertEquals(listOf("pause", "resume"), events)
    }

    @Test
    fun `the help opened during a pause leaves the pause on`() {
        open(sessionState = SessionState.PAUSED)
        compose.onNodeWithContentDescription(string(R.string.reading_guide_title)).performClick()
        compose.onNodeWithText(string(R.string.reading_guide_close)).performClick()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `the smallest size cannot get smaller`() {
        open(textSize = ReadingTextSizes.first())
        compose.onNodeWithContentDescription(string(R.string.text_smaller)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(string(R.string.text_bigger)).assertIsEnabled()
    }

    @Test
    fun `the biggest size cannot get bigger`() {
        open(textSize = ReadingTextSizes.last())
        compose.onNodeWithContentDescription(string(R.string.text_bigger)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(string(R.string.text_smaller)).assertIsEnabled()
    }
}
