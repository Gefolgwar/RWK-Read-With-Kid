package com.gefolgwar.readwithkid

import android.app.Activity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

enum class ModelError { NO_NETWORK, INSUFFICIENT_STORAGE, FAILED }

sealed interface ModelState {
    /** Looking up whether the model is installed. */
    data object Checking : ModelState
    /** [downloadBytes] is null when the size is unknown; [error] is why the last attempt failed. */
    data class NotInstalled(val downloadBytes: Long?, val error: ModelError? = null) : ModelState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelState
    /** The download waits for Wi-Fi or for the user to allow it. */
    data object NeedsConfirmation : ModelState
    data object Unpacking : ModelState
    data object Installed : ModelState
}

sealed interface PackEvent {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : PackEvent
    data object NeedsConfirmation : PackEvent
    /** The pack is on the device; [zip] is the model archive inside it. */
    data class Ready(val zip: File) : PackEvent
    data class Failed(val error: ModelError) : PackEvent
}

/** Delivers one model archive to the device (a Play asset pack in the app). */
interface ModelPack {
    /** Download size in bytes, or null when unknown. */
    suspend fun downloadSize(): Long?
    /** Starts or resumes the download; [onEvent] is called on the main thread. */
    fun fetch(onEvent: (PackEvent) -> Unit)
    fun showConfirmation(activity: Activity)
    /** Frees the space taken by the downloaded pack. */
    fun remove()
}

/**
 * Knows whether a speech model is installed; downloads it via [pack] and
 * unpacks the archive atomically into [directory] (see [ModelInstaller]). It is deleted with the app.
 */
class ModelManager(
    val directory: File,
    private val version: String,
    private val pack: ModelPack,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val freeSpace: () -> Long = { directory.parentFile!!.usableSpace }
) {
    private val _state = MutableStateFlow<ModelState>(ModelState.Checking)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** Whether the model can be loaded; emits once the first check is done, then on every change. */
    val installed: Flow<Boolean> = state
        .filter { it != ModelState.Checking }
        .map { it is ModelState.Installed }
        .distinctUntilChanged()

    private var downloadBytes: Long? = null

    init {
        scope.launch { _state.value = check() }
    }

    fun download() {
        if (_state.value !is ModelState.NotInstalled) return
        _state.value = ModelState.Downloading(0, downloadBytes ?: 0)
        pack.fetch(::onPackEvent)
    }

    fun confirmDownload(activity: Activity) {
        if (_state.value == ModelState.NeedsConfirmation) pack.showConfirmation(activity)
    }

    private fun onPackEvent(event: PackEvent) {
        val current = _state.value
        if (current !is ModelState.Downloading && current != ModelState.NeedsConfirmation) return
        when (event) {
            is PackEvent.Progress -> _state.value = ModelState.Downloading(event.downloadedBytes, event.totalBytes)
            PackEvent.NeedsConfirmation -> _state.value = ModelState.NeedsConfirmation
            is PackEvent.Failed -> _state.value = ModelState.NotInstalled(downloadBytes, event.error)
            is PackEvent.Ready -> {
                _state.value = ModelState.Unpacking
                scope.launch { _state.value = unpack(event.zip) }
            }
        }
    }

    private suspend fun check(): ModelState {
        if (withContext(io) { ModelInstaller.isReady(directory, version) }) return ModelState.Installed
        downloadBytes = pack.downloadSize()
        return ModelState.NotInstalled(downloadBytes)
    }

    private suspend fun unpack(zip: File): ModelState = withContext(io) {
        try {
            ZipFile(zip).use { archive ->
                val needed = archive.entries().asSequence().sumOf { maxOf(it.size, 0L) }
                if (needed > freeSpace()) {
                    return@withContext ModelState.NotInstalled(downloadBytes, ModelError.INSUFFICIENT_STORAGE)
                }
                ModelInstaller.install(directory, version) { target -> extract(archive, target) }
            }
            pack.remove()
            ModelState.Installed
        } catch (e: Exception) {
            ModelState.NotInstalled(downloadBytes, ModelError.FAILED)
        }
    }

    /** The Vosk archive holds one top-level folder; its content becomes [target]. */
    private fun extract(archive: ZipFile, target: File) {
        val root = target.canonicalPath + File.separator
        for (entry in archive.entries()) {
            val name = entry.name.substringAfter('/')
            if (entry.isDirectory || name.isEmpty()) continue
            val file = File(target, name)
            check(file.canonicalPath.startsWith(root)) { "Entry outside the model: ${entry.name}" }
            file.parentFile!!.mkdirs()
            archive.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
    }
}
