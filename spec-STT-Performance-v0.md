# Spec: STT Performance Tester v0 — Latency Comparison Spike

## Problem Statement

A developer building a children's reading-along app needs to validate which STT approach delivers acceptable latency on Android: full vocabulary recognition or grammar-constrained recognition. Without measured data, the architecture decision between these approaches is guesswork. The developer needs a minimal test harness that runs both modes on the same device with the same text and produces comparable latency numbers.

## Solution

A single-activity Kotlin Android app that runs Vosk STT in two modes — full recognition and grammar-constrained (vocabulary limited to current + next sentence) — behind a mode selector toggle. The tester reads a Ukrainian text aloud, the app highlights recognized words in real time, and captures four latency metrics per run. Results can be copied to clipboard for offline comparison.

## User Stories

1. As a performance tester, I want to select between "Full Recognition" and "Grammar-Constrained" modes before starting a run, so that I can compare latency between the two approaches on the same device.

2. As a performance tester, I want the app to load a Ukrainian Vosk model bundled in the APK assets on first launch, so that recognition works offline with zero setup beyond install.

3. As a performance tester, I want to select from 2–3 hardcoded Ukrainian text excerpts of different lengths, so that I can benchmark different complexities.

4. As a performance tester, I want the app to request microphone permission and begin streaming audio to Vosk when I press Start, so that recognition begins on demand.

5. As a performance tester, I want the app to highlight words in the source text as I read them aloud (green background + underline for confirmed words, yellow for tentative partial matches), so that I can visually judge whether the highlight keeps up with my reading speed.

6. As a performance tester, I want the highlighted text area to fill most of the screen, so that I can read comfortably and watch the highlights in real time.

7. As a performance tester, I want a collapsible bottom sheet showing raw Vosk output and detailed stats, so that I can inspect debugging data without it competing with the reading view.

8. As a performance tester, I want to see a one-line glanceable stats bar (e.g., "12/45 words | 43ms avg") visible at all times, so that I can monitor performance without opening the bottom sheet.

9. As a performance tester, I want the app to measure partial result frequency (partials/sec from Vosk), so that I can compare how often each mode delivers intermediate results.

10. As a performance tester, I want the app to measure utterance-to-final-result time (time from speech pause to Vosk final result callback), so that I can compare commit latency between modes.

11. As a performance tester, I want the app to measure callback-to-highlight time (processing overhead from Vosk callback to UI update), so that I can verify my alignment code isn't a bottleneck.

12. As a performance tester, I want the app to measure inter-word highlight time (time between successive word highlights), so that I can assess whether the pipeline keeps up with natural reading speed.

13. As a performance tester, I want to pause and resume the STT engine during a run, so that I can isolate specific pipeline phases.

14. As a performance tester, I want a "Copy Report" button that copies a text summary (mode, excerpt, all four metrics, per-word timings) to clipboard, so that I can paste results into a document for comparison.

15. As a performance tester, I want the app to display raw Vosk partial and final results in the bottom sheet, so that I can debug alignment issues when highlighting seems off.

16. As a performance tester, I want the grammar-constrained mode to limit Vosk's vocabulary to the words of the current sentence plus the next sentence, so that the search space is minimally small.

17. As a performance tester, I want the Recognizer to be rebuilt at sentence boundaries (when the user progresses past the current sentence), so that the grammar window advances as the user reads.

18. As a performance tester, I want the word alignment to use simple sequential matching with a small look-ahead window (2–3 words), so that minor misrecognitions don't stall highlighting.

## Implementation Decisions

- **Single module:** One Android Kotlin project, single-activity with Compose UI, MVVM pattern (ViewModel + state).
- **STT engine:** Vosk with `com.alphacephei:vosk-android:0.3.75` and `net.java.dev.jna:jna:5.18.1`. Model bundled in `assets/model-uk/`.
- **Two recognition modes:**
  - *Full mode:* `Recognizer(model, 16000.0f)` — no grammar constraint.
  - *Grammar mode:* `Recognizer(model, 16000.0f, grammarJson)` — grammar is a JSON array of words from the current sentence + next sentence. Recognizer is destroyed and recreated when the user crosses a sentence boundary.
- **Text structure:** Excerpts stored as pre-segmented `List<Sentence>` where each `Sentence` is a `List<String>` of words. Segmentation done manually at authoring time, not via runtime punctuation parsing.
- **Grammar window:** At any point during recognition, the grammar contains all words from the current sentence plus the next sentence. When the aligner detects the user has moved past the current sentence, the engine rebuilds the `Recognizer` with the new window.
- **Word alignment:** Simple sequential matching. Maintain a pointer into the flat word list. On each recognized word, look ahead up to 3 positions for a match (normalized: lowercased, punctuation stripped). Two pointers: `confirmedIndex` (advances on final results) and `tentativeIndex` (advances on partials, resets on next partial batch).
- **Metrics captured:**
  1. Partial result frequency (partials/sec)
  2. Utterance-to-final-result time (ms from last partial to final callback)
  3. Callback-to-highlight time (ns from callback entry to state emission)
  4. Inter-word highlight time (ms between successive word highlights)
- **UI layout:** Compose. Top bar with mode indicator + excerpt selector + controls (Start/Pause/Stop). Source text with word-level highlighting fills the screen. Collapsed bottom sheet shows one-line stats summary; expanded shows raw Vosk output + detailed per-word timing table + Copy Report button.
- **Mode selector:** Radio buttons or dropdown, selectable only when not running. Mode determines how the `Recognizer` is constructed.
- **Log export:** Clipboard only. "Copy Report" button generates a text summary and calls `ClipboardManager.setPrimaryClip()`.
- **CPU/memory monitoring:** Not in-app. Use Android Studio Profiler via ADB externally.
- **VoskEngine interface:** Engine exposes `start(mode, grammar?)`, `stop()`, `pause(Boolean)`, and callbacks `onPartialResult(String)`, `onResult(String)`, `onError(String)`, `onModelLoaded()`. This interface enables testing with a fake engine.

## Testing Decisions

- **Test what matters:** External behavior only — given a sequence of simulated recognition events, does the ViewModel produce the correct highlight state and metrics? Do not test Vosk internals or Compose rendering.
- **Primary test seam: ViewModel.** Inject a fake `VoskEngine` that replays scripted partial/final results. Assert:
  - Highlight index advances correctly in both modes
  - Grammar is rebuilt at sentence boundaries in constrained mode
  - All four metrics are computed correctly
  - State transitions (idle → loading → listening → paused → stopped) behave correctly
- **Secondary test seam: WordAligner.** Pure unit tests — feed recognized words, assert `confirmedIndex` and `tentativeIndex`. Test sequential matching, look-ahead tolerance, normalization (case, punctuation).
- **Secondary test seam: PerformanceTracker.** Pure unit tests — feed timestamps, assert computed averages, maximums, report formatting.
- **Not tested in v0:** UI rendering, Vosk integration (model loading, actual audio), Android permissions flow. These are validated manually on device.

## Out of Scope

- Children's speech patterns (self-loops for repetition, skip-loops, garbage/noise states) — deferred to v1, requires real child audio recordings to validate
- Forced alignment with CTC acoustic model + Viterbi/DTW — separate spike with a different model and pipeline
- In-app CPU/memory monitoring — use Android Studio Profiler externally
- File export or share intent for logs — clipboard is sufficient for v0
- Multiple languages — Ukrainian only
- Production UX (onboarding, model download progress, error recovery, accessibility)
- Play Store distribution — developer testing tool only
- Side-by-side simultaneous mode comparison — one mode per run, compare across runs

## Further Notes

- The Ukrainian Vosk model (`vosk-model-small-uk`) must be downloaded separately from https://alphacephei.com/vosk/models and placed in `src/main/assets/model-uk/`. The APK will be ~60MB+ with the bundled model.
- If grammar-constrained mode proves significantly faster, v1 should explore forced alignment (CTC + Viterbi/DTW) as the "professional" approach for sub-20ms latency with phoneme-level tracking.
- The flexible state graph for children's reading patterns (described in the grilling session) is the key v1 architectural piece — it requires real child audio data to validate, which this v0 spike should help collect.
