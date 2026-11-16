---
version: 1
slug: "com-gefolgwar-readwithkid-mainactivity-kt-b0f41b06"
primary_target: "src/main/java/com/gefolgwar/readwithkid/MainActivity.kt"
related_targets: ["src/main/java/com/gefolgwar/readwithkid/LibraryScreens.kt","src/main/java/com/gefolgwar/readwithkid/SettingsScreens.kt","src/main/java/com/gefolgwar/readwithkid/Theme.kt"]
---

# App surface: library → text → reading → result

Scope: the whole Compose app (library, text editor, ready-to-read, reading, result, history, settings). Visitor mode: **Operate**. The reading screen is used by a 5–7-year-old alone; the rest by the parent.

## Direction contract

THESIS: The app is the fridge door where a family's letters live. Refuses the category default of pastel mascot cards and cream-and-serif "storybook", and refuses the clinical assessment dashboard.

OWN-WORLD: Cool enamel-white ground (blue-grey tint, never cream), white cards, near-black blue ink. Six glossy plastic magnet colours (red, blue, yellow, green, orange, purple), saturated, each with a darker same-hue edge for plastic thickness. Andika throughout; chunky Andika Bold letters are the "magnets". Rounded, chunky shapes. Standard Material 3 controls.

STORY: Parent sees their texts as coloured magnet letters, adds one in a single field, hands the phone over; the child taps one big button, reads, sees the word being read lift off the door, finishes with stars; the parent reads exact numbers.

FIRST VIEWPORT (library): top app bar "My texts" + settings icon; list of texts, each led by its first letter as a large coloured magnet glyph in that text's own colour, title, two-line preview, history and more actions; extended FAB "Add text" bottom-right. Reading screen: text in Andika 30sp, current line banded like a reading ruler, current word on a lifted yellow magnet tile.

FORM: candidate 4 of 7 (fridge magnetic letters); seed:c3bb0bddea2a3b39. Signature move: the current word snaps onto a lifted yellow magnet tile with a plastic edge, springing as it advances. Raises — from boarding pass: result as labelled segments with tabular figures; from labanotation: the line in progress banded across the text (Leselineal); from developer console: leaving the reading separated from Pause/Done by space; from character catalog: each text keeps one magnet colour from library to reading to result; from split-flap: result numbers count up in place.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Unresolved

- Dark theme: the app is light-only today; the use scene (a child reading at home, daylight or room light) keeps it light. Not added in this pass.
