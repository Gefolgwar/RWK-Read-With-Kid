# 06 — Compose UI & MainActivity

**What to build:** The single-activity Compose UI with all visual elements described in the spec.

**Blocked by:** 04, 05 (needs ViewModel and real engine)

**Status:** ready

## Deliverables

- [ ] `MainActivity`: request mic permission, create ViewModel with RealVoskEngine, set Compose content
- [ ] **Top bar:**
  - Mode selector (radio buttons: "Full Recognition" / "Grammar-Constrained") — disabled while listening
  - Excerpt selector (dropdown of excerpt titles) — disabled while listening
  - Control buttons: Start / Pause / Resume / Stop (visibility based on AppState)
- [ ] **Source text area** (fills most of screen):
  - Displays all words of selected excerpt
  - Green background + underline for CONFIRMED words
  - Yellow background for TENTATIVE words
  - Normal for UNREAD words
  - Auto-scroll to keep current word visible
- [ ] **Stats bar** (always visible, one line):
  - Format: `"12/45 words | 43ms avg"`
  - Shows wordsMatched/totalWords and average inter-word time
- [ ] **Collapsible bottom sheet:**
  - Raw Vosk output (partials + finals, scrollable)
  - Detailed per-word timing table
  - Partial frequency, utterance-to-final, callback-to-highlight stats
  - "Copy Report" button → clipboard
- [ ] Permission flow: request RECORD_AUDIO on Start press if not granted

## Notes

- NOT unit tested in v0 (spec: "UI rendering validated manually on device").
- Use Material3 components, BottomSheetScaffold for the collapsible sheet.
- The text area should be comfortable to read — large font, good spacing.
- Keep the UI simple: this is a developer testing tool, not a production app.
