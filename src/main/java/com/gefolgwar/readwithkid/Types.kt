package com.gefolgwar.readwithkid

enum class RecognitionMode {
    FULL,
    GRAMMAR_CONSTRAINED
}

enum class SessionState {
    /** The speech model is not downloaded yet (or was deleted). */
    MODEL_MISSING,
    LOADING_MODEL,
    MODEL_ERROR,
    READY,
    READING,
    PAUSED,
    FINISHED
}

enum class WordStatus {
    UNREAD,
    TENTATIVE,
    CONFIRMED,
    SKIPPED
}
