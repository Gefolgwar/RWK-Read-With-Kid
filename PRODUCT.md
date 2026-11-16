# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

- **Child, 5–7 years old**, just learning to read (preschool or first grade), often still reading syllable by syllable. Reads the text aloud **alone**: the parent prepares the text and hands over the phone, but does not sit beside the child during reading. The child may not yet read menus or instructions fluently.
- **Parent** (or another adult in the family): adds the text the child should practise, and later looks at the result and history to see how the reading went. Families read in Ukrainian and/or German, in particular Ukrainian families living in Germany.

## Product Purpose

Lets parents check how their child reads aloud, on a text the parents chose themselves, without having to listen to every word. The child reads; the app follows along word by word in real time and then shows an objective result (accuracy, words correct per minute, skipped and problem words, duration), so progress becomes visible over repeated readings.

Success: the child can get from "text is ready" to "finished reading" without adult help, enjoys it, and the parent gets a result they trust.

## Positioning

All four together are the product's position (confirmed as essential by the owner):

1. **The parent's own texts.** Any text: a school assignment page, a favourite fairy tale, a teacher's exercise. No fixed content catalogue.
2. **Private and offline.** Speech recognition runs on the device (Vosk). Audio is processed as a stream and is never stored or transmitted. No accounts, no ads, no tracking, no server; texts and results stay on the phone.
3. **Ukrainian and German.** Both reading languages, language detected from the text's alphabet and switchable; interface in Ukrainian, German and English.
4. **Tolerant of how children actually read.** Repetitions, reading by syllables, self-corrections and pauses are not counted as errors; skipped words are noted and the reading moves on; words the speech model does not know are not counted as the child's errors.

## Operating Context

The v1.1 flow is one simple path with a minimum of buttons:

Text list → "+" → paste or type a text (language detected automatically, switchable uk/de) → Save → tap the text → the language model downloads automatically with progress (first use of a language only) → Start → child reads aloud with the current word highlighted → result.

- The child holds the phone and reads alone; the parent sets up before and reviews after.
- During reading: the current word is highlighted, read words are marked, long text auto-scrolls to keep the current line visible, Pause/Continue and Done are available; tapping a word speaks it (pronunciation help).
- Reading pauses automatically when the app goes to the background.
- Result history is shared (no child profiles) and shown per text, on the text screen before reading.
- Settings is a single icon on the main screen: interface language (uk/de/en/system), privacy policy (shown in the app), open-source licences.
- Intended to run on older, low-memory hand-me-down phones (2–3 GB RAM).

## Capabilities and Constraints

- Android app, Jetpack Compose with Material 3; minSdk 24, targetSdk 36. Package `com.gefolgwar.readwithkid`.
- Speech recognition: Vosk, offline, Full recognition mode by default. One model per language, delivered as on-demand Play asset packs (`model_uk`, `model_de`); the model is removed together with the app. Ukrainian model size (small 436 MB vs nano 133 MB) is still being decided (#33).
- Pronunciation help uses Android TextToSpeech in the text's locale; hidden if no voice for the language exists.
- Optional coloured syllables in the reading text.
- Storage: Room, local only.
- Removed in v1.1 and not to be reintroduced without a decision: parent zone, parental lock, onboarding, child profiles, text assignment, separate language-models screen, .txt import, sentence/word split preview, separate title field (title is generated from the first words).
- No external links from the app (so no parental gate is required under the Google Play Families Policy); the only exception is opening system settings for the microphone permission.
- Microphone permission is requested only when reading starts, with an explanation; a denied permission gets guidance to enable it in settings.
- Google Play requirements: Families Policy (target audience includes children), Data Safety (audio not collected), 16 KB page size.
- German law: texts are for private family use only (§53 UrhG), no sharing or publication; any bundled sample texts must be public domain (author died over 70 years ago, original edition) or the owner's own. DSGVO.
- **Undecided:** the final app name and `applicationId` ("Read With Kid" is a working title, #10); the Ukrainian model choice (#33).

## Brand Commitments

- Working name "Read With Kid" (not final; must pass a trademark check).
- Free, no monetisation, no ads, no purchases, no accounts. This is a promise to parents, not just a current state.
- Reading font: Andika (SIL OFL), chosen because its letterforms match how children learn to write them.
- Licensing: fonts OFL/Apache only, icons Material (Apache 2.0), illustrations and sounds own or openly licensed.
- Interface languages: Ukrainian, German, English.

## Evidence on Hand

- No testimonials, user numbers, reviews, press or study results exist. Testing with real children (#25) and closed Play testing (#26) are still pending. Do not invent any of these.
- No sample texts ship with the app; do not add copyrighted texts (modern Fibel/textbooks, Sukhomlynskyi and similar are prohibited).
- Owner feedback after phone testing of v1: the split interface (parent zone, lock, onboarding, profiles) was too complicated, which led to the v1.1 simplification (#28).

## Product Principles

1. **Fewest steps between "I have a text" and "the child reads".** Every extra screen, button or choice needs a reason; v1.1 already cut what didn't earn its place.
2. **A 5–7-year-old must manage the reading screen alone.** It must work without reading menus or instructions.
3. **Encourage, never punish.** Natural reading behaviour is not an error; the child finishes with praise. The precise numbers are for the parent.
4. **Privacy is structural, not a setting.** Nothing leaves the phone; no feature may require a network beyond the one-time model download.
5. **Both languages are first-class.** Ukrainian and German texts, and uk/de/en interface, get equal care.

## Accessibility & Inclusion

- Early readers: large reading text with generous line spacing in Andika, large touch targets, high contrast, minimal text on the screens the child uses.
- Support system font scaling and TalkBack on the parent-facing screens.
- Smooth on low-end devices (2–3 GB RAM).
