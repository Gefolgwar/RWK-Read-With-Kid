package com.gefolgwar.readwithkid

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every screen on the JVM into build/screens/ to look at the design without a phone.
 * Run: gradlew testDebugUnitTest --tests "*ScreenshotsTest*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w412dp-h915dp-xhdpi", sdk = [35])
class ScreenshotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val story = "Лисичка знайшла на стежці червоне яблуко. Вона понесла його додому, щоб поділитися з братиком. " +
        "Братик дуже зрадів і сказав: «Дякую, сестричко!» Увечері вони разом пили чай з яблучним пирогом."
    private val texts = listOf(
        SavedText(1, TextPreparation.autoTitle(story), story, 0, TextLanguage.UK),
        SavedText(2, "Der kleine Igel", "Der kleine Igel sucht im Garten nach Äpfeln. Er findet drei und trägt sie nach Hause.", 0, TextLanguage.DE),
        SavedText(3, "Мама мила раму", "Мама мила раму. Тато читав книжку. Оля малювала сонце.", 0, TextLanguage.UK),
        SavedText(4, "Kater Paul", "Kater Paul schläft gern in der Sonne. Am Abend spielt er mit einem Ball.", 0, TextLanguage.DE)
    )
    private val result = ReadingResult(
        totalWords = 34,
        correctWords = 31,
        durationMs = 52_000,
        skippedWords = listOf("стежці"),
        retriedWords = listOf("поділитися", "сестричко"),
        helpedWords = listOf("пирогом")
    )

    private fun shot(name: String, content: @Composable (Modifier) -> Unit) {
        compose.setContent {
            ReadWithKidTheme {
                Scaffold { padding -> content(Modifier.fillMaxSize().padding(padding).padding(16.dp)) }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun reading(state: SessionState = SessionState.READING): ReadingUiState {
        val prepared = TextPreparation.prepare(story)
        val count = prepared.words.size
        val statuses = List(count) { i ->
            when {
                i == 4 -> WordStatus.SKIPPED
                i < 11 -> WordStatus.CONFIRMED
                else -> WordStatus.UNREAD
            }
        }
        return ReadingUiState(
            sessionState = state,
            rawText = story,
            text = prepared,
            wordStatuses = statuses,
            currentWordIndex = 11,
            helpAvailable = true
        )
    }

    @Test
    fun library() = shot("1-library") {
        LibraryScreen(texts, {}, {}, {}, {}, {}, {}, it)
    }

    @Test
    fun libraryEmpty() = shot("1b-library-empty") {
        LibraryScreen(emptyList(), {}, {}, {}, {}, {}, {}, it)
    }

    @Test
    fun editor() = shot("2-editor") {
        TextEditorScreen(TextEditorState(rawText = "Кіт Мурко має 3 кошенят. Вони граються м'ячем."), {}, {}, {}, {}, it)
    }

    @Test
    fun ready() = shot("3-ready") {
        ReadyToReadScreen(texts[0].title, reading(SessionState.READY), ModelState.Installed, {}, {}, {}, {}, {}, it)
    }

    @Test
    fun readyDownloading() = shot("3b-ready-downloading") {
        ReadyToReadScreen(
            texts[0].title,
            reading(SessionState.MODEL_MISSING),
            ModelState.Downloading(52_000_000, 133_000_000),
            {}, {}, {}, {}, {}, it
        )
    }

    @Test
    fun readingScreen() = shot("4-reading") {
        ReadingScreen(reading(), syllableColors = false, {}, {}, {}, {}, {}, {}, {}, it)
    }

    @Test
    fun readingSyllables() = shot("4b-reading-syllables") {
        ReadingScreen(reading(SessionState.PAUSED), syllableColors = true, {}, {}, {}, {}, {}, {}, {}, it)
    }

    @Test
    fun resultScreen() = shot("5-result") {
        ResultScreen(result, onClose = {}, modifier = it)
    }

    @Test
    fun history() = shot("6-history") {
        HistoryScreen(
            TextHistory(
                texts[0],
                listOf(
                    ReadingRecord(1, 1, texts[0].title, 1_763_000_000_000, result),
                    ReadingRecord(2, 1, texts[0].title, 1_762_500_000_000, result.copy(correctWords = 25, durationMs = 70_000))
                )
            ),
            {}, {}, {}, it
        )
    }

    @Test
    fun settings() = shot("7-settings") {
        SettingsScreen(UiLanguage.SYSTEM, {}, syllableColors = true, {}, {}, {}, {}, it)
    }
}
