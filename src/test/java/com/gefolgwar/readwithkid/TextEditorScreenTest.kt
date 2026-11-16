package com.gefolgwar.readwithkid

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Saving a text with parts that speech recognition handles poorly first explains what to fix. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextEditorScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var saves = 0

    // Without arguments the text is not formatted, as stringResource(id) does: tips contain a plain "%".
    private fun string(id: Int, vararg args: Any) =
        if (args.isEmpty()) compose.activity.getString(id) else compose.activity.getString(id, *args)

    private fun open(raw: String) = compose.setContent {
        ReadWithKidTheme {
            TextEditorScreen(TextEditorState(rawText = raw), {}, {}, onSave = { saves++ }, onCancel = {})
        }
    }

    @Test
    fun `ordinary text is saved at once`() {
        open("Мама мила раму.")
        compose.onNodeWithText(string(R.string.save)).performClick()

        assertEquals(1, saves)
        compose.onNodeWithText(string(R.string.hard_parts_title)).assertDoesNotExist()
    }

    @Test
    fun `text with hard parts shows what to fix in this text`() {
        open("У 2024 році США & ЗСУ.")
        compose.onNodeWithText(string(R.string.save)).performClick()

        assertEquals(0, saves)
        compose.onNodeWithText(string(R.string.hard_parts_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.hard_parts_digits, "2024")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.hard_parts_abbreviations, "США, ЗСУ")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.hard_parts_symbols, "&")).assertIsDisplayed()
    }

    @Test
    fun `only the kinds of problems the text has are explained`() {
        open("Кіт має 3 кошенят.")
        compose.onNodeWithText(string(R.string.save)).performClick()

        compose.onNodeWithText(string(R.string.hard_parts_digits_tip)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.hard_parts_abbreviations_tip)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.hard_parts_symbols_tip)).assertDoesNotExist()
    }

    @Test
    fun `fixing the text goes back to editing without saving`() {
        open("Кіт має 3 кошенят.")
        compose.onNodeWithText(string(R.string.save)).performClick()
        compose.onNodeWithText(string(R.string.hard_parts_fix)).performClick()

        assertEquals(0, saves)
        compose.onNodeWithText(string(R.string.hard_parts_title)).assertDoesNotExist()
    }

    @Test
    fun `the advice can be skipped and the text saved anyway`() {
        open("Кіт має 3 кошенят.")
        compose.onNodeWithText(string(R.string.save)).performClick()
        compose.onNodeWithText(string(R.string.hard_parts_save_anyway)).performClick()

        assertEquals(1, saves)
    }
}
