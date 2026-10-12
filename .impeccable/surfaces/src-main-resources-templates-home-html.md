---
version: 1
slug: "src-main-resources-templates-home-html"
primary_target: "src/main/resources/templates/home.html"
related_targets: ["src/main/resources/templates/player.html"]
---

---
version: 1
slug: "src-main-resources-templates-home-html"
primary_target: "src/main/resources/templates/home.html"
related_targets: ["src/main/resources/templates/player.html"]
---

# Timeline surface brief

Scope: the timeline `/` (home.html, player.html fragment). Mode: Operate. Code-led.
Audience: the single owner, desktop and phone equally, usually listening while doing other things; under ~20 new items per visit.
Task: play or open, mark read, reach "You're all caught up". Deal-breakers: platform/social look, more clicks than today, busy motion, music stopping on navigation (the last is deferred: docked persistent player is a separate spec'd issue; superseded: the docked player shipped in the same branch, see FIRST VIEWPORT).
Constraints: Thymeleaf + vendored htmx, no runtime CDN (self-hosted font), no-JS still works, acceptance-test markup contracts kept (article data-title, class "item read", class "play", class "group-link" plain-text labels, class "load-more", hidden before/group inputs, time text yyyy-MM-dd, .ratio-16x9 > iframe).
Unresolved: dark theme; docked sleeve player (follow-up issue); sheet heading wording.

## Direction contract

THESIS: The timeline is a record label's release sheet: every item carries a catalogue number and the page reads as this label's current releases, not a social feed. It refuses the category default of thumbnail cards with avatars and engagement chrome.

OWN-WORLD: Off-white sheet, black ink, one signal orange used only for live state and the series sleeve; muted grey meta. Akira Expanded (user-supplied, personal-use demo, self-hosted and gitignored; fallback Archivo at 125% width, weight 900) for display text and catalogue numbers; Archivo normal width for titles, meta and controls; tabular figures. Square corners, 1px black rules between rows, 2px black rules for structure, black-outlined rectangular buttons that fill black on hover.

STORY: The visitor sees which series they are in and how many releases are unread, scans titles by catalogue number, plays or opens one, marks the rest read, and reaches the end of the sheet.

FIRST VIEWPORT: Black top bar (sticky on desktop, static on phones): STARVE wordmark left, group tabs as the label's sub-series (current underlined orange), Show read and Manage sources right, Mark all read as the bar's one filled button. Below, on desktop, a sticky orange sleeve column (~360px) left: the current series name in display caps with "n unread" and the catalogue range beneath, then the dock. Right: the release sheet with a display-caps heading, then rows: catalogue number STV 0132, art (or an empty cell), title + source, date, then Play and Mark read. Play docks the player in the sleeve as a black sleeve card (cover art or an orange numbered square, the STV number at display scale in orange, title, source, the embed, Stop); while anything plays the sleeve widens to clamp(22.5rem, 40vw, 44rem) and the sheet folds dates under titles. Group tabs, the read toggle and Mark all read swap only #sheet, #bar and #series, so playback continues. Phone: the sleeve is a compact band; the dock is a fixed bottom bar with the video capped at 40dvh.

FORM: The Label Catalogue, the model's top-ranked grounded candidate (IMPECCABLE'S PICK), chosen by the user over the rolled directions; seed key ab23b902.

Signature move: the catalogue number. Every item is STV plus its real id, set in display caps; the playing row inverts to black with an orange number and its Play button reads "Playing", and the dock carries the same number large. Motion: 150ms colour/inversion transitions and one 320ms expand of the sleeve column when a player docks; none under reduced motion.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
