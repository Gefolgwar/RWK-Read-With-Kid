package com.gefolgwar.sttperformance

import android.content.Context

class FakeVoskEngine : VoskEngine {

    private var _listener: VoskEngineListener? = null
    var started = false
    var paused = false
    var lastMode: RecognitionMode? = null
    var lastGrammar: List<String>? = null
    var startCount = 0
    var stopCount = 0

    override fun init(context: Context) {
        _listener?.onModelLoaded()
    }

    override fun start(mode: RecognitionMode, grammar: List<String>?) {
        lastMode = mode
        lastGrammar = grammar
        started = true
        startCount++
    }

    override fun stop() {
        started = false
        stopCount++
    }

    override fun pause(paused: Boolean) {
        this.paused = paused
    }

    override fun release() {
        started = false
    }

    override fun setListener(listener: VoskEngineListener) {
        _listener = listener
    }

    fun simulateModelLoaded() {
        _listener?.onModelLoaded()
    }

    fun simulatePartial(text: String) {
        _listener?.onPartialResult("""{"partial":"$text"}""")
    }

    fun simulateFinal(text: String) {
        _listener?.onResult("""{"text":"$text"}""")
    }

    fun simulateError(message: String) {
        _listener?.onError(message)
    }
}
