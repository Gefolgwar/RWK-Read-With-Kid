package com.gefolgwar.readwithkid

import java.io.File

/**
 * Installs a model directory atomically: files are copied into a temporary sibling
 * directory, a version marker is written last, and the directory is then renamed into place.
 * A directory without a matching marker (e.g. an interrupted copy) is never treated as ready.
 */
object ModelInstaller {
    const val MARKER_FILE = ".model-version"

    fun isReady(modelDir: File, version: String): Boolean {
        val marker = File(modelDir, MARKER_FILE)
        return marker.isFile && marker.readText() == version
    }

    fun install(modelDir: File, version: String, copyInto: (File) -> Unit): File {
        if (isReady(modelDir, version)) return modelDir

        val tmpDir = File(modelDir.parentFile, modelDir.name + ".tmp")
        tmpDir.deleteRecursively()
        modelDir.deleteRecursively()
        check(tmpDir.mkdirs()) { "Cannot create $tmpDir" }

        copyInto(tmpDir)
        File(tmpDir, MARKER_FILE).writeText(version)
        check(tmpDir.renameTo(modelDir)) { "Cannot move $tmpDir to $modelDir" }
        return modelDir
    }
}
