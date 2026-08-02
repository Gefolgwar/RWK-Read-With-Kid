# 05 — Real VoskEngine Implementation

**What to build:** The production `VoskEngine` implementation that wraps the Vosk SDK, AudioRecord, and model loading.

**Blocked by:** 01 (needs VoskEngine interface)

**Status:** ready

## Deliverables

- [ ] `RealVoskEngine` implementing `VoskEngine`:
  - `init()`: load model from `assets/model-uk/` on a background thread, call `onModelLoaded()` when done
  - `start()`:
    - FULL mode: `Recognizer(model, 16000.0f)`
    - GRAMMAR mode: `Recognizer(model, 16000.0f, grammarJson)` where grammarJson = `["word1","word2",...,"[unk]"]`
    - Start `AudioRecord` (MONO, 16kHz, 16-bit PCM) on a background thread
    - Read audio buffer in a loop, feed to `recognizer.acceptWaveForm()`
    - Dispatch `onPartialResult` / `onResult` to listener on main thread
  - `stop()`: stop AudioRecord, close Recognizer
  - `pause(paused)`: pause/resume AudioRecord reading (keep thread alive)
  - `release()`: release model, stop everything
- [ ] Thread management: audio capture on dedicated thread, callbacks posted to main thread
- [ ] Error handling: model load failure → `onError()`, AudioRecord init failure → `onError()`

## Notes

- NOT unit tested (spec says "Vosk integration validated manually on device").
- The Vosk model (`vosk-model-small-uk`) must be manually placed in `src/main/assets/model-uk/`.
- `@aar` dependencies for vosk-android and jna are already in build.gradle.
- AudioRecord requires `RECORD_AUDIO` permission (handled in UI layer).
