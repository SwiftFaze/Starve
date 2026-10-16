---
name: Starve
description: A personal feed set as a 90s OVA cassette cover. A red obi strip, a spec-panel release list, a tiled workspace and a tape-transport player deck; dark on ink, light on bone paper.
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
  code-amber: "#e8b04a"
  code-violet: "#b3a6ff"
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
  code-amber-paper: "#8a5a00"
  code-violet-paper: "#5b49c9"
  obi-ink-light: "#ffffff"
  stripe-light: "#ebe8e0"
typography:
  wordmark:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "0.01em"
  cover-title:
    fontFamily: "Akira Expanded, Archivo, system-ui, sans-serif"
    fontSize: "clamp(2.75rem, 5.1vw, 6rem)"
    fontWeight: 800
    lineHeight: 0.92
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
  docs-body:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.65
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
  code:
    fontFamily: "Martian Mono, ui-monospace, Menlo, Consolas, monospace"
    fontSize: "0.8125rem"
    fontWeight: 400
    lineHeight: 1.6
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
  button-beside-field:
    height: "2.5rem"
  button-primary:
    backgroundColor: "{colors.bone}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    width: "100%"
    height: "3rem"
  button-primary-hover:
    backgroundColor: "transparent"
    textColor: "{colors.bone}"
  button-playing:
    backgroundColor: "{colors.obi-red}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
  icon-toggle:
    backgroundColor: "transparent"
    textColor: "{colors.bone}"
    rounded: "{rounded.none}"
    width: "2.25rem"
    height: "2.25rem"
  icon-toggle-pressed:
    backgroundColor: "{colors.bone}"
    textColor: "{colors.ink}"
  layout-switch-active:
    backgroundColor: "transparent"
    textColor: "{colors.scan-cyan}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
  setting-switch-on:
    backgroundColor: "{colors.bone}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    height: "2.5rem"
  field:
    backgroundColor: "{colors.ink-sunk}"
    textColor: "{colors.bone}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: "0.5rem 0.75rem"
    height: "2.5rem"
  search-field:
    backgroundColor: "{colors.ink-sunk}"
    textColor: "{colors.bone}"
    rounded: "{rounded.none}"
    padding: "0.375rem 0.625rem 0.375rem 2rem"
    width: "9.5rem"
    height: "2.25rem"
  search-field-focus:
    width: "14rem"
  inline-code:
    backgroundColor: "{colors.bone}"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "0.15em 0.35em"
  code-block:
    backgroundColor: "{colors.ink-sunk}"
    textColor: "{colors.bone}"
    typography: "{typography.code}"
    rounded: "{rounded.none}"
    padding: "0.875rem 1rem"
  obi:
    backgroundColor: "{colors.obi-red}"
    textColor: "{colors.ink}"
    typography: "{typography.display}"
    width: "5.5rem"
  obi-landing:
    backgroundColor: "{colors.obi-red}"
    textColor: "{colors.ink}"
    width: "clamp(6rem, 9vw, 9.5rem)"
  pane-bar:
    backgroundColor: "{colors.ink-raised}"
    textColor: "{colors.bone-dim}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    height: "1.75rem"
  pane-bar-active:
    textColor: "{colors.bone}"
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

Starve is drawn as a 90s OVA cassette cover. A red obi strip runs down the left edge and names the series (the current group) in vertical Akira caps, closed at the foot by caution chevrons. The release list beside it is the cover's spec panel: hairline-ruled rows keyed by STV catalogue numbers, with registration crosses at the sheet head's corners. Along the bottom, while something plays, a tape-transport deck shows the art, the STV number in red, the title, and a waveform that fills red as it plays and is also the seek bar. On a desktop the list, a playing video and an open post are tiled beside the obi like terminal panels, each with a mono title bar, split by draggable rules, and dragging one title bar onto another splits or swaps them.

The same drawing exists twice. Dark is the cover on ink and light is the cover on bone paper, chosen by `prefers-color-scheme` unless the obi's switch has chosen. Both keep square corners, outlined mono controls, and a fine film grain over everything. Display type is loud and expanded, but reading text stays in mixed-case Archivo at reading sizes. The poster attitude belongs to the frame. The list itself is for reading. Signed out, the same world plays at full volume: the landing is the cassette's J-card, with a wider obi and a cover title at poster size.

The world rejects the reader/music-app default of a grey sidebar, rounded cards and pill buttons. It also rejects all-caps reading text.

**Key Characteristics:**
- A full-height red obi band (5.5rem; wider on the landing) at the left on desktop, a horizontal red band under the top bar on phones.
- A spec-panel list: STV number, art cell, title over source, date, actions, all on hairline rules.
- A tiled desktop workspace: list, video and post as panels with title bars, hairline frames and splitters; the active panel outlined in red.
- A fixed player deck with a red top rule and a waveform seek bar.
- Three type voices with strict jobs: Akira for display, Archivo for reading, Martian Mono for numbers, controls and code.
- Square corners everywhere, with no shadows. Depth comes from tone, rules and grain.
- Red means obi, live, or the active panel. Cyan means focus, the chosen layout, a drop target or an active splitter.

## Colors

Ink, bone and two signals: an obi red that says "this is the series / this is playing / this is the panel you're in" and a scanline cyan that says "this is where focus is or where the video goes". Every color has a dark value and a light value; the light palette is the same drawing on paper.

### Primary
- **Obi Red** (dark `obi-red`, light `paper-red`): the obi band, the deck's top rule and waveform fill, the playing row's number cell and Play button, the phone video's top rule, the active panel's frame and title-bar marker, text selection, the field caret, and the "no art" block in the deck. Text on it is always ink (`ink`), in both themes.
- **Obi Red Text** (dark `obi-red-text`, light `paper-red-text`): red used *as text*. It covers the deck's STV number, the "Watch on YouTube" fallback link, failure lines, and the brackets and border of an error message. It is lighter than Obi Red on ink and darker on paper so it reads at body sizes.

### Secondary
- **Scan Cyan** (dark `scan-cyan`, light `deep-cyan`): the focus outline (2px, offset 2px), the focused field border, the pressed button of the deck's layout switch, the splitter handle while it is hovered, focused or dragged, a lifted title bar, and the panel a dragged title bar would land on (a 2px cyan frame over an 8% cyan wash).

### Tertiary (code only)
- **Code Amber** (dark `code-amber`, light `code-amber-paper`) and **Code Violet** (dark `code-violet`, light `code-violet-paper`): the docs highlighter's two hues. Amber marks flags and operators, violet marks strings and values. They appear inside code blocks and nowhere else.

### Neutral
- **Ink** (`ink`): the dark ground. It is also the text color in light mode and the text on red in both themes.
- **Ink Raised / Paper Raised** (`ink-raised`, `paper-raised`): row hover, the playing row, the player deck, panel title bars, the landing's demo panel and the hovered off-cell of a switch.
- **Ink Sunk / Paper Sunk** (`ink-sunk`, `paper-sunk`): field and search wells, checkboxes, code blocks, and empty thumbnail backing.
- **Hairline / Strong Hairline** (`ink-line`, `ink-line-strong`; light `paper-line`, `paper-line-strong`): row rules, region dividers, panel frames, unplayed waveform bars, the volume track, unpressed layout-switch borders, idle splitter handles and the scrollbar.
- **Bone** (`bone`): dark-mode text, the button outline color (the outline always matches the text), the fill of the filled button, a pressed toggle, the current switch cell and inline code.
- **Bone Dim / Ink Dim** (`bone-dim`, `ink-dim`): secondary text such as sources, dates, field labels, hints, times, inactive tabs and idle panel names.
- **Bone Faint / Ink Faint** (`bone-faint`, `ink-faint`): the most muted tier, for read rows, placeholders, registration crosses, code comments and code-language labels; still AA on every ground, but meant to recede (see Do's and Don'ts).

### Named Rules
**The Obi Red Rule.** Red is for the obi and for live/playing state only, meaning what plays now and the video that is playing. Never use it as a general accent, link color or emphasis. One sanctioned exception, asked for by the user (issue #38): the active workspace panel, the one last clicked or focused, is outlined in red and its title-bar marker turns red. It does not extend to any other "selected" or "current" state.

**The Scanline Rule.** Cyan means focus, the chosen layout, where a dragged panel will land, or an active splitter, nothing else. If it isn't telling you where focus is or where something goes, it isn't cyan.

**The Code Ink Rule.** Amber and violet exist only inside code blocks. They never colour a control, a heading or a state.

**The Same Drawing Rule.** Every surface ships in both themes from the same tokens. A new color needs a dark value and a light value, or it doesn't ship.

## Typography

**Display Font:** Akira Expanded (800, `font-stretch: 125%` so the Archivo fallback widens too), with Archivo, system-ui fallback. The font is user-supplied and personal-use only. It is gitignored and never committed.
**Body Font:** Archivo (variable 100–900, width 62–125%), self-hosted OFL WOFF2, latin + latin-ext.
**Label/Mono Font:** Martian Mono (variable 100–800), self-hosted OFL WOFF2.

**Character:** A loud expanded poster face used only for names and headings, a plain grotesque that carries every word you actually read, and a technical mono that turns catalogue numbers, dates, controls and code into spec-sheet data. Tabular figures are on globally.

### Hierarchy
- **Cover Title** (Akira 800, `clamp(2.75rem, 5.1vw, 6rem)`, 0.92; `clamp(2rem, 9.6vw, 3.75rem)` on phones; uppercase, balanced): the landing's cover line only.
- **Display** (Akira 800, 2.75rem; 2.125rem for Sources/Import/Docs obi names; `clamp(3rem, 6.2vw, 6rem)` on the landing's obi; `clamp(1.25rem, 7vw, 1.75rem)` on the horizontal phone obi; uppercase, vertical-rl rotated 180deg on desktop, ellipsis at 70% of the band, 78% on the landing): the series name on the obi.
- **Headline** (Akira 800, 1.75rem, 1; 1.25rem below 60rem; uppercase, balanced): the sheet heading. Docs section heads use the same voice at 1.25rem/1.1 over a full-weight rule.
- **Closing** (Akira 800, 1.5rem, 1.05; uppercase, under a full-weight rule): "You're all caught up".
- **Wordmark** (Akira 800, 1.125rem, uppercase, 0.01em): STARVE in the top bar. The landing demo's head uses the same size.
- **Deck Number** (Akira 800, 1.125rem; 0.875rem below 60rem; Obi Red Text): the playing STV number in the deck.
- **Title** (Archivo 500, 1rem, 1.35, mixed case): release, source and setting names; underline only on hover; wraps anywhere rather than overflow. The reader's post title is 1.375rem/1.3 (1.25rem on phones).
- **Deck Title** (Archivo 600, 0.9375rem, 1.25): the playing title, one line, ticker when too long.
- **Body** (Archivo 400, 0.9375rem, 1.55): page default; notes at 1rem, hints at 0.8125rem/1.45. Sheet notes cap at 40rem. Posts and docs read at 1rem (posts 1.6, docs 1.65), capped at 40rem and 44rem.
- **Catalogue** (Martian Mono 500, 0.75rem, 0.04em): STV/SRC numbers at the head of each row.
- **Meta** (Martian Mono 400, 0.75rem, 1.3): source names, dates, URLs, fetch times, elapsed/total.
- **Nav** (Martian Mono 500, 0.75rem, 0.06em, uppercase): Timeline/Sources tabs, group links, Docs and Sign in.
- **Label** (Martian Mono 500, 0.6875rem, 0.06em, uppercase): every button and field label (field labels, panel names, spec and note terms 0.08em). Obi meta, the bar caption and the colophon run at 0.14em.
- **Code** (Martian Mono 400, 0.8125rem, 1.6): code blocks. Inline code is mono 500 at 0.8125em.

### Named Rules
**The Three Voices Rule.** Akira is for display only: the wordmark, the obi series name, the landing's cover title, sheet and docs headings, the deck STV number and "caught up". Archivo, in mixed case, carries every title and all body text. Martian Mono carries STV/SRC numbers, dates, sources, control labels and code. Never set reading text in Akira, and never set reading text in all caps.

## Layout

**Desktop grid.** A sticky top bar (3.5rem) holds the wordmark, the page tabs, the scrolling group tabs (separated by a hairline), the search field and the account actions. Below it is a two-column label grid. The first column is the sleeve: the obi band (5.5rem) pinned full height between bar and deck. On Sources and Import a 22rem tool panel sits beside the obi. The second column is the sheet, padded 1.75rem top, 2rem gutter, 4rem bottom. The deck is fixed to the viewport bottom, and the page reserves its height through `--deck-h`, which the script measures.

**Release rows.** The wide row is five columns: number 7rem, art 4rem, title flexible, date 12.5rem, actions 18.5rem fixed (wide enough for Playing, star, clock and Mark read). The fixed date and action columns keep every date aligned no matter which buttons a row has. A row with no art keeps an empty art cell so every row stays aligned. Rows are 0.875rem by 0.75rem, bleed into the gutter with a negative margin, and sit on a hairline.

**The sheet is a container** (`container: sheet / inline-size`), so rows respond to the sheet's width rather than the viewport. That matters when a panel beside it narrows the list.
- Below 68rem, source rows move status under the title.
- Below 64rem, rows fold. Art (3.5rem) sits on the left, number and date share the top line, the title goes under the number, and the buttons go under the date on the right.
- Below 44rem, the sheet head wraps and its actions take a full line.
- Below 34rem, the buttons drop under the title, left-aligned.

**The workspace (desktop).** When two or more of list, video and post are open, they tile the area right of the obi, between bar and deck. Each panel is fixed to a rectangle the script computes, scrolls on its own, and wears a 1.75rem title bar and a hairline frame. Only the video's box moves; the iframe is never re-parented, so playback never restarts. The arrangement is a tree of splits, made by dragging title bars and remembered (`starve.tree`); it defaults to the list beside a column of video over post:
- **Dragging a title bar** onto a panel splits it on the side it is dropped (left, right, top, bottom) or swaps the two in its middle (30-70% of it); onto the outer 28px of the workspace, the panel takes that whole edge. A cyan-bordered, cyan-tinted preview shows the landing area. Every tile slides to its new place.
- **Rules:** one per split, a 0.75rem drag zone with a 0.25rem × 3rem handle (3rem × 0.25rem across) that grows to 5rem in cyan when active. Panels never go below 200px. Arrow keys step 32px; a double-click, Home or End evens the split out. A list narrower than 610px, in any column, folds to an STV strip (8.5rem) of catalogue numbers whose rows click through to the item and strike through when read; Escape unfolds it.
- **A video alone in a tall panel** ends under its 16:9 picture.

**The landing (signed out).** The obi widens to `clamp(6rem, 9vw, 9.5rem)`. The first viewport is two columns (1.15fr and 1fr): the cover title, pitch and actions on the left, the demo release list on the right. Below 72rem they stack. Further down: an A/B-side tracklist in two columns, a specifications list (10rem terms), and a barcode colophon.

**Access pages (sign in, sign up, set up).** A form column (up to 26rem) beside a notes column (up to 22rem); one column below 60rem.

**Settings and Docs.** Settings is a ruled sheet of groups capped at 52rem. Docs is a sticky 13rem table of contents beside one 44rem reading column; on phones the contents sit above the column, unstuck.

**Below 60rem (phones).** The gutter drops to 1rem. The bar wraps into three lines: wordmark and account actions; pages and a full-width search; then the scrolling group tabs on their own hairline. The obi turns horizontal as a red band under the bar, with the series on the left, meta on the right and no chevrons (the landing hides the meta). The deck goes to two lines: the transport and waveform across the top, where a thumb reaches it, then art, text and Eject; volume is hidden. Phones get no layout choice: the video always sits full width directly above the deck (16:9, max 40dvh, with a red top rule), and splitters, title bars and the grip are hidden. An open post covers the whole screen above the deck until it is closed; its head is sticky, tucks away while scrolling down and slides back on the way up. Touch targets for row actions, toggles, fields and the deck rise to 2.75rem.

## Elevation & Depth

The system is flat, with no shadows anywhere. Depth comes from three things. The first is tone: ground, a raised surface for hover, the playing row, the deck and panel title bars, and a sunk surface for fields and code. The second is rules: hairlines between rows and around panels, a full-weight text-colored rule under sheet heads and above "caught up", and red rules on the deck's top edge, the active panel and the floating video. The third is z-order of fixed layers: bar 20, panel frames 24, video 25, title bars and the active frame 26, drop target 27, splitters 28, deck 30, the phone reader 35, back-to-top 36, a lifted title bar 40, grain 50, shutter 100. A fractal-noise grain covers everything and never catches the pointer: specks of the text colour cut out of a full-screen layer by a fractal-noise mask, at 3.5% opacity in dark and 4% in light. Being text-coloured, the specks read on ink and on paper alike and barely shift the ground (about 2.5 levels of luminance noise on empty ground).

### Named Rules
**The Hairline Rule.** Regions are separated by 1px rules, never by shadows or cards. The strongest separation is a 1px rule in the text color.

## Shapes

Every corner is square (`border-radius: 0`), including buttons, fields, checkboxes, the volume thumb, thumbnails and the video frame. The recurring cover geometry has six parts. Caution chevrons are 45deg stripes of ink on red that close the obi. Registration crosses are 0.75rem hairline crosses outside the sheet head's top corners (and the landing demo's). Small solid squares are the 0.5rem current-tab marker, the 0.375rem panel-name marker and the square volume thumb. Number cells are solid blocks behind the playing STV number. Barcodes are bands of 1–3px bars in the text color. Square brackets frame counts and messages: "Music [12]" in the group tabs, "[ Subscribed ]" in a message. Adjacent segmented buttons overlap by 1px so they share one rule.

## Components

### Buttons
Outlined mono controls that read as printed spec labels.
- **Shape:** square (0), 1px border in the text color, min-height 2.25rem (2.75rem in row actions on phones), padding 0.5rem 0.875rem.
- **Outline (default):** transparent with text-color label. Hover inverts to a text-color fill with ground-color label (150ms).
- **Filled:** the one filled control on a surface, inverting on hover. Examples are Mark all read on the sheet head, Add/Import in the tool panel, and Sign in on the landing.
- **Primary (forms):** the access form's one action: full width, filled, 3rem tall, label at 0.75rem.
- **Beside a field:** a button beside a field, or in any row of controls that contains one, is 2.5rem tall so the row lines up top and bottom.
- **Playing:** the playing row's Play button fills with Obi Red, label in ink.
- **Icon toggles:** the favourite star and the save-for-later clock are 2.25rem square buttons carrying a drawn SVG (the star outlined, the clock solid). Pressed, they invert to a text-color fill with the icon filled in ground color, never red. 2.75rem on the phone reader. The deck's star is the same toggle, sized to the play/pause beside it.
- **Disabled / busy:** 45% opacity when disabled, with no hover. During an htmx request the opacity is 55% with a progress cursor.
- **Load more:** a full-width outline bar 3.25rem tall.

### Chips
- **Failing tag:** a solid red block with an ink mono label, uppercase, padded 0.25rem by 0.375rem, ahead of a failing source's error. This predates the Obi Red Rule and falls outside it; don't extend it to new surfaces.

### Inputs / Fields
- **Style:** a sunk well with a 1px strong hairline, square, Archivo body text, a red caret, and faint placeholders. Min-height is 2.5rem (2.75rem and 1rem text on phones). Selects draw their own two-triangle chevron in the text color. File inputs use a mono body with an outlined mono selector button.
- **Search:** the same sunk well in the top bar with a drawn 0.875rem magnifier inside its left edge and a mono uppercase placeholder. It is 9.5rem wide idle and widens to 14rem when focused or filled (240ms); full width on phones.
- **Checkbox:** a 1.125rem square sunk well; checked, it fills with the text color and shows a drawn tick in the ground color. Its label row is 2.75rem tall.
- **Focus:** the border turns cyan, along with the global 2px cyan outline.
- **Labels:** mono uppercase field labels in the dim text color, and hints in body 0.8125rem.

### Message
A status or error line, used on every page that reports one: a 1px box padded 0.875rem by 1rem, medium weight, the text wrapped in mono brackets. A status draws its box and brackets in cyan; an error on an access form draws them in Obi Red Text.

### Navigation
- **Top bar:** sticky on desktop and static on phones. Tabs and group links are mono uppercase in the dim color and turn to the text color on hover. Group links carry their unread count in square brackets ("All [40]"). The current page or group is marked by a 0.5rem square before the label. The build draws it in red, which falls outside the Obi Red Rule; don't copy that use of red.
- **Account actions:** Settings (admins only, marked current on Settings) and an outlined Sign out button end the bar.
- **Public bar:** every signed-out page (landing, docs, sign in, sign up, set up) shares one bar: the wordmark, a mono caption ("No algorithm · Self-hosted", hidden on phones), then Docs and Sign in on the right, the current one marked. Set up leaves out Sign in.
- **Docs contents:** a list of mono uppercase links on hairlines, under a strong hairline, sticky beside the column.

### The Obi (signature)
The red spine. It holds the series name, set vertically in Akira and reading bottom to top. Mono meta below it gives "n unread", the sort order and the STV range, semibold. Its text is the obi ink: dark ink on red in the dark theme, white in the light theme (`--on-obi`). On Sources it carries a failing count as a block inverted in that ink. It is full height between bar and deck and never scrolls with the list. On the landing it is the cover's own spine: wider and carrying STARVE at poster size.
- **Theme switch:** the caution chevrons at its foot are a button that flips dark and light. The choice is stored in localStorage (`starve.theme`) and applied from `<head>` before the first paint; with nothing stored, the OS decides. The stripes sit on a 1rem square tile (`--stripe`: ink in dark, bone in light) and march sideways on hover. On phones the chevrons end the horizontal band.

### The Mark (signature)
The logo: the obi tile carrying two overlapping squares in obi ink, one solid and one outlined, with the overlap showing the tile red. The favicon is its own icon: the feed in frame, three shortening rows inside corner brackets with a small red live square, on the ground colour, pixel-fitted at 16px. Both follow the theme like the obi does. Its construction, colours and rules live in `docs/brand.md`.

### The Shutter (signature)
Every theme change and every page switch (an in-app navigation link, not a form submission) runs behind one red panel that sweeps across the whole viewport: 1.25s in, a 0.4s hold while covered, 1.25s out, all `cubic-bezier(0.65, 0, 0.35, 1)`. Chevron bands lead and trail it. It is dressed as an obi spec sheet: an inset hairline frame with registration crosses, mono corner captions (STARVE, NO ALGORITHM, `[ THEME ]` or `[ PAGE ]`), a barcode with a code derived from the label, a chevron strip, the destination in Akira over a thin rule and a mono caption, and a faint scanline. On a theme change it wears the destination theme's obi ink from the start, so it never flips colour mid-cover.
- **Page switches never reload:** a link between pages (the top bar's links, the wordmark, the public cross-links, Cancel and Back to Settings) is marked `data-switch`. The next page is fetched while the panel sweeps in, swapped in place under it, the URL pushed, and the same panel sweeps out, so nothing blinks. The player dock sits outside the shell, and the Timeline, Sources, Account and Settings pages all carry it, so moving between them by the bar's links keeps whatever plays. The Timeline and Sources tabs swap only the sheet; the other switches swap the shell (`#shell`: sleeve, sheet, body class), dock kept. A move to a page without a dock (Import, delete user, the temporary-password page, the public pages) removes it and stops playback, as a page load does. Modified clicks, back/forward and a failed fetch fall back to an ordinary page load.
- **Form submissions are plain:** sign in, sign up, setup, sign out, change password and confirm delete are ordinary form posts with no shutter, because they change who the visitor is.
- **The page you are on:** clicking its tab glides back to the top (of the list panel when tiled), or, already at the top, blinks the tab's red marker twice. No reload.
- Under reduced motion there is no panel: the theme or page changes at once.

### Sheet Head
Every sheet heading sits in one head: Akira headline, actions to the right, a full-weight text-color rule beneath, and registration crosses at its top corners. A second head lower on the Sources sheet (the groups section) keeps the rule but drops the crosses, as do heads inside a tiled list panel and on phones.

### Release Row
Its parts are the STV number, the art cell (empty if no art), the title over source, the date and the actions (Play, the icon toggles, Mark read). On hover the row moves to the raised surface. A read row turns its number and title to the faint tier, with the title struck through in a strong hairline. The playing row stays raised, its STV number becomes a red cell, and its Play button fills red.
- **Marking read:** before the server answers, the cleared rows strike through (2px, drawn in over 320ms) and dim to 45%, staggered 22ms per row; the read rows then settle in from 45% over 260ms rather than blinking. Under reduced motion the strike is simply there.

### Workspace Panel (signature)
The tmux-like tile. A 1.75rem title bar on the raised surface carries a 0.375rem square marker and the panel name (List, Video, Post) in mono uppercase, dim, then a small borderless × that closes the post or ejects the video (the list has none). The bar is the drag handle (grab cursor); lifted, it follows the pointer with a cyan border at 92% opacity, and the panel under it shows the cyan drop frame. A hairline frame outlines each panel. The active panel, the last one clicked or focused, gets a red frame and red bar border, its name in the text color and its marker red.

### Player Deck (signature)
A fixed bottom bar with a minimum height of 5.5rem, on the raised surface with a 1px red top rule. From left to right it holds the art (5.5rem square, or a red block with the STV number in mono when there is no art), the text stack (red Akira STV number, Archivo title and mono source), the favourite star, the transport, the layout switch for YouTube only, and Eject.
- **Star:** the item's favourite star (an icon toggle) sits in its own grid cell right after the text stack, centred on the deck, at the same size as the play/pause (2.5rem; 2.75rem on phones, where it takes the cell between the text and Eject). It follows the item's other stars.
- **Transport:** a 2.5rem square play/pause, the elapsed time, the waveform and the total time.
- **Waveform:** 3rem tall, with bars in the strong hairline color. A second red copy is revealed by a clip-path as playback progresses. A transparent range input over it seeks on release. Its focus shows as a cyan outline offset 4px.
- **Volume:** a 2px level line with a square 0.75rem thumb, 6rem wide. The level is remembered in localStorage. Hidden on phones.
- **Eject:** an outlined button with a drawn eject glyph (triangle over a bar); it stops playback and closes the deck.
- **Ticker:** a title too long for the deck holds at the start, slides to show the end, holds and returns. The cycle is at least 6s, ease-in-out, and pauses on hover. Under reduced motion it falls back to an ellipsis with no animation.
- **SoundCloud fallback:** if the widget's handshake never arrives, the transport hides and SoundCloud's own player shows above the deck in a red frame. The same happens when a play gets no answer from the widget within 5 seconds (iOS Safari may refuse it).

### Video Box
The box takes whatever rectangle its panel gives it, on the ground color; the picture inside keeps 16:9 at the largest size that fits (`min(100cqw, 100cqh * 16 / 9)` via a size container), anchored to the top and centred across, so a panel of any shape never shows black bars. The YouTube embed runs with `controls=0`. A transparent shield over the video blocks YouTube's hover overlay. A click on the shield plays or pauses, and a double-click goes fullscreen on black. While resizing or dragging, the iframe ignores the pointer and nothing animates. YouTube's title bar reappearing after a seek is an accepted platform limitation, not something to design around.

### Landing J-Card
The signed-out cover. A cover title in Akira, an Archivo pitch (up to 34rem, dim) and the actions (filled Sign in, outline Sign up when signups are open, both 3rem tall). Beside it, a demo release list on the raised surface inside a strong hairline with registration crosses: an Akira head with a mono "Demo" tag, six rows built from the app's own row parts with abstract hairline art blocks, which strike themselves read one by one (520ms each, 0.55s apart) and end on "You're all caught up". Below: an A/B-side tracklist (mono track numbers A1–B3, Archivo 600 names over dim descriptions), a specifications list (mono terms, Archivo values, hairline rows), and a colophon of mono captions with a barcode. Under reduced motion the demo shows its finished state.

### Access Form
Sign in, sign up and set up share one shape: a sheet head, mono field labels over fields, a checkbox row where needed, the full-width primary button, and an Archivo line linking to the other form. Beside it, a notes list: mono uppercase terms over dim Archivo text on hairlines, under a strong hairline.

### Settings Sheet
Groups under small mono uppercase headings (0.75rem, 0.12em, dim) on a strong hairline. Each setting is a row on a hairline: name in Archivo over a hint, the control on the right. The switch is one 2.5rem button split into Off and On cells; the current cell is filled with the text color, and on hover the other cell shows the raised surface.

### Code Block
On the docs page a code block is a sunk well with a strong hairline, mono 0.8125rem/1.6, wrapping long lines rather than scrolling. Its top-right corner carries a faint mono language label (hidden on phones) and a small outlined Copy button on the ground color that reports "Copied". The highlighter sets commands bold in the text color, flags and operators in Code Amber, strings and values in Code Violet, URLs underlined in the strong hairline, and comments in the faint tier. Inline code is reversed out: a text-color fill with ground-color mono text, never breaking across lines.

## Do's and Don'ts

### Do:
- **Do** set the wordmark, obi series name, cover title, sheet and docs headings, the deck STV number and "caught up" in Akira Expanded 800, uppercase, at `font-stretch: 125%`.
- **Do** set every title and all body text in Archivo, mixed case. Set STV/SRC numbers, dates, sources, control labels and code in Martian Mono.
- **Do** ship every color in both themes, following `prefers-color-scheme` unless the obi switch has set `data-theme` on the root.
- **Do** keep row dates aligned with the fixed 18.5rem actions column, and keep an empty art cell when a row has no art.
- **Do** let rows respond to the sheet's container width (64rem fold, 34rem buttons-under-title), not the viewport.
- **Do** make a button beside a field, or in a row that contains one, 2.5rem tall.
- **Do** draw icons as inline SVG in `currentColor`; show a pressed toggle by inverting it, not by colouring it.
- **Do** keep state changes at 150ms (`cubic-bezier(0.16, 1, 0.3, 1)`), panel swaps, the search widening and the reader head's tuck at 240ms, and the list fold at 280ms. Under `prefers-reduced-motion`, use no transitions, no fold animation, no strike or demo animation and no ticker.
- **Do** move only the video's box between panels and layouts, never the iframe, so playback never restarts.
- **Do** give phones a single video placement: full width above the deck.
- **Do** run theme changes and page switches through the shutter, at one speed, and keep the scrollbar gutter reserved (`scrollbar-gutter: stable`) so switching pages never changes the page width.

### Don't:
- **Don't** use Akira for reading text, and don't set reading text in all caps.
- **Don't** use red for anything but the obi, live/playing state and the active workspace panel.
- **Don't** use cyan for anything but focus, the chosen layout, a drop target or the active splitter.
- **Don't** use the code hues (amber, violet) outside code blocks.
- **Don't** round corners, use pill buttons or cards, or add shadows.
- **Don't** use unicode glyphs as icons.
- **Don't** use the faint text tier (`bone-faint` / `ink-faint`) as the main voice: it clears 4.5:1 on every ground (≥4.68:1) but exists to recede, for read rows and placeholders.
- **Don't** commit the Akira font files. They are personal-use and gitignored.
- **Don't** colour the page scrollbar: it stays the strong hairline on the ground (tried in red and rejected).
