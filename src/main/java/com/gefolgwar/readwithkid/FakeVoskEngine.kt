package com.gefolgwar.readwithkid

class FakeVoskEngine : VoskEngine {

    private var _listener: VoskEngineListener? = null
    var started = false
    var paused = false
    var released = false
    var lastMode: RecognitionMode? = null
    var lastGrammar: List<String>? = null
    var startCount = 0
    var stopCount = 0
    var loadCount = 0
    var grammarUpdateCount = 0

    override fun loadModel() {
        loadCount++
    }

    override fun start(mode: RecognitionMode, grammar: List<String>?) {
        lastMode = mode
        lastGrammar = grammar
        started = true
        paused = false // as RealVoskEngine
        startCount++
    }

    override fun updateGrammar(grammar: List<String>) {
        lastGrammar = grammar
        grammarUpdateCount++
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
        released = true
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
