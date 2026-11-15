package com.gefolgwar.readwithkid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private lateinit var repository: InMemoryTextRepository
    private var nowMs = 1_000L
    private lateinit var vm: LibraryViewModel

    private val state get() = vm.uiState.value
    private val editor get() = state.editor!!

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = InMemoryTextRepository()
        vm = LibraryViewModel(repository, clockMs = { nowMs })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun addText(raw: String) {
        vm.newText()
        vm.updateRawText(raw)
        vm.save()
    }

    @Test
    fun `library starts empty with editor closed`() {
        assertEquals(emptyList<SavedText>(), state.texts)
        assertNull(state.editor)
    }

    @Test
    fun `typed text is saved with an automatic title and editor closes`() {
        vm.newText()
        assertEquals("", editor.title)
        vm.updateRawText("Мама мила раму.\n")
        assertEquals("Мама мила раму", editor.title)
        vm.save()

        assertNull(state.editor)
        val saved = state.texts.single()
        assertEquals("Мама мила раму", saved.title)
        assertEquals("Мама мила раму.", saved.rawText)
        assertEquals(1_000L, saved.createdAt)
        assertTrue(saved.id > 0)
    }

    @Test
    fun `long text is titled by its first five words`() {
        addText("Мама мила раму. Тато читав книжку.")
        assertEquals("Мама мила раму. Тато читав…", state.texts.single().title)
    }

    @Test
    fun `editor warns about digits, abbreviations and symbols`() {
        vm.newText()
        vm.updateRawText("Мама мила раму.")
        assertTrue(editor.warnings.isEmpty)

        vm.updateRawText("У 2024 році США & ЗСУ.")
        assertEquals(listOf("2024"), editor.warnings.digits)
        assertEquals(listOf("США", "ЗСУ"), editor.warnings.abbreviations)
        assertEquals(listOf("&"), editor.warnings.symbols)
    }

    @Test
    fun `cannot save without words`() {
        vm.newText()
        assertFalse(editor.canSave)
        vm.save()
        assertNotNull(state.editor)

        vm.updateRawText(" — … ")
        assertFalse(editor.canSave)
        vm.save()
        assertTrue(state.texts.isEmpty())

        vm.updateRawText("Слово")
        assertTrue(editor.canSave)
    }

    @Test
    fun `cancel closes editor without saving`() {
        vm.newText()
        vm.updateRawText("Текст")
        vm.cancelEdit()
        assertNull(state.editor)
        assertTrue(state.texts.isEmpty())
    }

    @Test
    fun `editing updates the existing text and its title, and keeps its creation time`() {
        addText("Старий текст.")
        val original = state.texts.single()
        assertEquals("Старий текст", original.title)

        nowMs = 5_000
        vm.edit(original)
        assertEquals("Старий текст.", editor.rawText)
        vm.updateRawText("Новий текст.")
        assertEquals("Новий текст", editor.title)
        vm.save()

        val updated = state.texts.single()
        assertEquals(original.id, updated.id)
        assertEquals("Новий текст", updated.title)
        assertEquals("Новий текст.", updated.rawText)
        assertEquals(1_000L, updated.createdAt)
    }

    @Test
    fun `newest texts come first`() {
        addText("Перший.")
        nowMs = 2_000
        addText("Другий.")
        assertEquals(listOf("Другий", "Перший"), state.texts.map { it.title })
    }

    @Test
    fun `delete removes the text`() {
        addText("Перший.")
        addText("Другий.")
        vm.delete(state.texts.first { it.title == "Перший" })
        assertEquals(listOf("Другий"), state.texts.map { it.title })
    }

    @Test
    fun `texts are read from the repository, so a new viewmodel sees saved texts`() {
        addText("Жив собі кіт.")
        val afterRestart = LibraryViewModel(repository)
        assertEquals(listOf("Жив собі кіт"), afterRestart.uiState.value.texts.map { it.title })
        assertEquals("Жив собі кіт", runBlocking { repository.observeAll().first() }.single().title)
    }

    @Test
    fun `any saved text can be loaded into a reading session`() {
        addText("Один два.")
        addText("Мама мила раму. Тато читав книжку.")
        val engine = FakeVoskEngine()
        val reading = ReadingSessionViewModel({ engine })
        engine.simulateModelLoaded()

        val chosen = state.texts.first { it.rawText.startsWith("Мама") }
        reading.updateText(chosen.rawText)
        reading.start()

        assertEquals(SessionState.READING, reading.uiState.value.sessionState)
        assertEquals(6, reading.uiState.value.text.words.size)
    }

    // --- language of the text ---

    @Test
    fun `language is suggested from the alphabet and saved with the text`() {
        vm.newText()
        assertEquals(TextLanguage.UK, editor.language)
        assertNull(editor.detectedLanguage)

        vm.updateRawText("Die kleine Katze läuft über die Straße.")
        assertEquals(TextLanguage.DE, editor.language)
        assertNull(editor.chosenLanguage)

        vm.updateRawText("Мама мила раму.")
        assertEquals(TextLanguage.UK, editor.language)

        vm.updateRawText("Der Hund heißt Бровко und spielt gern.")
        assertEquals(TextLanguage.DE, editor.language)

        vm.save()
        assertEquals(TextLanguage.DE, state.texts.single().language)
    }

    @Test
    fun `parent can change the suggested language`() {
        vm.newText()
        vm.updateRawText("Anna")
        assertEquals(TextLanguage.DE, editor.language)

        vm.chooseLanguage(TextLanguage.UK)
        vm.updateRawText("Anna und Max")
        assertEquals(TextLanguage.UK, editor.language)
        vm.save()

        val saved = state.texts.single()
        assertEquals(TextLanguage.UK, saved.language)
        vm.edit(saved)
        assertEquals(TextLanguage.UK, editor.chosenLanguage)
    }

    @Test
    fun `editing a text whose language matches its alphabet keeps following the alphabet`() {
        addText("Die Katze schläft.")
        vm.edit(state.texts.single())
        assertEquals(TextLanguage.DE, editor.language)
        assertNull(editor.chosenLanguage)
    }

    @Test
    fun `a German text is read with the German model`() {
        addText("Die Katze schläft.")
        val engines = mutableMapOf<TextLanguage, FakeVoskEngine>()
        val reading = ReadingSessionViewModel({ FakeVoskEngine().also { engine -> engines[it] = engine } })

        val text = state.texts.single()
        reading.updateText(text.rawText, text.language)
        engines.getValue(TextLanguage.DE).simulateModelLoaded()
        reading.start()

        assertEquals(TextLanguage.DE, reading.uiState.value.language)
        assertEquals(SessionState.READING, reading.uiState.value.sessionState)
        assertTrue(engines.getValue(TextLanguage.DE).started)
    }
}
