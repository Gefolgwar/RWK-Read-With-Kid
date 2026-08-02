# 03 — PerformanceTracker (TDD)

**What to build:** Pure logic that records timestamps and computes the four latency metrics defined in the spec.

**Blocked by:** Nothing (standalone pure logic)

**Status:** ready

## Deliverables

- [ ] `PerformanceTracker` class with:
  - `recordPartial(timestampMs: Long)` — records a partial result event
  - `recordFinalStart(timestampMs: Long)` — marks when the last partial before a final was received
  - `recordFinalEnd(timestampMs: Long)` — marks when the final result callback fires
  - `recordHighlightUpdate(wordIndex: Int, timestampNs: Long)` — records when a word highlight was emitted
  - `recordCallbackEntry(timestampNs: Long)` / `recordHighlightEmit(timestampNs: Long)` — for callback-to-highlight measurement
  - `reset()`
- [ ] Computed metrics:
  1. **Partial result frequency** (partials/sec) — count of partials / elapsed time
  2. **Utterance-to-final-result time** (ms) — avg time from last partial to final callback
  3. **Callback-to-highlight time** (ns) — avg processing overhead from callback entry to state emission
  4. **Inter-word highlight time** (ms) — avg time between successive word highlights
- [ ] `generateReport(mode: RecognitionMode, excerptTitle: String): String` — text summary for clipboard
- [ ] Per-word timing list for the detailed bottom sheet table
- [ ] Unit tests covering:
  - Each metric computed correctly from scripted timestamps
  - Report formatting
  - Edge cases: zero partials, single word, reset clears state

## Notes

- Pure Kotlin, no Android dependencies. Uses timestamps passed in (not `System.nanoTime()` internally) for testability.
- This is one of the three spec-defined test seams.
