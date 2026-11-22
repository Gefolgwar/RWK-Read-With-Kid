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
    FINISHED
}

enum class WordStatus {
    UNREAD,
    /** Read in the sentence being read, by a partial result that may still change. */
    TENTATIVE,
    /** Read in the sentence being read. */
    CONFIRMED,
    /** Read correctly, in a read sentence. */
    READ,
    /** Misread or skipped, in a read sentence. */
    MISREAD,
    /** In a read sentence, a word the model could not check: unknown to it, or uncertain. */
    UNCHECKED
}

/** How a sentence was read last. */
enum class SentenceStatus {
    UNREAD,
    /** Read, with at most half of its words misread or skipped. */
    READ,
    /** Read with more than half of its words misread or skipped. */
    TOO_MANY_ERRORS
}

/** Language of a text; chooses the speech model. Stored by name. */
enum class TextLanguage { UK, DE }
