package com.gefolgwar.readwithkid

import android.content.Context

interface VoskEngineListener {
    fun onPartialResult(text: String)
    fun onResult(text: String)
    fun onError(message: String)
    fun onModelLoaded()
}

interface VoskEngine {
    fun init(context: Context)
    fun start(mode: RecognitionMode, grammar: List<String>? = null)
    fun stop()
    fun pause(paused: Boolean)
    fun release()
    fun setListener(listener: VoskEngineListener)
}
