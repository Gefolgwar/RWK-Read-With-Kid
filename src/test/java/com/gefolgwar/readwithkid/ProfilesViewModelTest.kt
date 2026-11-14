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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {

    private lateinit var texts: InMemoryTextRepository
    private lateinit var children: InMemoryChildRepository
    private lateinit var vm: ProfilesViewModel

    private val state get() = vm.uiState.value
    private val editor get() = state.editor!!

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        texts = InMemoryTextRepository()
        children = InMemoryChildRepository()
        vm = ProfilesViewModel(children)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun addChild(name: String, avatar: Avatar) {
        vm.newProfile()
        vm.updateName(name)
        vm.updateAvatar(avatar)
        vm.save()
    }

    @Test
    fun `new profile is saved with name and avatar`() {
        assertTrue(state.children.isEmpty())
        vm.newProfile()
        assertEquals("", editor.name)
        assertEquals(Avatar.entries.first(), editor.avatar)

        vm.updateName("  Оля ")
        vm.updateAvatar(Avatar.RABBIT)
        vm.save()

        assertNull(state.editor)
        val child = state.children.single()
        assertEquals("Оля", child.name)
        assertEquals(Avatar.RABBIT, child.avatar)
        assertTrue(child.id > 0)
    }

    @Test
    fun `profile needs a name`() {
        vm.newProfile()
        vm.updateName("   ")
        assertFalse(editor.canSave)
        vm.save()
        assertNotNull(state.editor)
        assertTrue(state.children.isEmpty())
    }

    @Test
    fun `cancel closes editor without saving`() {
        vm.newProfile()
        vm.updateName("Оля")
        vm.cancelEdit()
        assertNull(state.editor)
        assertTrue(state.children.isEmpty())
    }

    @Test
    fun `profiles are listed in the order they were created`() {
        addChild("Оля", Avatar.RABBIT)
        addChild("Петро", Avatar.ROCKET)
        assertEquals(listOf("Оля", "Петро"), state.children.map { it.name })
    }

    @Test
    fun `editing changes name and avatar of the same profile`() {
        addChild("Оля", Avatar.RABBIT)
        val original = state.children.single()

        vm.edit(original)
        assertEquals("Оля", editor.name)
        assertEquals(Avatar.RABBIT, editor.avatar)
        vm.updateName("Олечка")
        vm.updateAvatar(Avatar.STAR)
        vm.save()

        assertEquals(listOf(ChildProfile(original.id, "Олечка", Avatar.STAR)), state.children)
    }

    @Test
    fun `deleting a profile removes it together with its text assignments`() {
        addChild("Оля", Avatar.RABBIT)
        addChild("Петро", Avatar.ROCKET)
        val (olya, petro) = state.children
        val library = LibraryViewModel(texts, children)
        library.newText()
        library.updateTitle("Казка")
        library.updateRawText("Жив собі кіт.")
        library.toggleChild(olya)
        library.toggleChild(petro)
        library.save()

        vm.delete(olya)

        assertEquals(listOf(petro), state.children)
        val home = ChildHomeViewModel(texts, children)
        home.select(olya)
        assertNull(home.uiState.value.child)
        assertTrue(home.uiState.value.texts.isEmpty())
        assertEquals(listOf(petro), library.uiState.value.assignedTo.values.single())
    }
}
