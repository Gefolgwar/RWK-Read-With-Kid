# 01 — Domain Types, TextExcerpts Refactor & VoskEngine Interface

**What to build:** All shared types, the pre-segmented text data, and the engine abstraction that the rest of the app codes against.

**Blocked by:** Nothing

**Status:** ready

## Deliverables

- [ ] `RecognitionMode` enum: `FULL`, `GRAMMAR_CONSTRAINED`
- [ ] `AppState` enum: `IDLE`, `LOADING_MODEL`, `LISTENING`, `PAUSED`, `STOPPED`
- [ ] `WordStatus` enum: `UNREAD`, `TENTATIVE`, `CONFIRMED`
- [ ] Refactor `TextExcerpts`: each excerpt stores `sentences: List<List<String>>` (pre-segmented). Keep `title`. Drop raw `text` string — the flat word list is derived from `sentences.flatten()`. Update the 3 Ukrainian excerpts to use this structure.
- [ ] `VoskEngineListener` interface: `onPartialResult(text: String)`, `onResult(text: String)`, `onError(message: String)`, `onModelLoaded()`
- [ ] `VoskEngine` interface: `init(context: Context)`, `start(mode: RecognitionMode, grammar: List<String>?)`, `stop()`, `pause(paused: Boolean)`, `release()`, `setListener(listener: VoskEngineListener)`

## Notes

- These are pure Kotlin files, no Android dependencies except `Context` in the engine interface.
- The `VoskEngine` interface is the primary test seam — the ViewModel will depend only on this interface.
- Sentence segmentation is manual (done at authoring time in `TextExcerpts`), not runtime punctuation parsing.
