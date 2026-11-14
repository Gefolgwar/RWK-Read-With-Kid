package com.gefolgwar.readwithkid

interface VoskEngineListener {
    fun onPartialResult(text: String)
    fun onResult(text: String)
    fun onError(message: String)
    fun onModelLoaded()
}

interface VoskEngine {
    fun loadModel()
    fun start(mode: RecognitionMode, grammar: List<String>? = null)
    /** Swaps the grammar of a running recognition session without stopping audio capture. */
    fun updateGrammar(grammar: List<String>)
    fun stop()
    fun pause(paused: Boolean)
    fun release()
    fun setListener(listener: VoskEngineListener)
}
