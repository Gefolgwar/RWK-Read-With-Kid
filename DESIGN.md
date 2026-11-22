---
name: Read With Kid
description: The fridge door where a family's letters live; a child reads aloud alone, the parent reads exact numbers.
colors:
  magnet-blue: "#1D5BD8"
  magnet-blue-edge: "#113A8F"
  magnet-blue-container: "#DDE7FF"
  ink-on-blue-container: "#0A2463"
  magnet-red: "#E0322B"
  magnet-red-edge: "#9E1C17"
  magnet-green: "#14975A"
  magnet-green-edge: "#0A6339"
  magnet-green-container: "#D5F2E2"
  ink-on-green-container: "#053A20"
  magnet-orange: "#F06A0E"
  magnet-orange-edge: "#A84604"
  magnet-orange-container: "#FFE6D3"
  magnet-purple: "#7A45D6"
  magnet-purple-edge: "#4F2794"
  magnet-yellow: "#FFC20E"
  magnet-yellow-edge: "#D69A00"
  magnet-yellow-container: "#FFF1C4"
  enamel-door: "#EFF2F7"
  card-white: "#FFFFFF"
  door-low: "#F7F9FC"
  door-container: "#E9EDF3"
  door-tonal: "#E3E8F0"
  door-highest: "#DCE2EB"
  blue-black-ink: "#161B26"
  slate-ink: "#475062"
  steel-outline: "#6E7789"
  hairline: "#CCD3DE"
  error-red: "#C0261C"
  error-container: "#FFE3E0"
  ink-on-error-container: "#5F0D08"
typography:
  headline-medium:
    fontFamily: "Andika, sans-serif"
    fontSize: "28px"
    fontWeight: 700
    lineHeight: "36px"
    letterSpacing: "0px"
  headline-small:
    fontFamily: "Andika, sans-serif"
    fontSize: "24px"
    fontWeight: 700
    lineHeight: "32px"
    letterSpacing: "0px"
  title-large:
    fontFamily: "Andika, sans-serif"
    fontSize: "22px"
    fontWeight: 700
    lineHeight: "28px"
    letterSpacing: "0px"
  title-medium:
    fontFamily: "Andika, sans-serif"
    fontSize: "16px"
    fontWeight: 700
    lineHeight: "24px"
    letterSpacing: "0.15px"
  title-small:
    fontFamily: "Andika, sans-serif"
    fontSize: "14px"
    fontWeight: 700
    lineHeight: "20px"
    letterSpacing: "0.1px"
  body-large:
    fontFamily: "Andika, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "24px"
    letterSpacing: "0.5px"
  body-medium:
    fontFamily: "Andika, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
    letterSpacing: "0.25px"
  body-small:
    fontFamily: "Andika, sans-serif"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: "16px"
    letterSpacing: "0.4px"
  label-large:
    fontFamily: "Andika, sans-serif"
    fontSize: "14px"
    fontWeight: 700
    lineHeight: "20px"
    letterSpacing: "0.1px"
  reading:
    fontFamily: "Andika, sans-serif"
    fontSize: "30px"
    fontWeight: 400
    lineHeight: "52px"
    letterSpacing: "0.5px"
  reading-page:
    fontFamily: "Andika, sans-serif"
    fontSize: "24px"
    fontWeight: 400
    lineHeight: "38px"
    letterSpacing: "0.5px"
  editor:
    fontFamily: "Andika, sans-serif"
    fontSize: "20px"
    fontWeight: 400
    lineHeight: "30px"
    letterSpacing: "0.5px"
  magnet-letter:
    fontFamily: "Andika, sans-serif"
    fontSize: "44px"
    fontWeight: 700
    lineHeight: "44px"
  figures:
    fontFamily: "Andika, sans-serif"
    fontFeature: "tnum"
rounded:
  xs: "8px"
  magnet-tile: "10px"
  sm: "12px"
  reading-band: "14px"
  md: "16px"
  lg: "24px"
  xl: "32px"
  pill: "9999px"
spacing:
  xxs: "4px"
  xs: "8px"
  sm: "12px"
  md: "16px"
  lg: "20px"
  xl: "24px"
  fab-clearance: "96px"
components:
  button-child-primary:
    backgroundColor: "{colors.magnet-blue}"
    textColor: "{colors.card-white}"
    typography: "{typography.title-large}"
    rounded: "{rounded.xl}"
    padding: "12px"
    height: "72px"
  button-child-primary-stacked:
    backgroundColor: "{colors.magnet-blue}"
    textColor: "{colors.card-white}"
    typography: "{typography.title-large}"
    rounded: "{rounded.xl}"
    padding: "12px"
    height: "96px"
  button-child-tonal:
    backgroundColor: "{colors.door-tonal}"
    textColor: "{colors.blue-black-ink}"
    typography: "{typography.title-large}"
    rounded: "{rounded.xl}"
    padding: "12px"
    height: "96px"
  button-parent-primary:
    backgroundColor: "{colors.magnet-blue}"
    textColor: "{colors.card-white}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.pill}"
    height: "56px"
  button-parent-outlined:
    backgroundColor: "{colors.enamel-door}"
    textColor: "{colors.slate-ink}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.pill}"
    height: "56px"
  button-tonal-small:
    backgroundColor: "{colors.door-tonal}"
    textColor: "{colors.blue-black-ink}"
    typography: "{typography.label-large}"
    rounded: "{rounded.pill}"
  fab-add-text:
    backgroundColor: "{colors.magnet-blue}"
    textColor: "{colors.card-white}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.md}"
  card:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.blue-black-ink}"
    rounded: "{rounded.lg}"
    padding: "16px"
  card-reading-page:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.blue-black-ink}"
    typography: "{typography.reading}"
    rounded: "{rounded.lg}"
    padding: "24px 20px"
  field-text:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.blue-black-ink}"
    typography: "{typography.editor}"
    rounded: "{rounded.lg}"
  reading-band:
    backgroundColor: "{colors.magnet-blue-container}"
    rounded: "{rounded.reading-band}"
  magnet-tile-current:
    backgroundColor: "{colors.magnet-yellow}"
    textColor: "{colors.blue-black-ink}"
    rounded: "{rounded.magnet-tile}"
    padding: "0 7px"
  word-tile-retried:
    backgroundColor: "{colors.magnet-yellow-container}"
    textColor: "{colors.blue-black-ink}"
    rounded: "{rounded.sm}"
    padding: "8px 14px"
  word-tile-helped:
    backgroundColor: "{colors.magnet-blue-container}"
    textColor: "{colors.ink-on-blue-container}"
    rounded: "{rounded.sm}"
    padding: "8px 14px"
  hint-hard-parts:
    backgroundColor: "{colors.magnet-orange-container}"
    textColor: "{colors.blue-black-ink}"
    typography: "{typography.body-medium}"
    rounded: "{rounded.md}"
    padding: "12px 16px"
---

# Design System: Read With Kid

## Overview

**Creative North Star: "The Fridge Door"**

The app is the fridge door where a family's letters live. The ground is a cool enamel white with a blue-grey tint, never cream; white cards sit on it like paper held up on the door; ink is a near-black blue. Colour comes from one source only: six saturated plastic magnet colours, each with a darker same-hue edge that shows the plastic's thickness. Every text the parent adds is represented by its first letter as a chunky Andika Bold magnet, and that magnet's colour follows the text from library to reading to result.

The system is deliberately two-handed. The parent screens (library, editor, history, settings) are calm, standard Material 3: hand-rolled headers matching the large top app bar, filled white cards, pill buttons, a primary extended FAB. The child's screens (ready-to-read, reading, result) use the same tokens at child scale: 72 to 96dp buttons with a big icon and a short title-large label, reading text at 30sp with 52sp line height,. No marker chases the current word: a misheard word must never make the child stop and wait for it.

Depth is physical only where the world is physical. Magnets stand off the door with a darker edge, a soft drop shadow and a gloss across the top; everything else is flat tonal layering. The system rejects the category defaults it replaced: pastel mascot cards, cream-and-serif "storybook", and the clinical assessment dashboard.

The app follows the system theme. The dark door is the same world at night: dark blue-grey enamel (#11151D), cards one step lighter (#1B212C), light ink (#E6EAF2), a lighter magnet blue for actions (#6E98FF). The magnets keep their plastic faces; only their pale containers turn into deep tints, syllables switch to light blue and red, and the word on the yellow tile is always in dark ink.

**Key Characteristics:**
- Cool blue-grey enamel ground, white cards, near-black blue ink.
- Six glossy magnet colours, each a face, a darker edge and a pale container.
- Andika throughout; Bold for every heading, title and label, Regular for body and reading.
- Each text owns one magnet colour, derived from its id, everywhere it appears.
- Yellow belongs to the reading moment: the reward and retried words.
- Rounded, chunky shapes; standard Material 3 controls.

## Colors

Saturated plastic on cool enamel: the ground and ink stay quiet so the magnets carry all the colour.

### Primary
- **Magnet Blue** (magnet-blue): every primary action (Start, Done, Back to text, Save, the Add text FAB), the M3 `primary` role. Its pale container (magnet-blue-container) is the band of the sentence to read and the "helped" word tile; ink on that container is ink-on-blue-container. Bright Magnet Blue underlines the words read so far in the sentence being read; at 40% opacity it marks a tentatively heard word.

### Secondary
- **Magnet Green Edge** (magnet-green-edge): the M3 `secondary` and `tertiary` roles. Bright Magnet Green (magnet-green) underlines a word read correctly in a read sentence; its pale container tints a read sentence, on the page and on the result.

### Tertiary
- **Magnet Orange** (magnet-orange): its container (magnet-orange-container) backs the hard-to-recognise hint in the editor, with magnet-orange-edge for the hint's icon.

### The text magnets
- **Magnet Red, Blue, Green, Orange, Purple**, each a face over its edge, are the five colours a text can own. `Magnets.forText(id)` picks one by `id mod 5`, so texts added one after another get different colours and a text keeps its colour forever.
- **Magnet Red** also marks mistakes: its face underlines a misread or skipped word in a read sentence, and its pale container tints a sentence read with too many mistakes. What the phone heard in place of a misread word is written above it in the M3 error colour.
- **Magnet Yellow** (magnet-yellow over magnet-yellow-edge) is not in that set. It is the current-word tile and the earned reward stars; its pale container tints the retried-word tiles on the result.
- **Syllable colours**: when coloured syllables are on, syllables alternate magnet-blue-edge and magnet-red-edge, starting each word with blue; the edges are dark enough to read on the band.

### Neutral
- **Enamel Door** (enamel-door): the background and surface, the whole app's ground.
- **Card White** (card-white): every card, the reading page, the text field, popups (M3 `surfaceContainerLowest`).
- **Door Tonal** (door-tonal): tonal buttons (Pause, Continue, Paste) and the selected segment, M3 `secondaryContainer`, `surfaceVariant` and `surfaceContainerHigh`. Door Low, Door Container and Door Highest fill the remaining M3 container steps.
- **Blue-Black Ink** (blue-black-ink): all primary text and icons.
- **Slate Ink** (slate-ink): secondary text (language line, dates, hints, counts) and quiet icons.
- **Steel Outline** (steel-outline): M3 `outline`, the segmented-button stroke.
- **Hairline** (hairline): dividers, the unfocused field stroke, unearned star outlines.
- **Error Red** (error-red) with error-container and ink-on-error-container: model and microphone errors only.

### Named Rules
**The One Owner Rule.** A text has exactly one magnet colour, from its id across the five text colours, and it shows that colour on the library card, the text heading, the reading header and the result heading. Never pick a text's colour by hand or by position in a list.

**The Yellow Moment Rule.** Yellow is never a text's colour and never an action colour. It is the reward (earned stars) and the trace of the moment (retried words).

**The Enamel, Not Cream Rule.** The ground is cool blue-grey (enamel-door). A warm cream or beige ground is the look this system replaced.

## Typography

**Display Font:** Andika Bold (sans-serif fallback)
**Body Font:** Andika Regular (sans-serif fallback)

**Character:** One family, chosen because its letterforms match how children learn to write them (a single-storey "a", an "I" distinct from "l"). Bold weight makes the headings and the magnets chunky; Regular keeps reading text open.

### Hierarchy
Sizes follow Material 3's type scale with Andika swapped in; values in px are dp/sp at 100% font scale.
- **Headline Medium** (700, 28/36): screen titles (My texts, New text) and the praise line on the result.
- **Headline Small** (700, 24/32): settings-level screen headers and the result figures, with tabular numbers.
- **Title Large** (700, 22/28): child button labels, the large text heading, the empty-library line, the word-check popup.
- **Title Medium** (700, 16/24): card titles, parent button and FAB labels, section titles, compact text heading.
- **Title Small** (700, 14/20): field labels (Text language).
- **Body Large / Medium / Small** (400, 16/24, 14/20, 12/16): body, list rows, secondary lines, footnotes.
- **Label Large** (700, 14/20): small Material buttons and segments.
- **Reading** (400, 30/60): the reading text. **Reading Page** (400, 24/38) previews it before Start; **Editor** (400, 20/30) is the text field; result word tiles use 22sp.
- **Magnet Letter** (700, 44, line height equal to size): the text's first letter as a magnet; 72 in the empty library, 40 in the heading, 32 in the reading header, 28 compact.

### Named Rules
**The One Family Rule.** Andika for everything, including numbers and controls. No second typeface.

**The Steady Figures Rule.** Any number that changes or is compared (result figures, counts, history accuracy, download progress) uses tabular figures (`tnum`).

## Layout

A single phone column. Every screen shares one Scaffold with a 16dp content inset (md); screens are columns with a header row at the top (8dp top offset, back or settings IconButton) and, on the child screens, a full-width button pinned to the bottom with 16dp above it. Content between scrolls.

Rhythm is a 4dp base: 4 for hairline nudges, 8 between a label and its control, 12 between cards in a list and between paired buttons, 16 between sections and inside cards, 20 to 24 inside the reading page and for result word-list groups. Lists reserve 96dp (fab-clearance) at the bottom so the last card clears the extended FAB.

Child screens hold at most two actions, side by side with 12dp between them (Pause or Continue, Done); leaving the reading sits alone in the header, separated from them by the whole page. The reading page fills the remaining height and auto-scrolls so the current line sits a third of the way down.

System font scaling is supported: the child buttons grow from a minimum height rather than a fixed one, and headers wrap titles to two lines.

## Elevation & Depth

Flat tonal layering for the interface, physical depth for the magnets. Cards are filled white on the enamel ground with no shadow; separation comes from the tone step alone. Material defaults supply the FAB's elevation and the word-check popup (8dp shadow).

Magnets are the one place depth is drawn on purpose. A magnet letter is drawn twice: its edge colour offset down by 7% of the letter size, casting a soft shadow, with the face on top under a white gloss. The current-word tile is a rounded rectangle with a 4dp edge below it, a soft shadow and the same gloss. Earned stars repeat it: an edge-coloured star 4dp below a glossy yellow face.

### Shadow Vocabulary
- **Magnet letter shadow** (edge text cast by `rgba(16,24,40,0.2)`, y offset 1.6 x thickness, blur 2.5 x thickness, thickness = 7% of size): under every magnet letter.
- **Magnet tile shadow** (`rgba(16,24,40,0.25)`, y 3dp, blur 8dp, cast by the edge): under the current-word tile.
- **Popup** (M3 8dp shadow elevation): the word-check popup over a tapped word.

### Named Rules
**The Only Magnets Stand Off Rule.** The edge-plus-shadow-plus-gloss treatment belongs to magnets: letters, the current-word tile, earned stars. Cards, buttons and fields stay flat.

## Shapes

Rounded and chunky throughout: the M3 shape scale is raised to 8 / 12 / 16 / 24 / 32dp (xs, sm, md, lg, xl). Cards, the reading page, the text field and popups use lg (24dp). Child buttons use xl (32dp), which at 72dp tall reads as a fat pill. Parent Material buttons keep their default full pill. Result word tiles use sm (12dp); the editor hint uses md (16dp). The reading band rounds at 14dp. Reading-progress underlines are 4dp strokes with round caps.

## Components

### Buttons
- **Child button** (ChildButton): big icon (32dp) and a short Title Large label, xl corners, 12dp padding, minimum height 72dp, or 96dp when stacked icon-over-label to share a row. Primary is a Magnet Blue filled button; secondary (Pause, Continue) is the tonal door-tonal button. Icons are Material, labels say the action in one word.
- **Parent buttons:** standard M3 Button and OutlinedButton at a minimum 56dp, Title Medium label, full pill; paired 50/50 with 12dp between (Cancel, Save).
- **Small tonal button:** M3 FilledTonalButton with an 18dp icon (Paste in the editor header).
- **Extended FAB:** Add text, bottom-end, Magnet Blue, Title Medium label.
- **Hover / Focus:** Material 3 default state layers and ripples; nothing custom.

### Chips
Not used. Language choice is an M3 single-choice segmented button row (selected segment door-tonal with a check, steel-outline stroke).

### Cards / Containers
- **Corner Style:** lg (24dp).
- **Background:** card-white on enamel-door.
- **Shadow Strategy:** none; tonal step only (see Elevation & Depth).
- **Border:** none.
- **Internal Padding:** 16dp for rows and status cards (list cards 16 start, 12 vertical, 4 end beside IconButtons); 20 to 24dp around reading text.
- **Divider:** hairline inset 16dp between result rows; 56dp inset in settings lists.

### Inputs / Fields
- **Style:** M3 OutlinedTextField, card-white fill, hairline stroke at rest, lg corners, Editor type (20/30).
- **Focus:** M3 default, stroke shifts to Magnet Blue.
- **Error / Disabled:** M3 defaults; errors elsewhere are Error Red body text in a status card.

### Navigation
No app bar component. Each screen opens with a hand-rolled header row matching the M3 large top app bar: a Headline Medium title with heading semantics and M3 IconButtons (settings on the library, back elsewhere). Text screens head with the TextHeading: the text's magnet letter beside its title (Title Large, or Title Medium when compact). Window insets and predictive Back have not yet been verified on hardware.

### Text card (library)
The library row: the text's magnet letter centred in a 52dp slot, 12dp gap, title (Title Medium, up to two lines) over the text's language (Body Medium, slate-ink), then History and More IconButtons. No preview line.

### Magnet letter
The system's signature glyph: a letter or digit from the title, uppercase, Andika Bold, drawn as a magnet in its text's colour (face over edge, shadow, gloss). Decoration only; hidden from TalkBack. The empty library shows three of them (red, blue, green) at 72sp above the empty-state line.

### Reading page and the current-word magnet
The reading text sits on a white lg card at 30/60. The child reads sentence by sentence. Progress is drawn behind the words so lines never reflow, each sentence tinted on each of its lines (14dp corners, 3dp beyond its words): the sentence to read is banded in magnet-blue-container, and the words read so far in it get a solid 4dp Magnet Blue underline, tentative ones the same at 40%. A misread or skipped word in it is not marked and nothing shows what the model heard: a mark would make the child stop and repeat the word, and speech recognition sometimes mishears a word read correctly. The current word itself is not marked either, for the same reason. Once a sentence is read it turns green (magnet-green-container), or red (magnet-red-container) when more than half of its words were misread or skipped; its words get a Magnet Green or Magnet Red underline, and what the phone heard sits above a misread word. Tapping a word of the sentence to read speaks it; tapping another sentence chooses it, also a red one to read it again.

### Result
Earned stars (72dp, glossy yellow magnets) pop in one after another; unearned ones stay as hairline outlines. A praise line in Headline Medium follows, then a white card of labelled rows (label in Body Large, figure in Headline Small with tabular figures, minimum 64dp) whose numbers count up in place. The sentences with mistakes follow, each as the child first read it on its own md card tinted green or red as on the page: its words in the reading font at 22sp, misread and skipped ones in the error colour and underlined, with what the phone heard above them in Body Medium. Word lists follow as tiles in the reading font at 22sp on a pale container: green for corrected when the sentence was read again, purple for uncertain (the phone could not check), yellow for retried, blue for helped.

### Motion
Motion is limited to the reading moment: the tile spring, the stars' pop, the count-up and the auto-scroll. With the system's Remove animations setting on, every one of them cuts to its end state. Motion has been verified only in JVM renders, not on hardware.

## Do's and Don'ts

### Do:
- **Do** give every text its magnet colour from `Magnets.forText(id)` and show that same colour wherever the text appears.
- **Do** keep cards card-white on the enamel-door ground with lg (24dp) corners and no shadow.
- **Do** use ChildButton (72dp minimum, 96dp stacked, xl corners, 32dp icon, Title Large label) on any screen the child operates, with at most two actions in reach.
- **Do** use Andika for every string, Bold for headings, titles and labels, and tabular figures for any changing number.
- **Do** draw reading progress behind the text, so a word's state never changes its layout.
- **Do** honour the Remove animations setting: every animation must have a cut-to-end path.
- **Do** keep standard Material 3 controls on the parent screens.

### Don't:
- **Don't** use yellow as a text's magnet colour or as an action colour.
- **Don't** put a cream, beige or warm off-white ground behind anything; the door is cool enamel.
- **Don't** add pastel mascot cards, storybook serif type or clinical dashboard chrome.
- **Don't** give cards, buttons or fields the magnet edge, offset shadow or gloss; that treatment is for magnets only.
- **Don't** introduce a second typeface.
- **Don't** mark a misread word while its sentence is being read; mistakes show once the sentence is read.
- **Don't** derive dark colours by inverting tokens; the dark door has its own scheme (AppDarkColors) and every new colour needs both values.
