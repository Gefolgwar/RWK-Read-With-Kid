package com.gefolgwar.readwithkid

enum class RecognitionMode {
    FULL,
    GRAMMAR_CONSTRAINED
}

enum class SessionState {
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
