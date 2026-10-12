---
name: Starve
description: A personal feed set as a record label's release sheet. Black ink, off-white paper, one signal orange.
colors:
  signal-orange: "#ff5a1f"
  ink: "#111111"
  paper: "#f2f2ee"
  muted-grey: "#5f5f5a"
  on-ink: "#f2f2ee"
  on-ink-muted: "#b4b4ad"
typography:
  display:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "2.75rem"
    fontWeight: 900
    lineHeight: 1.05
    letterSpacing: "0"
    fontVariation: "'wdth' 125"
  headline:
    fontFamily: "Akira Expanded Outline, Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "3rem"
    fontWeight: 900
    lineHeight: 0.95
    fontVariation: "'wdth' 125"
  dock-number:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 900
    lineHeight: 1
    fontFeature: "tnum"
    fontVariation: "'wdth' 125"
  closing:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "1.625rem"
    fontWeight: 900
    lineHeight: 1
    fontVariation: "'wdth' 125"
  wordmark:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "1.375rem"
    fontWeight: 900
    lineHeight: 1
    letterSpacing: "0"
    fontVariation: "'wdth' 125"
  range:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 900
    lineHeight: 1
    fontFeature: "tnum"
    fontVariation: "'wdth' 125"
  catalogue:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 900
    lineHeight: 1
    fontFeature: "tnum"
    fontVariation: "'wdth' 125"
  title:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 600
    lineHeight: 1.3
    fontVariation: "'wdth' 125"
  body:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.6
    fontFeature: "tnum"
    fontVariation: "'wdth' 125"
  nav:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 600
    lineHeight: 1
    fontVariation: "'wdth' 125"
  label:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
    lineHeight: 1
    letterSpacing: "0.01em"
    fontVariation: "'wdth' 125"
  meta:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 400
    lineHeight: 1.6
    fontFeature: "tnum"
    fontVariation: "'wdth' 125"
rounded:
  none: "0"
spacing:
  row-pad: "1.125rem"
  gutter: "2.5rem"
  gutter-narrow: "1rem"
  bar-h: "4rem"
  sleeve-w: "22.5rem"
  sleeve-w-docked: "clamp(22.5rem, 40vw, 44rem)"
  art: "4.5rem"
components:
  button:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "0.5rem 1rem"
    height: "2.5rem"
  button-hover:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.on-ink}"
  button-bar:
    backgroundColor: "{colors.on-ink}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "0.5rem 1rem"
    height: "2.5rem"
  button-bar-hover:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.on-ink}"
  button-playing:
    backgroundColor: "{colors.signal-orange}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    padding: "0.5rem 1rem"
    height: "2.5rem"
  button-on-ink-hover:
    backgroundColor: "{colors.on-ink}"
    textColor: "{colors.ink}"
  load-more:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    width: "100%"
    height: "3.5rem"
  group-tab:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.on-ink-muted}"
    typography: "{typography.nav}"
  group-tab-current:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.on-ink}"
  top-bar:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.on-ink}"
    padding: "0 2.5rem"
    height: "4rem"
  sleeve:
    backgroundColor: "{colors.signal-orange}"
    textColor: "{colors.ink}"
    padding: "{spacing.gutter}"
    width: "{spacing.sleeve-w}"
  sleeve-docked:
    width: "{spacing.sleeve-w-docked}"
  dock-card:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.on-ink}"
    rounded: "{rounded.none}"
    padding: "1rem"
  dock-art-blank:
    backgroundColor: "{colors.signal-orange}"
    textColor: "{colors.ink}"
    size: "{spacing.art}"
  release-row:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    padding: "1.125rem 0.75rem"
  release-row-playing:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.on-ink}"
---

# Design System: Starve

## Overview

**Creative North Star: "The Label Catalogue"**

Starve is set as a record label's release sheet. The timeline is the label's current releases: each item carries a catalogue number (STV plus its four-digit id), rows are ruled off like a printed sheet, and the series being browsed sits on an orange sleeve beside it. It is a document you work through to the end, not a feed of cards. What is playing is pulled out of the sheet and filed in the sleeve as a black sleeve card, so the release you are listening to stays in view while the sheet scrolls and swaps.

The world is three inks on paper. Black ink carries text, rules, the top bar and anything live; the off-white paper is the sheet; one signal orange marks the series sleeve and whatever is current or playing. One extended, heavy face, Akira Expanded, sets every word on the page; its outline cut sets the sheet heading. Corners are square everywhere. Structure comes from ink rules at two weights, not from boxes, shadows or tints.

Density is a working sheet: one row per item with generous vertical padding, catalogue number first, so the eye scans numbers and titles in a column. State is shown by inversion, not decoration: the playing row turns black with an orange number and its Play control reads "Playing". Motion is 150ms colour changes plus one 320ms widening of the sleeve when a player docks; all of it disappears under reduced motion.

**Key Characteristics:**
- Black, off-white and one signal orange; no other hues, no gradients, no tints.
- Akira Expanded for all text, Akira Expanded Outline for the sheet heading; Archivo at 125% width is the shipped fallback.
- Square corners on every element.
- 1px ink rules between rows, 2px ink rules for structure.
- Outlined rectangular controls that invert on hover; one filled control per region.
- Live state shown by inverting a whole row to ink and docking a black sleeve card in the orange column.

## Colors

A stark print palette: two inks on off-white paper, with orange as the only colour and a rationed one.

### Primary
- **Signal Orange**: the series sleeve's ground, the 3px underline under the current group tab, the catalogue number and filled "Playing" control of the playing row, the large STV number in the dock card, the numbered square standing in for missing cover art in the dock, the "Watch on YouTube" link in the refused-embed notice, focus outlines on ink grounds, and text selection. Ink text sits on it; it never carries paper-coloured text.

### Neutral
- **Ink**: body text, all rules, control strokes, the top bar, the playing row, the dock card, and the focus outline on paper.
- **Paper**: the page and sheet ground.
- **Muted Grey**: secondary text on paper (source names, dates) and the catalogue number and title of read items.
- **On-Ink**: text and the filled bar control on ink grounds; the hover fill of outlined controls sitting on ink. Same value as Paper, kept as its own role so ink regions can be reasoned about separately.
- **On-Ink Muted**: secondary text on ink (inactive group tabs, source and date in the playing row, source in the dock card).

### Named Rules
**The Signal Rule.** Orange means "the series you are in" or "live now". It appears on the sleeve and on current or playing state only; a static row, heading or control at rest on the sheet never turns orange.

**The Inversion Rule.** State is shown by swapping ink and paper, not by tinting. Hovered controls fill with ink (with on-ink on ink grounds); the playing row becomes an ink row with on-ink text. There are no grey hover washes or translucent overlays.

## Typography

**Display Font:** Akira Expanded Outline (sheet heading only), falling back to Akira Expanded, then Archivo
**Body Font:** Akira Expanded (Super Bold, a single weight) for everything else, with Archivo (variable, self-hosted WOFF2, OFL) at `font-stretch: 125%`, then system-ui, sans-serif
**Label/Mono Font:** none distinct; figures are tabular across the page (`font-variant-numeric: tabular-nums` on body)

**Character:** One wide, heavy grotesque at every size: the label speaks in a single voice, and hierarchy comes from size, colour and inversion rather than from a second face. The outline cut is reserved for the sheet heading so the biggest words on the sheet read as a printed mark, not a shout. The CSS still declares weights 900 / 600 / 400 per role; Akira renders all of them at its one Super Bold, and those weights exist so the Archivo fallback keeps a real hierarchy. Sizes are stepped down from the earlier condensed scale because the extended face sets roughly twice as wide.

### Hierarchy
- **Headline** (outline cut, 3rem desktop / 2rem narrow, line-height 0.95, uppercase): the sheet heading ("New releases" / "All releases"). One per page.
- **Display** (2.75rem / 1.75rem narrow, line-height 1.05, uppercase, balanced wrap, breaks anywhere): the series name on the sleeve.
- **Dock Number** (1.75rem / 1rem narrow, line-height 1, Signal Orange, tabular): the STV number in the dock card.
- **Closing** (1.625rem / 1.25rem narrow, line-height 1, uppercase): "You're all caught up".
- **Wordmark** (1.375rem, line-height 1, uppercase): STARVE in the top bar.
- **Range** (1rem, line-height 1, tabular): the STV range on the sleeve, under a 2px ink rule.
- **Catalogue** (0.9375rem / 0.875rem narrow, line-height 1, tabular, no wrap): the STV number in each row.
- **Title** (600, 0.875rem, line-height 1.3): item titles in rows and the dock; underlined on hover only.
- **Body** (400, 0.875rem, line-height 1.6): page default; the empty-state note runs at 0.9375rem, max 40rem. Series meta is 0.8125rem at line-height 1.4.
- **Nav** (600, 0.8125rem): group tabs.
- **Label** (600, 0.75rem, line-height 1, 0.01em): control text. Load more runs at 0.875rem.
- **Meta** (400, 0.75rem, Muted Grey or On-Ink Muted): source names and dates.

### Named Rules
**The One Face Rule.** Every word on every surface is set in Akira Expanded; the only other cut is Akira Expanded Outline, and it belongs to the page heading alone. Do not introduce a second family for reading text or controls.

**The Licensed Fallback Rule.** Akira Expanded and its outline cut are user-supplied personal-use fonts (Typologic). They are self-hosted from `/fonts/` but gitignored and never committed, because the repository is public. Archivo (committed, OFL) is the declared fallback, set at 125% width with weights 900 / 600 / 400 so a fresh clone or Docker image renders a wide Archivo with a working hierarchy. Every font stack ends in Archivo; nothing loads from a CDN.

**The Catalogue Number Rule.** Every item is identified by "STV " plus its id zero-padded to four digits, set in the Catalogue style as the first column of its row and in the Dock Number style in the dock. Ranges use an en dash ("STV 0106–0155").

## Layout

The desktop page is an ink top bar (min 4rem, sticky at the top) over a two-column grid: a 22.5rem sleeve column on the left and the release sheet filling the rest. The sleeve is sticky under the bar and exactly the viewport height minus the bar, scrolling internally if its contents overflow, so the series and the dock stay in view while the sheet scrolls. Horizontal gutter is 2.5rem; the sheet pads 2.25rem top and 4rem bottom.

The sleeve stacks the series block above the dock with a 1.5rem gap. While a player is docked the sleeve column widens to clamp(22.5rem, 40vw, 44rem), animated over 320ms; the embed iframe is never moved in the DOM, only its column grows.

A release row is a five-column grid: catalogue number (9.5rem), art (4.5rem square), title and source (flexible), date (9rem), actions (13rem, right-aligned), with 1.25rem column gaps and 1.125rem vertical padding. Rows bleed 0.75rem past the sheet edge so an inverted row reads as a full band. The sheet is a size container: when it is narrower than 56rem (typically because a docked player has widened the sleeve), each row drops to four columns and the date folds under the title.

Below 60rem the gutter drops to 1rem, the bar turns static and wraps (wordmark and actions on one line, group tabs scrolling horizontally on a second line of min 3rem), the sleeve becomes a static orange band with the series name left and meta right-aligned, and each row restacks: 4rem art on the left spanning catalogue number, title block and date, with actions on their own line beneath at 2.75rem min height. The dock becomes a fixed bar along the bottom of the viewport (now-playing line and Stop on one row, embed beneath, video capped at 40dvh tall, safe-area padding); the page reserves bottom padding equal to the measured dock height.

Spacing rhythm is built on rem steps rather than a strict scale: 0.5rem between controls, 1-1.5rem inside regions, 2.5rem for gutters and bar gaps.

## Elevation & Depth

The system is flat. There are no shadows anywhere. Depth and grouping come from ink: the top bar, the playing row and the dock card are solid ink grounds, the sleeve is a solid orange ground, and everything else is separated by rules. The only layering is positional: the sticky bar, the sticky sleeve, and on phones the fixed dock bar, each of which is an opaque ground sitting over the sheet.

### Named Rules
**The Flat Sheet Rule.** Nothing floats. If a region needs to stand apart, give it an ink or orange ground or a 2px rule, never a shadow or a raised card.

## Shapes

Every corner is square (radius 0): controls, art, the sleeve, the dock card, the embed. Form is drawn with ink rules at fixed weights. 1px rules separate rows; 2px rules mark structure (the top of the first row, the foot of the timeline, the rule above the sleeve's catalogue range, the empty-state note and the caught-up statement) and stroke the full-width Load more control. Controls are stroked at 1.5px in the current text colour. The current group tab is marked by a 3px orange underline.

Item art is a 4.5rem square (4rem narrow), cropped to cover; YouTube thumbnails and SoundCloud cover art both use it. A row without art keeps an empty, unframed cell of the same size so the title column stays aligned. In the dock, missing art becomes a solid orange square carrying the STV number in ink, set bottom-left.

### Named Rules
**The Rule Weight Rule.** 1px separates like from like; 2px closes or opens a structure. Do not introduce a third rule weight for separation.

## Components

### Buttons
Rectangular, outlined, typographic. Controls are words, never icons.
- **Shape:** square corners (0), 1.5px stroke in currentColor, min height 2.5rem (2.75rem in narrow row actions), padding 0.5rem 1rem.
- **Default:** transparent ground, ink text and stroke, Label type.
- **Hover:** inverts to an ink ground with on-ink text over 150ms. On ink grounds (playing row, dock) outlined controls fill on-ink with ink text instead.
- **Focus:** 2px ink outline offset 2px on paper; the outline turns orange on ink grounds (bar, playing row, dock).
- **Busy / disabled:** 55% opacity with a progress cursor while an htmx request is in flight.
- **Bar filled (Mark all read):** on-ink ground with ink text; the bar's one filled control. Hover inverts to ink with an on-ink stroke.
- **Playing:** in the playing row the Play control fills orange with ink text and its label changes to "Playing" (`aria-pressed="true"`).
- **Stop:** an outlined control on the dock card's ink ground, start-aligned under the embed (beside the now-playing line on phones).
- **Load more:** a full-width outlined block, 3.5rem tall, 2px stroke, 0.875rem text, 1.5rem margin above and below; inverts like any button.

### Navigation
- **Top bar:** ink ground, on-ink text, min 4rem tall, gutters at 2.5rem, sticky on desktop and static below 60rem. Wordmark left, group tabs next, the read toggle and Manage sources links and the filled control pushed right.
- **Group tabs:** On-Ink Muted Nav type with a 3px transparent bottom border; hover brightens to on-ink; the current tab (`aria-current`) is on-ink with an orange 3px underline. Tabs scroll horizontally without a scrollbar when they overflow.
- **Links:** plain underlined text (1px underline, 2px on hover, 0.2em offset).

### Series Sleeve (signature)
An orange column holding the series name in Display type, then the unread count (600), a sort note (400), and the catalogue range in Range type set off by a 2px ink rule above; the dock sits beneath. Sticky and full height on desktop; a compact horizontal band on narrow screens.

### Dock Card (signature)
The playing release filed as a black sleeve inside the orange column: ink ground, 1rem padding, 1rem gaps. Top line: 4.5rem cover art (or the orange numbered square), then the STV number in Dock Number type, the title, and the source in On-Ink Muted Meta. Below: the embed (16:9 for YouTube; the 166px SoundCloud widget; or a plain sentence and an orange "Watch on YouTube" link when embedding is refused), then Stop. The dock is empty and hidden until something plays, and it survives every swap of the sheet. On phones it is the fixed bottom bar described in Layout, with 3rem art and a 1rem number.

### Release Row (signature)
One item per row on paper, 1px ink rule above (2px for the first row), catalogue number first, art cell, title over source, date, then actions (Play when playable, Mark read when unread). Read items drop the number and title to Muted Grey. The playing row inverts: ink ground, on-ink text, orange catalogue number, On-Ink Muted source and date, and the filled orange "Playing" control. The player itself lives in the dock, never under the row.

### End of Sheet
Either the Load more block or the Closing statement ("You're all caught up") in caps under a 2px rule. The sheet always ends on one of the two.

### Inputs / Fields
Not yet built in this world. A future surface (the Sources page) should derive fields from the button language: square, 1.5px ink stroke, paper ground, Body type, ink focus outline at 2px offset 2px. Treat this as provisional until a built field confirms it.

### Named Rules
**The One Filled Control Rule.** Each region has at most one filled control: Mark all read in the bar, Playing in the playing row. Every other control is outlined and fills only on hover.

**The Docked Player Rule.** Playback lives in the sleeve, not in the sheet. Navigation, filters and bulk actions swap only the sheet, bar and series block, so the dock and its embed are never re-rendered while something plays.

## Do's and Don'ts

### Do:
- **Do** reuse the ink top bar (with its group tabs and filled bar control), the outline-cut sheet heading, the orange sleeve column, the ruled row grid with the catalogue number first, the outlined controls and the 2px end-of-sheet rule on every new surface; the Sources page should be a sheet of ruled source rows beside the same sleeve, so the whole app reads as one catalogue.
- **Do** keep orange to the sleeve and to current or live state (current tab underline, playing row, dock number, focus on ink, selection).
- **Do** show state by inverting ink and paper, with 150ms colour transitions (`cubic-bezier(0.16, 1, 0.3, 1)`); the sleeve's 320ms widening is the only longer motion, and none of it runs under reduced motion.
- **Do** separate rows with 1px ink rules and mark structure with 2px ink rules.
- **Do** set every word in Akira Expanded, the page heading in Akira Expanded Outline, and end every font stack in the self-hosted Archivo at 125% width.
- **Do** keep the Akira files out of git; the public repository ships only Archivo and its OFL licence.
- **Do** keep figures tabular and catalogue numbers zero-padded to four digits.

### Don't:
- **Don't** round corners, add shadows, or lift anything onto a card.
- **Don't** introduce a second accent hue, gradients, or tinted surfaces.
- **Don't** put more than one filled control in a region.
- **Don't** add `text-transform` to content (titles, sources, notes, control labels); only the label's own marks (wordmark, series name, sheet heading, closing statement) are uppercased in CSS, so the Archivo fallback still reads content in its own case.
- **Don't** replace control words with icons.
- **Don't** add engagement chrome (avatars, counts of likes or views, recommendation rails); the sheet lists only what the owner subscribed to.
