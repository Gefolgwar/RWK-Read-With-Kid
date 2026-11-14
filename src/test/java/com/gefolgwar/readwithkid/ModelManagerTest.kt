package com.gefolgwar.readwithkid

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ModelManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val version = "vosk-model-small-uk-v3-small"
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val pack = FakeModelPack(size = 5_000L)
    private var freeBytes = Long.MAX_VALUE

    private val modelDir get() = File(tmp.root, "model-uk")

    private fun manager() = ModelManager(modelDir, version, pack, scope, dispatcher) { freeBytes }
        .also { scope.advanceUntilIdle() }

    /** A zip laid out like the official Vosk archive: one top-level folder with the model. */
    private fun modelZip(): File {
        val zip = File(tmp.root, "$version.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("$version/"))
            out.putNextEntry(ZipEntry("$version/am/final.mdl"))
            out.write("acoustic".toByteArray())
            out.putNextEntry(ZipEntry("$version/conf/model.conf"))
            out.write("conf".toByteArray())
            out.closeEntry()
        }
        return zip
    }

    @Test
    fun `missing model is not installed and reports download size`() {
        val manager = manager()
        assertEquals(ModelState.NotInstalled(5_000L), manager.state.value)
    }

    @Test
    fun `download size is unknown when the pack cannot tell it`() {
        pack.size = null
        assertEquals(ModelState.NotInstalled(null), manager().state.value)
    }

    @Test
    fun `installed model reports version and size on disk`() {
        ModelInstaller.install(modelDir, version) { File(it, "final.mdl").writeText("12345") }
        val state = manager().state.value as ModelState.Installed
        assertEquals(version, state.version)
        assertEquals(5L + version.length, state.sizeBytes) // model file + version marker
    }

    @Test
    fun `model of another version is not considered installed`() {
        ModelInstaller.install(modelDir, "old-version") { File(it, "final.mdl").writeText("x") }
        assertTrue(manager().state.value is ModelState.NotInstalled)
    }

    @Test
    fun `download shows progress, unpacks the archive atomically and frees the pack`() {
        val manager = manager()
        manager.download()
        assertEquals(ModelState.Downloading(0, 5_000L), manager.state.value)
        assertEquals(1, pack.fetchCount)

        pack.emit(PackEvent.Progress(2_000L, 5_000L))
        assertEquals(ModelState.Downloading(2_000L, 5_000L), manager.state.value)

        pack.emit(PackEvent.Ready(modelZip()))
        assertEquals(ModelState.Unpacking, manager.state.value)
        scope.advanceUntilIdle()

        assertTrue(manager.state.value is ModelState.Installed)
        assertTrue(ModelInstaller.isReady(modelDir, version))
        assertEquals("acoustic", File(modelDir, "am/final.mdl").readText())
        assertEquals("conf", File(modelDir, "conf/model.conf").readText())
        assertTrue(pack.removed)
    }

    @Test
    fun `installed flow emits only after the check and on real changes`() {
        val manager = manager()
        val emitted = mutableListOf<Boolean>()
        scope.launch { manager.installed.toList(emitted) }
        scope.advanceUntilIdle()

        manager.download()
        pack.emit(PackEvent.Progress(1L, 5_000L))
        pack.emit(PackEvent.Ready(modelZip()))
        scope.advanceUntilIdle()

        assertEquals(listOf(false, true), emitted)
    }

    @Test
    fun `no network lets the parent retry`() {
        val manager = manager()
        manager.download()
        pack.emit(PackEvent.Failed(ModelError.NO_NETWORK))
        assertEquals(ModelState.NotInstalled(5_000L, ModelError.NO_NETWORK), manager.state.value)

        manager.download()
        assertEquals(2, pack.fetchCount)
        assertTrue(manager.state.value is ModelState.Downloading)
    }

    @Test
    fun `not enough storage for the download is reported`() {
        val manager = manager()
        manager.download()
        pack.emit(PackEvent.Failed(ModelError.INSUFFICIENT_STORAGE))
        assertEquals(ModelState.NotInstalled(5_000L, ModelError.INSUFFICIENT_STORAGE), manager.state.value)
    }

    @Test
    fun `not enough storage to unpack is reported and nothing is left behind`() {
        freeBytes = 3L
        val manager = manager()
        manager.download()
        pack.emit(PackEvent.Ready(modelZip()))
        scope.advanceUntilIdle()

        assertEquals(ModelState.NotInstalled(5_000L, ModelError.INSUFFICIENT_STORAGE), manager.state.value)
        assertFalse(modelDir.exists())
        assertFalse(pack.removed)
    }

    @Test
    fun `broken archive fails without a half-installed model`() {
        val broken = File(tmp.root, "broken.zip").apply { writeText("not a zip") }
        val manager = manager()
        manager.download()
        pack.emit(PackEvent.Ready(broken))
        scope.advanceUntilIdle()

        assertEquals(ModelState.NotInstalled(5_000L, ModelError.FAILED), manager.state.value)
        assertFalse(ModelInstaller.isReady(modelDir, version))
        assertFalse(File(tmp.root, "model-uk.tmp").exists())
    }

    @Test
    fun `download waiting for confirmation continues after it`() {
        val manager = manager()
        manager.download()
        pack.emit(PackEvent.NeedsConfirmation)
        assertEquals(ModelState.NeedsConfirmation, manager.state.value)

        pack.emit(PackEvent.Progress(10L, 5_000L))
        assertEquals(ModelState.Downloading(10L, 5_000L), manager.state.value)
    }

    @Test
    fun `download is ignored while downloading or installed`() {
        val manager = manager()
        manager.download()
        manager.download()
        assertEquals(1, pack.fetchCount)

        pack.emit(PackEvent.Ready(modelZip()))
        scope.advanceUntilIdle()
        manager.download()
        assertEquals(1, pack.fetchCount)
    }

    @Test
    fun `delete removes the model and offers the download again`() {
        val manager = manager()
        manager.download()
        pack.emit(PackEvent.Ready(modelZip()))
        scope.advanceUntilIdle()

        manager.delete()
        scope.advanceUntilIdle()

        assertFalse(modelDir.exists())
        assertEquals(ModelState.NotInstalled(5_000L), manager.state.value)
    }
}
