package com.gefolgwar.readwithkid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** The child's start screen: pick an avatar → see your texts → read one. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChildHomeViewModelTest {

    private lateinit var texts: InMemoryTextRepository
    private lateinit var children: InMemoryChildRepository
    private lateinit var profiles: ProfilesViewModel
    private lateinit var library: LibraryViewModel
    private lateinit var vm: ChildHomeViewModel
    private var nowMs = 1_000L

    private val state get() = vm.uiState.value

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        texts = InMemoryTextRepository()
        children = InMemoryChildRepository()
        profiles = ProfilesViewModel(children)
        library = LibraryViewModel(texts, children, clockMs = { nowMs++ })
        vm = ChildHomeViewModel(texts, children)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun addChild(name: String, avatar: Avatar): ChildProfile {
        profiles.newProfile()
        profiles.updateName(name)
        profiles.updateAvatar(avatar)
        profiles.save()
        return profiles.uiState.value.children.last()
    }

    private fun addText(title: String, raw: String, vararg forChildren: ChildProfile) {
        library.newText()
        library.updateTitle(title)
        library.updateRawText(raw)
        forChildren.forEach(library::toggleChild)
        library.save()
    }

    @Test
    fun `start screen shows every child's avatar and no texts`() {
        assertTrue(state.children.isEmpty())
        val olya = addChild("Оля", Avatar.RABBIT)
        val petro = addChild("Петро", Avatar.ROCKET)
        addText("Казка", "Жив собі кіт.", olya)

        assertEquals(listOf(olya, petro), state.children)
        assertNull(state.child)
        assertTrue(state.texts.isEmpty())
    }

    @Test
    fun `picking an avatar shows only that child's texts, newest first`() {
        val olya = addChild("Оля", Avatar.RABBIT)
        val petro = addChild("Петро", Avatar.ROCKET)
        addText("Для Олі", "Один.", olya)
        addText("Для обох", "Два.", olya, petro)
        addText("Для Петра", "Три.", petro)
        addText("Нікому", "Чотири.")

        vm.select(olya)
        assertEquals(olya, state.child)
        assertEquals(listOf("Для обох", "Для Олі"), state.texts.map { it.title })

        vm.back()
        assertNull(state.child)
        vm.select(petro)
        assertEquals(listOf("Для Петра", "Для обох"), state.texts.map { it.title })
    }

    @Test
    fun `a child with no texts sees an empty list`() {
        val olya = addChild("Оля", Avatar.RABBIT)
        vm.select(olya)
        assertEquals(olya, state.child)
        assertTrue(state.texts.isEmpty())
    }

    @Test
    fun `assignments made while the child's screen is open show up`() {
        val olya = addChild("Оля", Avatar.RABBIT)
        addText("Казка", "Жив собі кіт.")
        vm.select(olya)
        assertTrue(state.texts.isEmpty())

        library.edit(library.uiState.value.texts.single())
        library.toggleChild(olya)
        library.save()

        assertEquals(listOf("Казка"), state.texts.map { it.title })
    }

    @Test
    fun `a text card leads to reading that text`() {
        val olya = addChild("Оля", Avatar.RABBIT)
        addText("Казка", "Мама мила раму. Тато читав книжку.", olya)
        val engine = FakeVoskEngine()
        val reading = ReadingSessionViewModel(engine)
        engine.simulateModelLoaded()

        vm.select(olya)
        reading.updateText(state.texts.single().rawText)
        reading.start()

        assertEquals(SessionState.READING, reading.uiState.value.sessionState)
        assertEquals(6, reading.uiState.value.text.words.size)
    }
}
