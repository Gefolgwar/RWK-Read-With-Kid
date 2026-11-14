package com.gefolgwar.readwithkid

enum class RecognitionMode {
    FULL,
    GRAMMAR_CONSTRAINED
}

enum class AppState {
    IDLE,
    LOADING_MODEL,
    LISTENING,
    PAUSED,
    STOPPED
}

enum class WordStatus {
    UNREAD,
    TENTATIVE,
    CONFIRMED
}
