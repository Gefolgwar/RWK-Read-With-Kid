# 02 — WordAligner (TDD)

**What to build:** Pure logic that matches recognized words against the source text and tracks highlight positions.

**Blocked by:** 01 (needs domain types)

**Status:** ready

## Deliverables

- [ ] `WordAligner` class with:
  - Constructor takes `words: List<String>` (flat word list from excerpt)
  - `confirmedIndex: Int` — advances on final results only
  - `tentativeIndex: Int` — advances on partials, resets on next partial batch
  - `processPartial(recognizedWords: List<String>)` — advances `tentativeIndex` from current `confirmedIndex`
  - `processFinal(recognizedWords: List<String>)` — advances `confirmedIndex`, resets `tentativeIndex`
  - `reset()` — resets both pointers to -1
  - `getWordStatus(index: Int): WordStatus` — returns CONFIRMED / TENTATIVE / UNREAD
- [ ] Normalization: lowercase, strip Ukrainian punctuation (`,.''"!?—:;()«»`)
- [ ] Look-ahead window: up to 3 positions forward when matching
- [ ] Unit tests covering:
  - Sequential word matching (exact)
  - Case-insensitive matching
  - Punctuation stripping (e.g., "держава." matches "держава")
  - Look-ahead skip (misrecognized word skipped, next word matches)
  - Partial → tentative advancement, then reset on new partial
  - Final → confirmed advancement
  - Mixed partial/final sequence
  - Empty input handling

## Notes

- Pure Kotlin, no Android dependencies.
- This is one of the three spec-defined test seams.
- The aligner does NOT know about sentences — it works on a flat word list. Sentence boundary detection is the ViewModel's job.
