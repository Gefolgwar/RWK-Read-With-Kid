package com.gefolgwar.readwithkid

import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Duration

/**
 * Renders every screen on the JVM into build/screens/ to look at the design without a phone.
 * Run: gradlew testDebugUnitTest --tests "*ScreenshotsTest*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "uk-w412dp-h915dp-xhdpi", sdk = [35])
class ScreenshotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val story = "Лисичка знайшла на стежці червоне яблуко. Вона понесла його додому, щоб поділитися з братиком. " +
        "Братик дуже зрадів і сказав: «Дякую, сестричко!» Увечері вони разом пили чай з яблучним пирогом."
    /** The child the texts belong to, and a child who has no name yet, as in a new library. */
    private val olya = Child(2, "Оля")
    private val unnamed = Child(1)
    private val texts = listOf(
        SavedText(1, TextPreparation.autoTitle(story), story, 0, TextLanguage.UK, childId = 2),
        SavedText(2, "Der kleine Igel", "Der kleine Igel sucht im Garten nach Äpfeln. Er findet drei und trägt sie nach Hause.", 0, TextLanguage.DE, childId = 2),
        SavedText(3, "Мама мила раму", "Мама мила раму. Тато читав книжку. Оля малювала сонце.", 0, TextLanguage.UK, childId = 2, sample = true),
        SavedText(4, "Kater Paul", "Kater Paul schläft gern in der Sonne. Am Abend spielt er mit einem Ball.", 0, TextLanguage.DE, childId = 2)
    )
    /** The story and the German hedgehog were read today, the others not. */
    private val stats = texts.associate { text ->
        val today = mapOf(1L to 27, 2L to 15)[text.id] ?: 0
        text.id to TextStats(TextPreparation.prepare(text.rawText).words.size, today, lastReadAt = if (today > 0) 1_763_000_000_000 else null)
    }
    private val result = ReadingResult(
        totalWords = 34,
        correctWords = 31,
        durationMs = 52_000,
        skippedWords = listOf("стежці"),
        retriedWords = listOf("поділитися", "сестричко"),
        helpedWords = listOf("пирогом"),
        correctedWords = listOf("червоне"),
        uncertainWords = listOf("бабусі"),
        errorSentences = listOf(
            ReadSentence(
                "Лисичка знайшла на стежці червоне яблуко.".split(" ").mapIndexed { i, word ->
                    if (i == 3) ReadWord(word, WordStatus.MISREAD, "стежки") else ReadWord(word, WordStatus.READ)
                },
                tooManyErrors = false
            ),
            ReadSentence(
                "Вона понесла його додому, щоб поділитися з братиком.".split(" ").mapIndexed { i, word ->
                    when (i) {
                        3 -> ReadWord(word, WordStatus.MISREAD, "додом")
                        in 4..6 -> ReadWord(word, WordStatus.MISREAD)
                        2 -> ReadWord(word, WordStatus.MISREAD, "йо")
                        else -> ReadWord(word, WordStatus.READ)
                    }
                },
                tooManyErrors = true
            )
        )
    )

    /** [dark] null follows the system setting, as the app does. */
    private fun shot(name: String, dark: Boolean? = false, content: @Composable (Modifier) -> Unit) {
        compose.setContent {
            ReadWithKidTheme(dark = dark ?: isSystemInDarkTheme()) {
                // The same insets handling as the app's Scaffold
                Scaffold { padding ->
                    content(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(16.dp))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        save(name, compose.activity.window.decorView)
    }

    private fun save(name: String, view: View) {
        val dir = File("build/screens").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun reading(state: SessionState = SessionState.READING): ReadingUiState {
        val prepared = TextPreparation.prepare(story)
        val count = prepared.words.size
        // The first sentence read with a misread word, the second with too many mistakes, the third being read
        val statuses = List(count) { i ->
            when (i) {
                3, in 8..12 -> WordStatus.MISREAD
                in 0..13 -> WordStatus.READ
                14, 15 -> WordStatus.CONFIRMED
                16 -> WordStatus.TENTATIVE
                else -> WordStatus.UNREAD
            }
        }
        return ReadingUiState(
            sessionState = state,
            rawText = story,
            text = prepared,
            wordStatuses = statuses,
            heard = mapOf(3 to "стежки", 8 to "йо", 9 to "додом"),
            sentenceStatuses = listOf(SentenceStatus.READ, SentenceStatus.TOO_MANY_ERRORS, SentenceStatus.UNREAD, SentenceStatus.UNREAD),
            currentWordIndex = 17,
            currentSentence = 2,
            helpAvailable = true
        )
    }

    @Test
    fun library() = shot("1-library") {
        LibraryScreen(texts, olya, {}, {}, {}, {}, {}, {}, it, textStats = stats)
    }

    @Test
    fun libraryEmpty() = shot("1b-library-empty") {
        LibraryScreen(emptyList(), unnamed, {}, {}, {}, {}, {}, {}, it)
    }

    /** Locked for the child: no adding, no text menu, no settings; the closed lock asks a grown-up's question. */
    @Test
    fun libraryChildMode() = shot("1c-library-child-mode") {
        LibraryScreen(texts, olya, {}, {}, {}, {}, {}, {}, it, childMode = true, textStats = stats)
    }

    @Test
    fun unlockQuestion() = unlockShot("1d-unlock-question", dark = false)

    /**
     * The question is its own window, so it is captured alone. With a text field in a dialog the compose rule never
     * gets idle under native graphics, so the dialog is shown on the activity directly and the main looper is run.
     */
    private fun unlockShot(name: String, dark: Boolean) {
        compose.activity.setContent {
            ReadWithKidTheme(dark = dark) {
                LibraryScreen(texts, olya, {}, {}, {}, {}, {}, {}, childMode = true, unlockQuestion = UnlockQuestion(7, 8))
            }
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        save(name, ShadowDialog.getLatestDialog().window!!.decorView)
    }

    @Test
    fun children() = childrenShot("1e-children", dark = false)

    @Test
    fun childrenDark() = childrenShot("d1e-children", dark = true)

    /** The list of children opened from the avatar, the active one marked: the dialog is its own window, so it is captured alone. */
    private fun childrenShot(name: String, dark: Boolean) {
        compose.setContent {
            ReadWithKidTheme(dark = dark) {
                LibraryScreen(
                    texts, olya, {}, {}, {}, {}, {}, {},
                    children = listOf(unnamed, olya, Child(3, "Петрик")),
                    childrenOpen = true
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        save(name, ShadowDialog.getLatestDialog().window!!.decorView)
    }

    @Test
    fun renameChild() = renameChildShot("1f-rename-child", dark = false)

    @Test
    fun renameChildDark() = renameChildShot("d1f-rename-child", dark = true)

    /** The new name starts from the old one. A text field in a dialog: shown on the activity directly, as the unlock question. */
    private fun renameChildShot(name: String, dark: Boolean) {
        compose.activity.setContent {
            ReadWithKidTheme(dark = dark) {
                ChildNameDialog(R.string.rename_child, R.string.save, initialName = olya.name, onConfirm = {}, onDismiss = {})
            }
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        save(name, ShadowDialog.getLatestDialog().window!!.decorView)
    }

    @Test
    fun deleteChild() = deleteChildShot("1g-delete-child", dark = false)

    @Test
    fun deleteChildDark() = deleteChildShot("d1g-delete-child", dark = true)

    /** Deleting a child names them and warns that their texts and results go too. */
    private fun deleteChildShot(name: String, dark: Boolean) {
        compose.setContent {
            ReadWithKidTheme(dark = dark) {
                DeleteChildDialog(olya, onDelete = {}, onDismiss = {})
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        save(name, ShadowDialog.getLatestDialog().window!!.decorView)
    }

    @Test
    fun historyChildMode() = shot("6b-history-child-mode") {
        HistoryScreen(
            TextHistory(texts[0], listOf(ReadingRecord(1, 1, texts[0].title, 1_763_000_000_000, result))),
            onShow = {}, onDelete = null, onBack = {}, modifier = it
        )
    }

    @Test
    fun editor() = shot("2-editor") {
        TextEditorScreen(TextEditorState(rawText = "Кіт Мурко має 3 кошенят. Вони граються м'ячем."), {}, {}, {}, {}, it)
    }

    /** Saving a text with digits, abbreviations and signs: the dialog is its own window, so it is captured alone. */
    @Test
    fun editorHardParts() {
        compose.setContent {
            ReadWithKidTheme {
                TextEditorScreen(TextEditorState(rawText = "У 2024 році 5 учнів з США & ЗСУ отримали 100% балів."), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.save)).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        save("2c-editor-hard-parts", ShadowDialog.getLatestDialog().window!!.decorView)
    }

    @Test
    fun ready() = shot("3-ready") {
        ReadyToReadScreen(texts[0].title, Magnets.forText(1), reading(SessionState.READY), ModelState.Installed, {}, {}, {}, {}, {}, it)
    }

    @Test
    fun readyDownloading() = shot("3b-ready-downloading") {
        ReadyToReadScreen(
            texts[0].title,
            Magnets.forText(1),
            reading(SessionState.MODEL_MISSING),
            ModelState.Downloading(52_000_000, 133_000_000),
            {}, {}, {}, {}, {}, it
        )
    }

    @Test
    fun readingScreen() = shot("4-reading") {
        ReadingScreen(reading(), texts[0].title, Magnets.forText(1), syllableColors = false, {}, {}, {}, {}, {}, it)
    }

    @Test
    fun readingSyllables() = shot("4b-reading-syllables") {
        ReadingScreen(reading(SessionState.PAUSED), texts[0].title, Magnets.forText(1), syllableColors = true, {}, {}, {}, {}, {}, it)
    }

    @Test
    fun readingBiggestText() = shot("4c-reading-biggest-text") {
        ReadingScreen(
            reading(), texts[0].title, Magnets.forText(1), syllableColors = false, {}, {}, {}, {}, {}, it,
            textSize = ReadingTextSizes.last()
        )
    }

    @Test
    fun resultScreen() = shot("5-result") {
        ResultScreen(result, texts[0].title, Magnets.forText(1), onClose = {}, modifier = it)
    }

    /** Several readings of a text, newest first, each with its score. */
    private val readings = listOf(
        ReadingRecord(1, 1, texts[0].title, 1_763_000_000_000, result),
        ReadingRecord(2, 1, texts[0].title, 1_762_500_000_000, result.copy(correctWords = 28, durationMs = 61_000)),
        ReadingRecord(3, 1, texts[0].title, 1_762_000_000_000, result.copy(correctWords = 25, durationMs = 70_000)),
        ReadingRecord(4, 1, texts[0].title, 1_761_500_000_000, result.copy(correctWords = 19, durationMs = 105_000))
    )

    @Test
    fun history() = shot("6-history") {
        HistoryScreen(TextHistory(texts[0], readings), {}, {}, {}, it)
    }

    @Test
    fun historyDark() = shot("d6-history", dark = true) {
        HistoryScreen(TextHistory(texts[0], readings), {}, {}, {}, it)
    }

    @Test
    fun readingNorms() = normsShot("6c-reading-norms", dark = false)

    @Test
    fun readingNormsDark() = normsShot("d6c-reading-norms", dark = true)

    @Test
    @Config(qualifiers = "de-w412dp-h915dp-xhdpi", fontScale = 1.3f)
    fun readingNormsGermanLargeFont() = normsShot("6d-reading-norms-de-font130", dark = false)

    @Test
    fun readingGuide() = guideShot("4d-reading-guide", dark = false)

    @Test
    fun readingGuideDark() = guideShot("d4d-reading-guide", dark = true)

    /** The help button on the reading screen opens what to do while reading, captured alone like the norms. */
    private fun guideShot(name: String, dark: Boolean) {
        compose.setContent {
            ReadWithKidTheme(dark = dark) {
                ReadingScreen(reading(), texts[0].title, Magnets.forText(1), syllableColors = false, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.reading_guide_title)).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        save(name, ShadowDialog.getLatestDialog().window!!.decorView)
    }

    /** The info button in the history header opens the norms: the dialog is its own window, so it is captured alone. */
    private fun normsShot(name: String, dark: Boolean) {
        compose.setContent {
            ReadWithKidTheme(dark = dark) {
                HistoryScreen(TextHistory(texts[0], readings), onShow = {}, onDelete = null, onBack = {})
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.norms_title)).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        save(name, ShadowDialog.getLatestDialog().window!!.decorView)
    }

    @Test
    @Config(qualifiers = "de-w412dp-h915dp-xhdpi", fontScale = 1.3f)
    fun readingGermanLargeFont() = shot("8-reading-de-font130") {
        ReadingScreen(reading(SessionState.PAUSED), texts[0].title, Magnets.forText(1), syllableColors = false, {}, {}, {}, {}, {}, it)
    }

    @Test
    @Config(qualifiers = "de-w412dp-h915dp-xhdpi", fontScale = 1.3f)
    fun libraryGermanLargeFont() = shot("9-library-de-font130") {
        // The longest placeholder at the largest font
        LibraryScreen(texts, unnamed, {}, {}, {}, {}, {}, {}, it, textStats = stats)
    }

    @Test
    fun settings() = shot("7-settings") {
        SettingsScreen(UiLanguage.SYSTEM, {}, syllableColors = true, {}, textWordsOnly = false, {}, {}, {}, {}, it)
    }

    @Test
    @Config(qualifiers = "uk-w412dp-h915dp-night-xhdpi")
    fun libraryFollowsSystemDarkMode() = shot("d0-library-system-night", dark = null) {
        LibraryScreen(texts, olya, {}, {}, {}, {}, {}, {}, it, textStats = stats)
    }

    /** The editor while the keyboard is open: the keyboard's insets are given to the window as the phone does. */
    @Test
    fun editorWithKeyboard() {
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent {
            ReadWithKidTheme {
                Scaffold { padding ->
                    TextEditorScreen(
                        TextEditorState(rawText = "Кіт Мурко має 3 кошенят. Вони граються м'ячем."),
                        {}, {}, {}, {},
                        Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(16.dp)
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            val keyboard = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 1_100))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, keyboard)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        save("2b-editor-keyboard", compose.activity.window.decorView)
    }

    /** The launcher icon: the adaptive layers in a circle mask, and the themed (monochrome) layer. */
    @Test
    fun launcherIcon() {
        val context = compose.activity
        val size = 432
        val bitmap = Bitmap.createBitmap(size * 2 + 48, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(239, 242, 247))
        val path = android.graphics.Path().apply { addCircle(size / 2f, size / 2f, size / 2f * 0.92f, android.graphics.Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!.apply { setBounds(-size / 4, -size / 4, size * 5 / 4, size * 5 / 4) }.draw(canvas)
        canvas.restore()
        canvas.translate(size + 48f, 0f)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawColor(android.graphics.Color.rgb(220, 228, 245))
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome)!!.apply {
            setTint(android.graphics.Color.rgb(29, 60, 140))
            setBounds(-size / 4, -size / 4, size * 5 / 4, size * 5 / 4)
        }.draw(canvas)
        canvas.restore()
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "0-icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun libraryDark() = shot("d1-library", dark = true) {
        LibraryScreen(texts, olya, {}, {}, {}, {}, {}, {}, it, textStats = stats)
    }

    @Test
    fun libraryEmptyDark() = shot("d1b-library-empty", dark = true) {
        LibraryScreen(emptyList(), unnamed, {}, {}, {}, {}, {}, {}, it)
    }

    @Test
    fun libraryChildModeDark() = shot("d1c-library-child-mode", dark = true) {
        LibraryScreen(emptyList(), olya, {}, {}, {}, {}, {}, {}, it, childMode = true)
    }

    @Test
    fun unlockQuestionDark() = unlockShot("d1d-unlock-question", dark = true)

    @Test
    fun editorDark() = shot("d2-editor", dark = true) {
        TextEditorScreen(TextEditorState(rawText = "Кіт Мурко має 3 кошенят. Вони граються м'ячем."), {}, {}, {}, {}, it)
    }

    @Test
    fun readingDark() = shot("d4-reading", dark = true) {
        ReadingScreen(reading(), texts[0].title, Magnets.forText(1), syllableColors = false, {}, {}, {}, {}, {}, it)
    }

    @Test
    fun readingSyllablesDark() = shot("d4b-reading-syllables", dark = true) {
        ReadingScreen(reading(SessionState.PAUSED), texts[0].title, Magnets.forText(1), syllableColors = true, {}, {}, {}, {}, {}, it)
    }

    @Test
    fun resultDark() = shot("d5-result", dark = true) {
        ResultScreen(result, texts[0].title, Magnets.forText(1), onClose = {}, modifier = it)
    }

    @Test
    fun settingsDark() = shot("d7-settings", dark = true) {
        SettingsScreen(UiLanguage.SYSTEM, {}, syllableColors = true, {}, textWordsOnly = false, {}, {}, {}, {}, it)
    }

    @Test
    fun readyDark() = shot("d3-ready", dark = true) {
        ReadyToReadScreen(texts[0].title, Magnets.forText(1), reading(SessionState.READY), ModelState.Installed, {}, {}, {}, {}, {}, it)
    }
}
