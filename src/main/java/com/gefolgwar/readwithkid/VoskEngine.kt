package com.gefolgwar.readwithkid

interface VoskEngineListener {
    fun onPartialResult(text: String)
    fun onResult(text: String)
    fun onError(message: String)
    fun onModelLoaded()
}

interface VoskEngine {
    fun loadModel()
    /**
     * Whether the loaded model's vocabulary has [word] (lowercase, as written): an unknown word cannot be recognised.
     * Without a loaded model, or a model without a vocabulary, every word counts as known.
     */
    fun knowsWord(word: String): Boolean
    fun start(mode: RecognitionMode, grammar: List<String>? = null)
    /** Swaps the grammar of a running recognition session without stopping audio capture. */
    fun updateGrammar(grammar: List<String>)
    fun stop()
    fun pause(paused: Boolean)
    fun release()
    fun setListener(listener: VoskEngineListener)
}
