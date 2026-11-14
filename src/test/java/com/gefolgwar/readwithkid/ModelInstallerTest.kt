package com.gefolgwar.readwithkid

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val modelDir get() = File(tmp.root, "model-uk")

    private fun copyFiles(target: File) {
        File(target, "am").mkdirs()
        File(target, "am/final.mdl").writeText("model")
    }

    @Test
    fun `install copies files and writes version marker`() {
        ModelInstaller.install(modelDir, "v1", ::copyFiles)

        assertTrue(ModelInstaller.isReady(modelDir, "v1"))
        assertEquals("model", File(modelDir, "am/final.mdl").readText())
        assertFalse(File(tmp.root, "model-uk.tmp").exists())
    }

    @Test
    fun `interrupted copy is not considered ready and is redone`() {
        try {
            ModelInstaller.install(modelDir, "v1") { target ->
                File(target, "partial").writeText("x")
                throw RuntimeException("disk full")
            }
            fail("expected failure")
        } catch (e: RuntimeException) {
            // expected
        }
        assertFalse(ModelInstaller.isReady(modelDir, "v1"))
        assertFalse(modelDir.exists())

        ModelInstaller.install(modelDir, "v1", ::copyFiles)
        assertTrue(ModelInstaller.isReady(modelDir, "v1"))
        assertFalse(File(modelDir, "partial").exists())
    }

    @Test
    fun `directory without marker is replaced`() {
        modelDir.mkdirs()
        File(modelDir, "stale").writeText("x")

        ModelInstaller.install(modelDir, "v1", ::copyFiles)

        assertTrue(ModelInstaller.isReady(modelDir, "v1"))
        assertFalse(File(modelDir, "stale").exists())
    }

    @Test
    fun `ready model with same version is not copied again`() {
        ModelInstaller.install(modelDir, "v1", ::copyFiles)
        var copied = false
        ModelInstaller.install(modelDir, "v1") { copied = true }
        assertFalse(copied)
    }

    @Test
    fun `new version triggers fresh copy`() {
        ModelInstaller.install(modelDir, "v1", ::copyFiles)
        var copied = false
        ModelInstaller.install(modelDir, "v2") { copied = true; copyFiles(it) }
        assertTrue(copied)
        assertTrue(ModelInstaller.isReady(modelDir, "v2"))
    }
}
