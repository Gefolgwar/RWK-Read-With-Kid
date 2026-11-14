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
    private lateinit var children: InMemoryChildRepository
    private var nowMs = 1_000L
    private lateinit var vm: LibraryViewModel

    private val state get() = vm.uiState.value
    private val editor get() = state.editor!!

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = InMemoryTextRepository()
        children = InMemoryChildRepository()
        vm = LibraryViewModel(repository, children, clockMs = { nowMs })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun addText(title: String, raw: String) {
        vm.newText()
        vm.updateTitle(title)
        vm.updateRawText(raw)
        vm.save()
    }

    @Test
    fun `library starts empty with editor closed`() {
        assertEquals(emptyList<SavedText>(), state.texts)
        assertNull(state.editor)
    }

    @Test
    fun `typed text is saved with title and editor closes`() {
        vm.newText()
        assertEquals("", editor.title)
        vm.updateTitle("  Казка ")
        vm.updateRawText("Мама мила раму.\n")
        vm.save()

        assertNull(state.editor)
        val saved = state.texts.single()
        assertEquals("Казка", saved.title)
        assertEquals("Мама мила раму.", saved.rawText)
        assertEquals(1_000L, saved.createdAt)
        assertTrue(saved.id > 0)
    }

    @Test
    fun `editor previews sentence and word split before saving`() {
        vm.newText()
        vm.updateRawText("Мама мила раму. Тато читав книжку.")
        assertEquals(2, editor.preview.sentences.size)
        assertEquals(6, editor.preview.words.size)
        assertTrue(state.texts.isEmpty())
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
    fun `cannot save without title or without words`() {
        vm.newText()
        vm.updateRawText("Мама мила раму.")
        assertFalse(editor.canSave)
        vm.save()
        assertNotNull(state.editor)

        vm.updateTitle("Назва")
        vm.updateRawText(" — … ")
        assertFalse(editor.canSave)
        vm.save()
        assertTrue(state.texts.isEmpty())

        vm.updateRawText("Слово")
        assertTrue(editor.canSave)
    }

    @Test
    fun `imported txt fills text and takes title from file name`() {
        vm.newText()
        vm.importTxt("Казка про кота.txt", "Жив собі кіт.")
        assertEquals("Казка про кота", editor.title)
        assertEquals("Жив собі кіт.", editor.rawText)
    }

    @Test
    fun `import keeps title the parent already typed`() {
        vm.newText()
        vm.updateTitle("Моя назва")
        vm.importTxt("file.txt", "Жив собі кіт.")
        assertEquals("Моя назва", editor.title)
    }

    @Test
    fun `cancel closes editor without saving`() {
        vm.newText()
        vm.updateTitle("Назва")
        vm.updateRawText("Текст")
        vm.cancelEdit()
        assertNull(state.editor)
        assertTrue(state.texts.isEmpty())
    }

    @Test
    fun `editing updates the existing text and keeps its creation time`() {
        addText("Стара", "Старий текст.")
        val original = state.texts.single()

        nowMs = 5_000
        vm.edit(original)
        assertEquals("Стара", editor.title)
        assertEquals("Старий текст.", editor.rawText)
        vm.updateTitle("Нова")
        vm.updateRawText("Новий текст.")
        vm.save()

        val updated = state.texts.single()
        assertEquals(original.id, updated.id)
        assertEquals("Нова", updated.title)
        assertEquals("Новий текст.", updated.rawText)
        assertEquals(1_000L, updated.createdAt)
    }

    @Test
    fun `newest texts come first`() {
        addText("Перший", "Один.")
        nowMs = 2_000
        addText("Другий", "Два.")
        assertEquals(listOf("Другий", "Перший"), state.texts.map { it.title })
    }

    @Test
    fun `delete removes the text`() {
        addText("Перший", "Один.")
        addText("Другий", "Два.")
        vm.delete(state.texts.first { it.title == "Перший" })
        assertEquals(listOf("Другий"), state.texts.map { it.title })
    }

    @Test
    fun `texts are read from the repository, so a new viewmodel sees saved texts`() {
        addText("Казка", "Жив собі кіт.")
        val afterRestart = LibraryViewModel(repository, children)
        assertEquals(listOf("Казка"), afterRestart.uiState.value.texts.map { it.title })
        assertEquals("Казка", runBlocking { repository.observeAll().first() }.single().title)
    }

    private fun addChild(name: String): ChildProfile = runBlocking {
        val id = children.saveChild(ChildProfile(name = name, avatar = Avatar.CAT))
        ChildProfile(id, name, Avatar.CAT)
    }

    @Test
    fun `a new text can be assigned to one or several children`() {
        val olya = addChild("Оля")
        val petro = addChild("Петро")
        assertEquals(listOf(olya, petro), state.children)

        vm.newText()
        assertEquals(emptySet<Long>(), editor.childIds)
        vm.updateTitle("Казка")
        vm.updateRawText("Жив собі кіт.")
        vm.toggleChild(olya)
        vm.toggleChild(petro)
        assertEquals(setOf(olya.id, petro.id), editor.childIds)
        vm.save()

        val text = state.texts.single()
        assertEquals(listOf(olya, petro), state.assignedTo[text.id])
    }

    @Test
    fun `a text is not assigned to anyone by default`() {
        addChild("Оля")
        addText("Казка", "Жив собі кіт.")
        assertNull(state.assignedTo[state.texts.single().id])
    }

    @Test
    fun `editing a text shows and changes who it is assigned to`() {
        val olya = addChild("Оля")
        val petro = addChild("Петро")
        vm.newText()
        vm.updateTitle("Казка")
        vm.updateRawText("Жив собі кіт.")
        vm.toggleChild(olya)
        vm.save()

        vm.edit(state.texts.single())
        assertEquals(setOf(olya.id), editor.childIds)
        vm.toggleChild(olya)
        vm.toggleChild(petro)
        vm.save()

        assertEquals(listOf(petro), state.assignedTo[state.texts.single().id])
    }

    @Test
    fun `cancelling the editor keeps the old assignment`() {
        val olya = addChild("Оля")
        vm.newText()
        vm.updateTitle("Казка")
        vm.updateRawText("Жив собі кіт.")
        vm.toggleChild(olya)
        vm.save()

        vm.edit(state.texts.single())
        vm.toggleChild(olya)
        vm.cancelEdit()

        assertEquals(listOf(olya), state.assignedTo[state.texts.single().id])
    }

    @Test
    fun `any saved text can be loaded into a reading session`() {
        addText("Перший", "Один два.")
        addText("Другий", "Мама мила раму. Тато читав книжку.")
        val engine = FakeVoskEngine()
        val reading = ReadingSessionViewModel(engine)
        engine.simulateModelLoaded()

        val chosen = state.texts.first { it.title == "Другий" }
        reading.updateText(chosen.rawText)
        reading.start()

        assertEquals(SessionState.READING, reading.uiState.value.sessionState)
        assertEquals(6, reading.uiState.value.text.words.size)
    }
}
