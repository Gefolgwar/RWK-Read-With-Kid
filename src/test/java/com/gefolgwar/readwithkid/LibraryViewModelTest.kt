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
import java.util.Calendar
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private lateinit var repository: InMemoryTextRepository
    private lateinit var readings: InMemoryReadingRepository
    private lateinit var children: InMemoryChildRepository
    private lateinit var session: FakeLibrarySession
    private var nowMs = 1_000L
    private lateinit var vm: LibraryViewModel

    private val state get() = vm.uiState.value
    private val editor get() = state.editor!!

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = InMemoryTextRepository()
        readings = InMemoryReadingRepository()
        children = InMemoryChildRepository(repository, readings)
        session = FakeLibrarySession()
        vm = newViewModel()
    }

    private fun newViewModel() = LibraryViewModel(repository, readings, children, session, clockMs = { nowMs })

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
        val afterRestart = LibraryViewModel(repository, readings, children, session)
        assertEquals(listOf("Жив собі кіт"), afterRestart.uiState.value.texts.map { it.title })
        assertEquals("Жив собі кіт", runBlocking { repository.observeAll(1).first() }.single().title)
    }

    // --- children ---

    private fun addChild(name: String): Child {
        val child = Child(name = name)
        return child.copy(id = runBlocking { children.save(child) })
    }

    private fun saveText(title: String, child: Child, createdAt: Long = 1) {
        runBlocking { repository.save(SavedText(title = title, rawText = "$title.", createdAt = createdAt, childId = child.id)) }
    }

    @Test
    fun `a new library belongs to the first child, who has no name yet`() {
        assertEquals(Child(1, ""), state.activeChild)
    }

    @Test
    fun `the library lists only the active child's texts`() {
        val first = state.activeChild!!
        val olya = addChild("Оля")
        saveText("Кіт", first)
        saveText("Katze", olya)
        saveText("Пес", first, createdAt = 2)

        assertEquals(listOf("Пес", "Кіт"), state.texts.map { it.title })

        session.activeChildId = olya.id
        assertEquals(olya, newViewModel().uiState.value.activeChild)
        assertEquals(listOf("Katze"), newViewModel().uiState.value.texts.map { it.title })
    }

    @Test
    fun `a new text is saved for the active child`() {
        val olya = addChild("Оля")
        session.activeChildId = olya.id
        vm = newViewModel()

        addText("Мама мила раму.")

        assertEquals(listOf(olya.id), state.texts.map { it.childId })
        assertEquals(emptyList<SavedText>(), runBlocking { repository.observeAll(1).first() })
    }

    @Test
    fun `an edited text stays with its child`() {
        val olya = addChild("Оля")
        session.activeChildId = olya.id
        vm = newViewModel()
        addText("Старий текст.")

        vm.edit(state.texts.single())
        vm.updateRawText("Новий текст.")
        vm.save()

        assertEquals(listOf("Новий текст" to olya.id), state.texts.map { it.title to it.childId })
    }

    @Test
    fun `when the active child no longer exists, the first child is active`() {
        val olya = addChild("Оля")
        session.activeChildId = olya.id
        vm = newViewModel()
        assertEquals(olya, state.activeChild)

        runBlocking { children.delete(olya.id) }
        assertEquals(Child(1, ""), state.activeChild)

        session.activeChildId = 42
        assertEquals(Child(1, ""), newViewModel().uiState.value.activeChild)
    }

    @Test
    fun `the children list shows all children and closes again`() {
        val olya = addChild("Оля")
        assertEquals(listOf(Child(1, ""), olya), state.children)

        vm.openChildren()
        assertTrue(state.childrenOpen)
        vm.closeChildren()
        assertFalse(state.childrenOpen)
    }

    @Test
    fun `switching the child shows their texts and is remembered across restarts`() {
        val first = state.activeChild!!
        val olya = addChild("Оля")
        saveText("Кіт", first)
        saveText("Katze", olya)

        vm.openChildren()
        vm.switchChild(olya)

        assertFalse(state.childrenOpen)
        assertEquals(olya, state.activeChild)
        assertEquals(listOf("Katze"), state.texts.map { it.title })
        assertEquals(olya, newViewModel().uiState.value.activeChild)

        vm.switchChild(first)
        assertEquals(listOf("Кіт"), state.texts.map { it.title })
        assertEquals(first, newViewModel().uiState.value.activeChild)
    }

    @Test
    fun `an added child becomes active with an empty library`() {
        saveText("Кіт", state.activeChild!!)

        vm.openChildren()
        vm.addChild("  Оля ")

        assertFalse(state.childrenOpen)
        val olya = state.children.last()
        assertEquals("Оля", olya.name)
        assertEquals(olya, state.activeChild)
        assertEquals(emptyList<SavedText>(), state.texts)
        assertEquals(olya, newViewModel().uiState.value.activeChild)
    }

    @Test
    fun `a child added without a name is not named yet`() {
        vm.addChild(" ")
        assertEquals(listOf("", ""), state.children.map { it.name })
        assertEquals(state.children.last(), state.activeChild)
    }

    @Test
    fun `in child mode the children list does not open and children cannot be switched or added`() {
        val first = state.activeChild!!
        val olya = addChild("Оля")
        vm.lock()

        vm.openChildren()
        assertFalse(state.childrenOpen)
        vm.switchChild(olya)
        assertEquals(first, state.activeChild)
        vm.addChild("Петрик")
        assertEquals(listOf(first, olya), state.children)
        assertEquals(first, state.activeChild)
        assertNull(session.activeChildId)
    }

    @Test
    fun `renaming a child updates the list and the avatar, and an empty name brings the placeholder back`() {
        val olya = addChild("Оля")
        session.activeChildId = olya.id
        vm = newViewModel()

        vm.renameChild(olya, "  Ольга ")
        assertEquals(Child(olya.id, "Ольга"), state.activeChild)
        assertEquals(listOf("", "Ольга"), state.children.map { it.name })

        vm.renameChild(state.activeChild!!, " ")
        assertEquals(Child(olya.id, ""), state.activeChild)
        assertEquals(listOf("", ""), state.children.map { it.name })
    }

    @Test
    fun `deleting a child deletes their texts and readings, and keeps the others'`() {
        val first = state.activeChild!!
        val olya = addChild("Оля")
        saveText("Кіт", first)
        saveText("Katze", olya)
        val kit = runBlocking { repository.observeAll(first.id).first() }.single()
        val katze = runBlocking { repository.observeAll(olya.id).first() }.single()
        saveReading(kit, readAt = 1)
        saveReading(katze, readAt = 2)

        vm.openChildren()
        vm.deleteChild(olya)

        assertTrue(state.childrenOpen)
        assertEquals(listOf(first), state.children)
        assertEquals(first, state.activeChild)
        assertEquals(listOf("Кіт"), state.texts.map { it.title })
        assertEquals(emptyList<SavedText>(), runBlocking { repository.observeAll(olya.id).first() })
        assertEquals(listOf(kit.id), readings.all.map { it.textId })
    }

    @Test
    fun `deleting the active child opens another child's library, also after a restart`() {
        val first = state.activeChild!!
        val olya = addChild("Оля")
        saveText("Кіт", first)
        vm.switchChild(olya)

        vm.deleteChild(olya)

        assertEquals(first, state.activeChild)
        assertEquals(listOf("Кіт"), state.texts.map { it.title })
        assertEquals(first.id, session.activeChildId)
        assertEquals(first, newViewModel().uiState.value.activeChild)
    }

    @Test
    fun `the last child cannot be deleted`() {
        val only = state.activeChild!!
        saveText("Кіт", only)

        vm.deleteChild(only)

        assertEquals(listOf(only), state.children)
        assertEquals(only, state.activeChild)
        assertEquals(listOf("Кіт"), state.texts.map { it.title })
    }

    @Test
    fun `in child mode children cannot be renamed or deleted`() {
        val first = state.activeChild!!
        val olya = addChild("Оля")
        saveText("Katze", olya)
        vm.lock()

        vm.renameChild(first, "Марко")
        vm.deleteChild(olya)

        assertEquals(listOf(first, olya), state.children)
        assertEquals(listOf("Katze"), runBlocking { repository.observeAll(olya.id).first() }.map { it.title })
    }

    @Test
    fun `a renamed active child is shown with the new name`() {
        runBlocking { children.save(Child(1, "Оля")) }
        assertEquals(Child(1, "Оля"), state.activeChild)
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

    // --- history of readings ---

    private fun saveReading(text: SavedText, readAt: Long, correct: Int = 6): ReadingRecord {
        val record = ReadingRecord(
            textId = text.id,
            textTitle = text.title,
            readAt = readAt,
            result = ReadingResult(6, correct, 60_000, emptyList(), emptyList())
        )
        return record.copy(id = runBlocking { readings.saveReading(record) })
    }

    @Test
    fun `a text's history shows its readings, newest first, and follows new ones`() {
        addText("Мама мила раму.")
        addText("Кіт спав.")
        val (cat, mama) = state.texts
        saveReading(mama, readAt = 1)
        saveReading(cat, readAt = 2)
        saveReading(mama, readAt = 3)

        vm.openHistory(mama)
        assertEquals(mama, state.history!!.text)
        assertEquals(listOf(3L, 1L), state.history!!.readings.map { it.readAt })
        saveReading(mama, readAt = 4)
        assertEquals(listOf(4L, 3L, 1L), state.history!!.readings.map { it.readAt })

        vm.closeHistory()
        assertNull(state.history)
    }

    @Test
    fun `a reading in the history opens its result and closes back to the history`() {
        addText("Мама мила раму.")
        val text = state.texts.single()
        val first = saveReading(text, readAt = 1, correct = 4)
        saveReading(text, readAt = 2)

        vm.openHistory(text)
        assertNull(state.history!!.shown)
        vm.showReading(first)
        assertEquals(4, state.history!!.shown!!.result.correctWords)
        vm.closeReading()
        assertNull(state.history!!.shown)
        assertEquals(2, state.history!!.readings.size)
    }

    @Test
    fun `a deleted reading leaves the history`() {
        addText("Мама мила раму.")
        val text = state.texts.single()
        val first = saveReading(text, readAt = 1)
        val second = saveReading(text, readAt = 2)

        vm.openHistory(text)
        vm.showReading(first)
        vm.deleteReading(first)
        assertEquals(listOf(second), state.history!!.readings)
        assertNull(state.history!!.shown)
        assertEquals(listOf(second), readings.all)
    }

    @Test
    fun `the progress chart has a score per reading, oldest first, and loses a deleted reading's`() {
        addText("Мама мила раму.")
        val text = state.texts.single()
        // Each reading takes a minute, so its score is its correct words.
        saveReading(text, readAt = 1, correct = 3)
        val middle = saveReading(text, readAt = 2, correct = 4)
        saveReading(text, readAt = 3, correct = 6)

        vm.openHistory(text)
        assertEquals(listOf(3, 4, 6), state.history!!.scores)
        vm.deleteReading(middle)
        assertEquals(listOf(3, 6), state.history!!.scores)
    }

    @Test
    fun `another text's history starts on its list`() {
        addText("Мама мила раму.")
        addText("Кіт спав.")
        val (cat, mama) = state.texts
        vm.openHistory(mama)
        vm.showReading(saveReading(mama, readAt = 1))
        vm.closeHistory()
        vm.openHistory(cat)
        assertEquals(cat, state.history!!.text)
        assertNull(state.history!!.shown)
        assertEquals(emptyList<ReadingRecord>(), state.history!!.readings)
    }

    // --- word counts on text cards ---

    /** Local time on the phone, ms since the epoch. */
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, day, hour, minute)
        }.timeInMillis

    @Test
    fun `a text card counts the words of the text as the reading session splits them`() {
        val raw = "Мама — мила раму! Тато, читав книжку… «Добре», сказав він."
        addText(raw)
        val text = state.texts.single()
        assertEquals(TextPreparation.prepare(raw).words.size, state.textStats.getValue(text.id).wordCount)
        assertEquals(9, state.textStats.getValue(text.id).wordCount)
    }

    @Test
    fun `words read today sum the correct words of today's readings of that text only`() {
        nowMs = at(day = 16, hour = 10)
        addText("Мама мила раму.")
        addText("Кіт спав.")
        val (cat, mama) = state.texts
        saveReading(mama, readAt = at(day = 15, hour = 23, minute = 59), correct = 5)
        saveReading(mama, readAt = at(day = 16, hour = 0), correct = 3)
        saveReading(mama, readAt = at(day = 16, hour = 9), correct = 2)
        saveReading(cat, readAt = at(day = 15, hour = 12), correct = 2)

        assertEquals(5, state.textStats.getValue(mama.id).wordsReadToday)
        assertEquals(0, state.textStats.getValue(cat.id).wordsReadToday)
    }

    @Test
    fun `words read today follow saved and deleted readings`() {
        nowMs = at(day = 16, hour = 10)
        addText("Мама мила раму.")
        val text = state.texts.single()
        assertEquals(0, state.textStats.getValue(text.id).wordsReadToday)

        val first = saveReading(text, readAt = at(day = 16, hour = 9), correct = 3)
        saveReading(text, readAt = at(day = 16, hour = 9, minute = 30), correct = 2)
        assertEquals(5, state.textStats.getValue(text.id).wordsReadToday)

        vm.deleteReading(first)
        assertEquals(2, state.textStats.getValue(text.id).wordsReadToday)
    }

    @Test
    fun `a text card knows when that text was read last, from its history`() {
        addText("Мама мила раму.")
        addText("Кіт спав.")
        val (cat, mama) = state.texts
        assertNull(state.textStats.getValue(mama.id).lastReadAt)

        saveReading(mama, readAt = at(day = 12, hour = 9))
        val latest = saveReading(mama, readAt = at(day = 14, hour = 18))
        saveReading(mama, readAt = at(day = 13, hour = 9))
        assertEquals(at(day = 14, hour = 18), state.textStats.getValue(mama.id).lastReadAt)
        assertNull(state.textStats.getValue(cat.id).lastReadAt)

        vm.deleteReading(latest)
        assertEquals(at(day = 13, hour = 9), state.textStats.getValue(mama.id).lastReadAt)
    }

    @Test
    fun `an edited sample text is the parent's own text`() {
        val sample = SavedText(title = "Кіт", rawText = "Кіт спав.", createdAt = 1, childId = 1, sample = true)
        runBlocking { repository.save(sample) }
        vm.edit(state.texts.single())
        vm.updateRawText("Кіт спав на вікні.")
        vm.save()
        assertFalse(state.texts.single().sample)
    }

    // --- parent mode and child mode ---

    /** Gives the factors of the unlock questions in order. */
    private class FakeRandom(vararg factors: Int) : Random() {
        private val factors = ArrayDeque(factors.toList())
        override fun nextBits(bitCount: Int): Int = error("only factors are drawn")
        override fun nextInt(from: Int, until: Int): Int = factors.removeFirst().also { assertTrue(it in from until until) }
    }

    private fun lockedViewModel(vararg factors: Int): LibraryViewModel {
        session.childMode = true
        return LibraryViewModel(repository, readings, children, session, clockMs = { nowMs }, random = FakeRandom(*factors))
    }

    @Test
    fun `first launch is in parent mode`() {
        assertFalse(state.childMode)
        assertNull(state.unlockQuestion)
    }

    @Test
    fun `one tap locks, and the mode survives a restart`() {
        vm.lock()
        assertTrue(state.childMode)
        assertTrue(session.childMode)
        assertTrue(newViewModel().uiState.value.childMode)
    }

    @Test
    fun `the correct product unlocks`() {
        vm = lockedViewModel(7, 8)
        vm.askToUnlock()
        assertEquals(UnlockQuestion(7, 8), state.unlockQuestion)

        vm.answerUnlock(" 56 ")
        assertFalse(state.childMode)
        assertFalse(session.childMode)
        assertNull(state.unlockQuestion)
    }

    @Test
    fun `a wrong answer keeps child mode and closes the question`() {
        vm = lockedViewModel(7, 8, 3, 4)
        vm.askToUnlock()
        vm.answerUnlock("54")
        assertTrue(state.childMode)
        assertTrue(session.childMode)
        assertNull(state.unlockQuestion)

        vm.askToUnlock()
        vm.answerUnlock("")
        assertTrue(state.childMode)
    }

    @Test
    fun `a new question is asked each time`() {
        vm = lockedViewModel(7, 8, 3, 9)
        vm.askToUnlock()
        vm.dismissUnlock()
        assertNull(state.unlockQuestion)
        assertTrue(state.childMode)
        vm.askToUnlock()
        assertEquals(UnlockQuestion(3, 9), state.unlockQuestion)
        vm.answerUnlock("56")
        assertTrue(state.childMode)
    }

    @Test
    fun `nothing is asked in parent mode`() {
        vm.askToUnlock()
        assertNull(state.unlockQuestion)
    }

    @Test
    fun `settings open only in parent mode`() {
        vm.openSettings()
        assertTrue(state.settingsOpen)
        vm.closeSettings()
        assertFalse(state.settingsOpen)

        vm.lock()
        vm.openSettings()
        assertFalse(state.settingsOpen)
    }

    @Test
    fun `in child mode texts and readings cannot be added, changed or deleted`() {
        addText("Мама мила раму.")
        val text = state.texts.single()
        val reading = saveReading(text, readAt = 1)
        vm.lock()

        vm.newText()
        assertNull(state.editor)
        vm.edit(text)
        assertNull(state.editor)
        vm.delete(text)
        assertEquals(listOf(text), state.texts)

        vm.openHistory(text)
        vm.deleteReading(reading)
        assertEquals(listOf(reading), state.history!!.readings)
        assertEquals(listOf(reading), readings.all)
    }

    @Test
    fun `an editor left open is not saved in child mode`() {
        vm.newText()
        vm.updateRawText("Мама мила раму.")
        vm.lock()
        vm.save()
        assertTrue(state.texts.isEmpty())
    }

    @Test
    fun `in child mode texts can be read and their history viewed`() {
        addText("Мама мила раму.")
        val text = state.texts.single()
        val reading = saveReading(text, readAt = 1)
        vm.lock()

        vm.openHistory(text)
        assertEquals(listOf(reading), state.history!!.readings)
        vm.showReading(reading)
        assertEquals(reading, state.history!!.shown)
    }
}
