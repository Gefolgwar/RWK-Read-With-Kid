package com.gefolgwar.readwithkid

import android.app.Activity
import android.content.Context
import com.google.android.play.core.assetpacks.AssetPackException
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** A model archive [zipName] shipped in the on-demand Play asset pack [packName]. */
class PlayModelPack(context: Context, private val packName: String, private val zipName: String) : ModelPack {

    private val manager = AssetPackManagerFactory.getInstance(context)
    private var onEvent: ((PackEvent) -> Unit)? = null

    init {
        manager.registerListener(AssetPackStateUpdateListener { state ->
            if (state.name() == packName) toEvent(state)?.let { event -> onEvent?.invoke(event) }
        })
    }

    override suspend fun downloadSize(): Long? = suspendCancellableCoroutine { cont ->
        manager.getPackStates(listOf(packName))
            .addOnSuccessListener { states ->
                cont.resume(states.packStates()[packName]?.totalBytesToDownload()?.takeIf { it > 0 })
            }
            .addOnFailureListener { cont.resume(null) }
    }

    override fun fetch(onEvent: (PackEvent) -> Unit) {
        this.onEvent = onEvent
        // Downloaded earlier, e.g. the app was closed while unpacking
        downloadedZip()?.let { onEvent(PackEvent.Ready(it)); return }
        manager.fetch(listOf(packName)).addOnFailureListener { e ->
            onEvent(PackEvent.Failed(errorOf((e as? AssetPackException)?.errorCode ?: AssetPackErrorCode.INTERNAL_ERROR)))
        }
    }

    override fun showConfirmation(activity: Activity) {
        manager.showConfirmationDialog(activity)
    }

    override fun remove() {
        manager.removePack(packName)
    }

    private fun downloadedZip(): File? = manager.getPackLocation(packName)?.assetsPath()?.let { File(it, zipName) }

    private fun toEvent(state: AssetPackState): PackEvent? = when (state.status()) {
        AssetPackStatus.PENDING, AssetPackStatus.DOWNLOADING, AssetPackStatus.TRANSFERRING ->
            PackEvent.Progress(state.bytesDownloaded(), state.totalBytesToDownload())
        AssetPackStatus.WAITING_FOR_WIFI, AssetPackStatus.REQUIRES_USER_CONFIRMATION -> PackEvent.NeedsConfirmation
        AssetPackStatus.COMPLETED -> downloadedZip()?.let { PackEvent.Ready(it) } ?: PackEvent.Failed(ModelError.FAILED)
        AssetPackStatus.FAILED -> PackEvent.Failed(errorOf(state.errorCode()))
        AssetPackStatus.CANCELED -> PackEvent.Failed(ModelError.FAILED)
        else -> null
    }

    private fun errorOf(code: Int): ModelError = when (code) {
        AssetPackErrorCode.NETWORK_ERROR -> ModelError.NO_NETWORK
        AssetPackErrorCode.INSUFFICIENT_STORAGE -> ModelError.INSUFFICIENT_STORAGE
        else -> ModelError.FAILED
    }
}

/** The Ukrainian model (Apache 2.0), delivered by the on-demand asset pack `model_uk`. */
object UkrainianModel {
    const val VERSION = "vosk-model-small-uk-v3-small"

    @Volatile
    private var instance: ModelManager? = null

    fun get(context: Context): ModelManager = instance ?: synchronized(this) {
        instance ?: context.applicationContext.let { app ->
            ModelManager(
                directory = File(app.filesDir, "model-uk"),
                version = VERSION,
                pack = PlayModelPack(app, packName = "model_uk", zipName = "$VERSION.zip"),
                scope = MainScope()
            )
        }.also { instance = it }
    }
}
