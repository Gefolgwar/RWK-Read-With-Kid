package com.gefolgwar.readwithkid

enum class RecognitionMode {
    FULL,
    GRAMMAR_CONSTRAINED
}

enum class SessionState {
    /** The speech model is not downloaded yet; it is being downloaded or the download failed. */
    MODEL_MISSING,
    LOADING_MODEL,
    MODEL_ERROR,
    READY,
    READING,
    PAUSED,
    /** The text is read; the child reads misread, skipped and uncertain words again. */
    REVIEWING,
    FINISHED
}

enum class WordStatus {
    UNREAD,
    TENTATIVE,
    CONFIRMED,
    SKIPPED
}

/** Language of a text; chooses the speech model. Stored by name. */
enum class TextLanguage { UK, DE }
