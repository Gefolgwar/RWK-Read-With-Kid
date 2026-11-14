package com.gefolgwar.readwithkid

import android.app.Activity

class FakeModelPack(var size: Long? = 1_000L) : ModelPack {
    private var onEvent: ((PackEvent) -> Unit)? = null
    var fetchCount = 0
    var removed = false

    override suspend fun downloadSize(): Long? = size

    override fun fetch(onEvent: (PackEvent) -> Unit) {
        fetchCount++
        this.onEvent = onEvent
    }

    override fun showConfirmation(activity: Activity) = Unit

    override fun remove() {
        removed = true
    }

    fun emit(event: PackEvent) {
        onEvent!!(event)
    }
}
