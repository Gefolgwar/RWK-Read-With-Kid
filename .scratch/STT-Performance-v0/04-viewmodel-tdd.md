# 04 — ViewModel (TDD)

**What to build:** The `SttViewModel` that orchestrates engine, aligner, tracker, and state — the primary test seam.

**Blocked by:** 01, 02, 03 (needs types, aligner, tracker)

**Status:** ready

## Deliverables

- [ ] `FakeVoskEngine` (test double): replays scripted partial/final results, records start/stop/pause calls
- [ ] `SttViewModel` with:
  - `uiState: StateFlow<SttUiState>` containing:
    - `appState: AppState`
    - `mode: RecognitionMode`
    - `selectedExcerptIndex: Int`
    - `wordStatuses: List<WordStatus>` (one per word in current excerpt)
    - `stats: StatsSnapshot` (the four metrics, formatted for display)
    - `rawLog: List<String>` (raw Vosk partial/final output for bottom sheet)
    - `wordsMatched: Int` / `totalWords: Int`
  - `selectMode(mode: RecognitionMode)` — only when IDLE/STOPPED
  - `selectExcerpt(index: Int)` — only when IDLE/STOPPED
  - `start()` — request mic permission callback, then start engine
  - `pause()` / `resume()`
  - `stop()`
  - `copyReport()` — delegates to PerformanceTracker.generateReport()
- [ ] Grammar window logic:
  - In GRAMMAR_CONSTRAINED mode, compute grammar = words of current sentence + next sentence
  - Detect sentence boundary crossing (when confirmedIndex passes the last word of current sentence)
  - Rebuild recognizer (engine.stop() + engine.start() with new grammar) at boundary
- [ ] Wire VoskEngineListener callbacks:
  - `onPartialResult` → parse JSON, feed words to aligner.processPartial(), update tracker, update state
  - `onResult` → parse JSON, feed words to aligner.processFinal(), update tracker, update state
  - `onModelLoaded` → transition to IDLE
  - `onError` → log to rawLog
- [ ] Tests with FakeVoskEngine:
  - State transitions: IDLE → LOADING → LISTENING → PAUSED → LISTENING → STOPPED
  - Highlight index advances correctly in both modes
  - Grammar is rebuilt at sentence boundaries in constrained mode
  - Mode/excerpt selection blocked while listening
  - Stats update on each callback
  - Raw log accumulates partial/final output

## Notes

- The ViewModel is the spec's PRIMARY test seam.
- Vosk returns JSON: `{"partial":"слово слово"}` for partials, `{"text":"слово слово"}` for finals.
- Grammar is a JSON array string: `["слово1", "слово2", "[unk]"]`.
