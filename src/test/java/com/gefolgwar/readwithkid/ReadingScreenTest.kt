package com.gefolgwar.readwithkid

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The child makes the reading text smaller or bigger, step by step. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val chosen = mutableListOf<Int>()

    private fun string(id: Int) = compose.activity.getString(id)

    private fun open(textSize: Int) = compose.setContent {
        ReadWithKidTheme {
            val text = TextPreparation.prepare("Мама мила раму.")
            ReadingScreen(
                ReadingUiState(SessionState.READING, text = text, wordStatuses = List(text.words.size) { WordStatus.UNREAD }),
                "Мама мила раму",
                Magnets.forText(1),
                syllableColors = false,
                onWordTap = {},
                onRetryWord = {},
                onCloseWordCheck = {},
                onPause = {},
                onResume = {},
                onFinish = {},
                onLeave = {},
                textSize = textSize,
                onTextSizeChange = { chosen += it }
            )
        }
    }

    @Test
    fun `the text gets one step bigger or smaller`() {
        open(textSize = 30)
        compose.onNodeWithContentDescription(string(R.string.text_bigger)).performClick()
        compose.onNodeWithContentDescription(string(R.string.text_smaller)).performClick()
        assertEquals(listOf(34, 26), chosen)
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
