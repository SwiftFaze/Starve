---
name: Starve
description: A personal feed set as a 90s OVA cassette cover. A red obi strip, a spec-panel release list, a tape-transport player deck; dark on ink, light on bone paper.
colors:
  ink: "#0c0c12"
  ink-raised: "#14141c"
  ink-sunk: "#08080c"
  ink-line: "#2a2a36"
  ink-line-strong: "#454556"
  bone: "#f0ece4"
  bone-dim: "#a3a2b3"
  bone-faint: "#85849a"
  obi-red: "#ff3b2f"
  obi-red-text: "#ff5a4f"
  scan-cyan: "#2fd2c4"
  paper: "#ebe8e0"
  paper-raised: "#f5f3ee"
  paper-sunk: "#dedad0"
  paper-line: "#cdc8bc"
  paper-line-strong: "#a39d90"
  ink-dim: "#4c4b5a"
  ink-faint: "#5e5c6a"
  paper-red: "#ef3326"
  paper-red-text: "#c0231a"
  deep-cyan: "#0a6f67"
  obi-ink-light: "#ffffff"
  stripe-light: "#ebe8e0"
typography:
  wordmark:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "0.01em"
  display:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "2.75rem"
    fontWeight: 800
    lineHeight: 1
  headline:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 800
    lineHeight: 1
  closing:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 800
    lineHeight: 1.05
  deck-number:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 800
    lineHeight: 1
  title:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 500
    lineHeight: 1.35
  deck-title:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 600
    lineHeight: 1.25
  body:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.55
    fontFeature: "tnum"
  catalogue:
    fontFamily: "Martian Mono, ui-monospace, Menlo, Consolas, monospace"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.2
    letterSpacing: "0.04em"
  meta:
    fontFamily: "Martian Mono, ui-monospace, Menlo, Consolas, monospace"
    fontSize: "0.75rem"
    fontWeight: 400
    lineHeight: 1.3
  nav:
    fontFamily: "Martian Mono, ui-monospace, Menlo, Consolas, monospace"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1
    letterSpacing: "0.06em"
  label:
    fontFamily: "Martian Mono, ui-monospace, Menlo, Consolas, monospace"
    fontSize: "0.6875rem"
    fontWeight: 500
    lineHeight: 1
    letterSpacing: "0.06em"
rounded:
  none: "0"
spacing:
  xs: "0.25rem"
  sm: "0.5rem"
  row: "0.875rem"
  md: "1.25rem"
  lg: "1.75rem"
  gutter: "2rem"
  gutter-narrow: "1rem"
components:
  button-outline:
    backgroundColor: "transparent"
    textColor: "{colors.bone}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "0.5rem 0.875rem"
    height: "2.25rem"
  button-outline-hover:
    backgroundColor: "{colors.bone}"
    textColor: "{colors.ink}"
  button-filled:
    backgroundColor: "{colors.bone}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "0.5rem 0.875rem"
    height: "2.25rem"
  button-filled-hover:
    backgroundColor: "transparent"
    textColor: "{colors.bone}"
  button-playing:
    backgroundColor: "{colors.obi-red}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
  placement-active:
    backgroundColor: "transparent"
    textColor: "{colors.scan-cyan}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
  field:
    backgroundColor: "{colors.ink-sunk}"
    textColor: "{colors.bone}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: "0.5rem 0.75rem"
    height: "2.5rem"
  obi:
    backgroundColor: "{colors.obi-red}"
    textColor: "{colors.ink}"
    typography: "{typography.display}"
    width: "5.5rem"
  release-row:
    backgroundColor: "transparent"
    textColor: "{colors.bone}"
    typography: "{typography.title}"
    padding: "0.875rem 0.75rem"
  release-row-hover:
    backgroundColor: "{colors.ink-raised}"
  player-deck:
    backgroundColor: "{colors.ink-raised}"
    textColor: "{colors.bone}"
    height: "5.5rem"
---

# Design System: Starve

## Overview

**Creative North Star: "The Obi Deck"**

Starve is drawn as a 90s OVA cassette cover. A red obi strip runs down the left edge and names the series (the current group) in vertical Akira caps, closed at the foot by caution chevrons. The release list beside it is the cover's spec panel: hairline-ruled rows keyed by STV catalogue numbers, with registration crosses at the sheet head's corners. Along the bottom, while something plays, a tape-transport deck shows the art, the STV number in red, the title, and a waveform that fills red as it plays and is also the seek bar. A playing video sits in a theatre pane beside the list, in a floating mini window, or in a 16:9 slot standing up out of the deck.

The same drawing exists twice. Dark is the cover on ink and light is the cover on bone paper, chosen by `prefers-color-scheme`. Both keep square corners, outlined mono controls, and a fine film grain over everything. Display type is loud and expanded, but reading text stays in mixed-case Archivo at reading sizes. The poster attitude belongs to the frame. The list itself is for reading.

The world rejects the reader/music-app default of a grey sidebar, rounded cards and pill buttons. It also rejects all-caps reading text.

**Key Characteristics:**
- A full-height red obi band (5.5rem) at the left on desktop, a horizontal red band under the top bar on phones.
- A spec-panel list: STV number, art cell, title over source, date, actions, all on hairline rules.
- A fixed player deck with a red top rule and a waveform seek bar.
- Three type voices with strict jobs: Akira for display, Archivo for reading, Martian Mono for numbers and controls.
- Square corners everywhere, with no shadows. Depth comes from tone, rules and grain.
- Red means obi or live. Cyan means current placement, focus or active splitter.

## Colors

Ink, bone and two signals: an obi red that says "this is the series / this is playing" and a scanline cyan that says "this is where focus or the video is". Every color has a dark value and a light value; the light palette is the same drawing on paper.

### Primary
- **Obi Red** (dark `obi-red`, light `paper-red`): the obi band, the deck's top rule and waveform fill, the playing row's number cell and Play button, the video frame's border, the resize grip, and the "no art" block in the deck. Text on it is always ink (`ink`), in both themes.
- **Obi Red Text** (dark `obi-red-text`, light `paper-red-text`): red used *as text*. It covers the deck's STV number, the "Watch on YouTube" fallback link and failure lines. It is lighter than Obi Red on ink and darker on paper so it reads at body sizes.

### Secondary
- **Scan Cyan** (dark `scan-cyan`, light `deep-cyan`): the focus outline (2px, offset 2px), the focused field border, the pressed Pane/Mini/Deck button, and the splitter handle while it is hovered, focused or dragged.

### Neutral
- **Ink** (`ink`): the dark ground. It is also the text color in light mode and the text on red in both themes.
- **Ink Raised / Paper Raised** (`ink-raised`, `paper-raised`): row hover, the playing row and the player deck.
- **Ink Sunk / Paper Sunk** (`ink-sunk`, `paper-sunk`): field wells and empty thumbnail backing.
- **Hairline / Strong Hairline** (`ink-line`, `ink-line-strong`; light `paper-line`, `paper-line-strong`): row rules, region dividers, unplayed waveform bars, the volume track, unpressed placement borders and the scrollbar.
- **Bone** (`bone`): dark-mode text, the button outline color (the outline always matches the text), and the fill of the filled button.
- **Bone Dim / Ink Dim** (`bone-dim`, `ink-dim`): secondary text such as sources, dates, field labels, hints, times and inactive tabs.
- **Bone Faint / Ink Faint** (`bone-faint`, `ink-faint`): the most muted tier, for read rows, placeholders and registration crosses; still AA on every ground, but meant to recede (see Do's and Don'ts).

### Named Rules
**The Obi Red Rule.** Red is for the obi and for live/playing state only, meaning what plays now and the video that is playing. Never use it as a general accent, link color or emphasis.

**The Scanline Rule.** Cyan means current placement, focus or active splitter, nothing else. If it isn't telling you where focus is or where the video lives, it isn't cyan.

**The Same Drawing Rule.** Every surface ships in both themes from the same tokens. A new color needs a dark value and a light value, or it doesn't ship.

## Typography

**Display Font:** Akira Expanded (800, `font-stretch: 125%` so the Archivo fallback widens too), with Archivo, system-ui fallback. The font is user-supplied and personal-use only. It is gitignored and never committed.
**Body Font:** Archivo (variable 100–900, width 62–125%), self-hosted OFL WOFF2, latin + latin-ext.
**Label/Mono Font:** Martian Mono (variable 100–800), self-hosted OFL WOFF2.

**Character:** A loud expanded poster face used only for names and headings, a plain grotesque that carries every word you actually read, and a technical mono that turns catalogue numbers, dates and controls into spec-sheet data. Tabular figures are on globally.

### Hierarchy
- **Display** (Akira 800, 2.75rem; 2.125rem for Sources/Import obi names; `clamp(1.25rem, 7vw, 1.75rem)` on the horizontal phone obi; uppercase, vertical-rl rotated 180deg on desktop, ellipsis at 70% of the band): the series name on the obi.
- **Headline** (Akira 800, 1.75rem, 1; 1.25rem below 60rem; uppercase, balanced): the sheet heading.
- **Closing** (Akira 800, 1.5rem, 1.05; uppercase, under a full-weight rule): "You're all caught up".
- **Wordmark** (Akira 800, 1.125rem, uppercase, 0.01em): STARVE in the top bar.
- **Deck Number** (Akira 800, 1.125rem; 0.875rem below 60rem; Obi Red Text): the playing STV number in the deck.
- **Title** (Archivo 500, 1rem, 1.35, mixed case): release and source titles; underline only on hover; wraps anywhere rather than overflow.
- **Deck Title** (Archivo 600, 0.9375rem, 1.25): the playing title, one line, ticker when too long.
- **Body** (Archivo 400, 0.9375rem, 1.55): page default; notes at 1rem, hints at 0.8125rem/1.45. Sheet notes cap at 40rem.
- **Catalogue** (Martian Mono 500, 0.75rem, 0.04em): STV/SRC numbers at the head of each row.
- **Meta** (Martian Mono 400, 0.75rem, 1.3): source names, dates, URLs, fetch times, elapsed/total.
- **Nav** (Martian Mono 500, 0.75rem, 0.06em, uppercase): Timeline/Sources tabs and group links.
- **Label** (Martian Mono 500, 0.6875rem, 0.06em, uppercase): every button and field label (field labels 0.08em). Obi meta runs at 0.14em.

### Named Rules
**The Three Voices Rule.** Akira is for display only: the wordmark, the obi series name, sheet headings, the deck STV number and "caught up". Archivo, in mixed case, carries every title and all body text. Martian Mono carries STV/SRC numbers, dates, sources and control labels. Never set reading text in Akira, and never set reading text in all caps.

## Layout

**Desktop grid.** A sticky top bar (3.5rem) holds the wordmark, the page tabs and the scrolling group tabs, which are separated by a hairline. Below it is a two-column label grid. The first column is the sleeve: the obi band (5.5rem) pinned full height between bar and deck. On Sources and Import a 22rem tool panel sits beside the obi. The second column is the sheet, padded 1.75rem top, 2rem gutter, 4rem bottom. The deck is fixed to the viewport bottom, and the page reserves its height through `--deck-h`, which the script measures.

**Release rows.** The wide row is five columns: number 7rem, art 4rem, title flexible, date 12.5rem, actions 13.5rem fixed. The fixed date and action columns keep every date aligned no matter which buttons a row has. A row with no art keeps an empty art cell so every row stays aligned. Rows are 0.875rem by 0.75rem, bleed into the gutter with a negative margin, and sit on a hairline.

**The sheet is a container** (`container: sheet / inline-size`), so rows respond to the sheet's width rather than the viewport. That matters when the video pane narrows the list.
- Below 68rem, source rows move status under the title.
- Below 64rem, rows fold. Art (3.5rem) sits on the left, number and date share the top line, the title goes under the number, and the buttons go under the date on the right.
- Below 44rem, the sheet head wraps and its actions take a full line.
- Below 34rem, the buttons drop under the title, left-aligned.

**Video placements.** Play opens a YouTube item in the theatre pane. A desktop user can switch between Pane, Mini and Deck. Only the video's box moves; the iframe is never re-parented, so playback never restarts.
- **Pane:** the label grid gains a third column. The list sits at a list width that defaults to 720px and is remembered. The video fills the rest, inset 1.75rem from bar and deck.
- **Splitter:** a 0.75rem drag zone on the list's right edge, with a 0.25rem × 3rem handle that grows to 5rem in cyan when active. Below 610px the list folds to an STV strip (8.5rem) of catalogue numbers only. Double-clicking resets it to 720px. Arrow keys step it 32px.
- **Mini:** a 16:9 window above the deck's right end, starting at 320px and resizable from 240 to 720px.
- **Deck:** a 16:9 slot standing up out of the deck's right end, starting at 384px and resizable from 288 to 640px. The deck's own columns tighten to make room, and the sheet's bottom padding clears the slot.
- Mini and deck video sizes are remembered per placement by a top-left grip.

**Below 60rem (phones).** The gutter drops to 1rem. The bar wraps, with group tabs on their own scrolling line. The obi turns horizontal as a red band under the bar, with the series on the left, meta on the right and no chevrons. The deck goes to two lines: art, text and Stop on top, transport below. Phones get no placement choice. The video always sits full width directly above the deck (16:9, max 40dvh), and the splitter and grip are hidden. Touch targets for row actions and fields rise to 2.75rem.

## Elevation & Depth

The system is flat, with no shadows anywhere. Depth comes from three things. The first is tone: ground, a raised surface for hover, the playing row and the deck, and a sunk surface for fields. The second is rules: hairlines between rows, a full-weight text-colored rule under sheet heads and above "caught up", and a red rule on the deck's top edge and around the video. The third is z-order of fixed layers: video 25, splitter 26, deck 30, grain 50. A fractal-noise grain covers everything at 6% opacity in dark and 8% in light, overlay-blended, and never catches the pointer.

### Named Rules
**The Hairline Rule.** Regions are separated by 1px rules, never by shadows or cards. The strongest separation is a 1px rule in the text color.

## Shapes

Every corner is square (`border-radius: 0`), including buttons, fields, the volume thumb, thumbnails, the video frame and the grip. The recurring cover geometry has four parts. Caution chevrons are 45deg stripes of ink on red that close the obi. Registration crosses are 0.75rem hairline crosses outside the sheet head's top corners. Small solid squares are the 0.5rem current-tab marker and the square volume thumb. Number cells are solid blocks behind the playing STV number. Adjacent segmented buttons overlap by 1px so they share one rule.

## Components

### Buttons
Outlined mono controls that read as printed spec labels.
- **Shape:** square (0), 1px border in the text color, min-height 2.25rem (2.75rem in row actions on phones), padding 0.5rem 0.875rem.
- **Outline (default):** transparent with text-color label. Hover inverts to a text-color fill with ground-color label (150ms).
- **Filled:** the one filled control on a surface, inverting on hover. Examples are Mark all read on the sheet head and Add/Import in the tool panel.
- **Playing:** the playing row's Play button fills with Obi Red, label in ink.
- **Disabled / busy:** 45% opacity when disabled, with no hover. During an htmx request the opacity is 55% with a progress cursor.
- **Load more:** a full-width outline bar 3.25rem tall.

### Chips
- **Failing tag:** a solid red block with an ink mono label, uppercase, padded 0.25rem by 0.375rem, ahead of a failing source's error. This predates the Obi Red Rule and falls outside it; don't extend it to new surfaces.

### Inputs / Fields
- **Style:** a sunk well with a 1px strong hairline, square, Archivo body text, a red caret, and faint placeholders. Min-height is 2.5rem (2.75rem and 1rem text on phones). Selects draw their own two-triangle chevron in the text color. File inputs use a mono body with an outlined mono selector button.
- **Focus:** the border turns cyan, along with the global 2px cyan outline.
- **Labels:** mono uppercase field labels in the dim text color, and hints in body 0.8125rem.

### Navigation
- **Top bar:** sticky on desktop and static on phones. Tabs and group links are mono uppercase in the dim color and turn to the text color on hover. The current page or group is marked by a 0.5rem square before the label. The build draws it in red, which falls outside the Obi Red Rule; don't copy that use of red.

### The Obi (signature)
The red spine. It holds the series name, set vertically in Akira and reading bottom to top. Mono meta below it gives "n unread", the sort order and the STV range, semibold. Its text is the obi ink: dark ink on red in the dark theme, white in the light theme (`--on-obi`). On Sources it carries a failing count as a block inverted in that ink. It is full height between bar and deck and never scrolls with the list.
- **Theme switch:** the caution chevrons at its foot are a button that flips dark and light. The choice is stored in localStorage (`starve.theme`) and applied from `<head>` before the first paint; with nothing stored, the OS decides. The stripes sit on a 1rem square tile (`--stripe`: ink in dark, bone in light) and march sideways on hover. On phones the chevrons end the horizontal band.

### The Mark (signature)
The logo and favicon: the obi tile carrying two overlapping squares in obi ink, one solid and one outlined, with the overlap showing the tile red. It follows the theme like the obi does. Its construction, colours and rules live in `docs/brand.md`.

### The Shutter (signature)
Every theme change and every Timeline/Sources switch runs behind one red panel that sweeps across the whole viewport: 1.25s in, a 0.4s hold while covered, 1.25s out, all `cubic-bezier(0.65, 0, 0.35, 1)`. Chevron bands lead and trail it. It is dressed as an obi spec sheet: an inset hairline frame with registration crosses, mono corner captions (STARVE, NO ALGORITHM, `[ THEME ]` or `[ PAGE ]`), a barcode with a code derived from the label, a chevron strip, the destination in Akira over a thin rule and a mono caption, and a faint scanline. On a theme change it wears the destination theme's obi ink from the start, so it never flips colour mid-cover.
- **Page switches never reload:** the next page is fetched while the panel sweeps in, swapped in place under it, the URL pushed, and the same panel sweeps out, so nothing blinks. Modified clicks, back/forward and a failed fetch fall back to an ordinary page load. Leaving the timeline this way stops whatever plays, as a page load does.
- **The page you are on:** clicking its tab glides back to the top, or, already at the top, blinks the tab's red marker twice. No reload.
- Under reduced motion there is no panel: the theme or page changes at once.

### Release Row
Its parts are the STV number, the art cell (empty if no art), the title over source, the date and the actions (Play, Mark read). On hover the row moves to the raised surface. A read row turns its number and title to the faint tier, with the title struck through in a strong hairline. The playing row stays raised, its STV number becomes a red cell, and its Play button fills red.

### Player Deck (signature)
A fixed bottom bar with a minimum height of 5.5rem, on the raised surface with a 1px red top rule. From left to right it holds the art (5.5rem square, or a red block with the STV number in mono when there is no art), the text stack (red Akira STV number, Archivo title and mono source), the transport, the placement switch for YouTube only, and Stop.
- **Transport:** a 2.5rem square play/pause, the elapsed time, the waveform and the total time.
- **Waveform:** 3rem tall, with bars in the strong hairline color. A second red copy is revealed by a clip-path as playback progresses. A transparent range input over it seeks on release. Its focus shows as a cyan outline offset 4px.
- **Volume:** a 2px level line with a square 0.75rem thumb, 6rem wide. The level is remembered in localStorage.
- **Ticker:** a title too long for the deck holds at the start, slides to show the end, holds and returns. The cycle is at least 6s, ease-in-out, and pauses on hover. Under reduced motion it falls back to an ellipsis with no animation.
- **SoundCloud fallback:** if the widget's handshake never arrives, the transport hides and SoundCloud's own player shows above the deck in a red frame.

### Video Box
A black 16:9 box with a 1px red border that moves between placements over 240ms. The YouTube embed runs with `controls=0`. A transparent shield over the video blocks YouTube's hover overlay. A click on the shield plays or pauses, and a double-click goes fullscreen, which drops the border. The grip is a 1.75rem red square at the top-left of the mini and deck placements. It inverts on hover and resizes by pointer or arrow keys. While resizing, the iframe ignores the pointer and nothing animates. YouTube's title bar reappearing after a seek is an accepted platform limitation, not something to design around.

### Message
A flash status line in the tool panel, with a 1px border and the text wrapped in mono brackets.

## Do's and Don'ts

### Do:
- **Do** set the wordmark, obi series name, sheet headings, the deck STV number and "caught up" in Akira Expanded 800, uppercase, at `font-stretch: 125%`.
- **Do** set every title and all body text in Archivo, mixed case. Set STV/SRC numbers, dates, sources and control labels in Martian Mono.
- **Do** ship every color in both themes, following `prefers-color-scheme` unless the obi switch has set `data-theme` on the root.
- **Do** keep row dates aligned with the fixed 13.5rem actions column, and keep an empty art cell when a row has no art.
- **Do** let rows respond to the sheet's container width (64rem fold, 34rem buttons-under-title), not the viewport.
- **Do** keep state changes at 150ms (`cubic-bezier(0.16, 1, 0.3, 1)`), placement moves at 240ms and the list fold at 280ms. Under `prefers-reduced-motion`, use no transitions, no fold animation and no ticker.
- **Do** move only the video's box between placements, never the iframe, so playback never restarts.
- **Do** give phones a single placement: video full width above the deck.
- **Do** run theme changes and page switches through the shutter, at one speed, and keep the scrollbar gutter reserved (`scrollbar-gutter: stable`) so switching pages never changes the page width.

### Don't:
- **Don't** use Akira for reading text, and don't set reading text in all caps.
- **Don't** use red for anything but the obi and live/playing state.
- **Don't** use cyan for anything but current placement, focus or the active splitter.
- **Don't** round corners, use pill buttons or cards, or add shadows.
- **Don't** use the faint text tier (`bone-faint` / `ink-faint`) as the main voice: it clears 4.5:1 on every ground (≥4.68:1) but exists to recede, for read rows and placeholders.
- **Don't** commit the Akira font files. They are personal-use and gitignored.
- **Don't** colour the page scrollbar: it stays the strong hairline on the ground (tried in red and rejected).
