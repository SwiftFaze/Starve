---
version: 1
slug: "src-main-resources-templates-home-html"
primary_target: "src/main/resources/templates/home.html"
related_targets: ["src/main/resources/templates/player.html"]
---

# Timeline surface brief

Scope: the timeline `/` (home.html, player.html fragment). Mode: Operate. Code-led. Issue #22.
Audience: the single owner, desktop (long listening sessions) and phone (short catch-ups); under ~20 new items per visit; references are dark poster/HUD work, Ghost Hardware, designbyzeth, 90s OVA covers.
Task: play or open, mark read, reach "You're all caught up", while music or video keeps playing. Deal-breakers: unreadable all-caps reading text, platform/social look, music stopping on navigation or on any layout change.
Constraints: Thymeleaf + vendored htmx, no CDN, no-JS still works; one new self-hosted OFL mono WOFF2 approved; acceptance-test markup contracts kept (article data-title, class "item read", class "play", class "group-link" plain-text labels, class "load-more", hidden before/group inputs, time text yyyy-MM-dd, .ratio-16x9 > iframe, ids #bar #series #sheet #dock, swaps target only #sheet/#bar/#series).
Unresolved: reading article text in the pane is a separate issue; the pane holds only video on this branch.

## Direction contract

THESIS: The timeline is a 90s OVA cassette cover: a red obi strip names the series, the release list is the cover's spec panel, and a player deck runs along the bottom like the tape transport. It refuses the reader/music-app default of a grey sidebar, rounded cards and pill buttons.

OWN-WORLD: Dark: ink #0c0c12 ground, bone #f0ece4 text, obi red #ff3b2f for the obi band and live state only, cyan #2fd2c4 for "open in the pane" state only; light: the same drawing on bone paper with ink text, red and a darker cyan. Archivo Expanded for display (wordmark, obi series name, sheet heading, deck STV number, caught-up); Archivo mixed case for titles and body; a mono for STV numbers, dates, sources and control labels. Square corners, hairline rules, outlined mono buttons, caution chevrons on the obi, a barcode block for the playing STV number, registration crosses at region corners, fine grain.

STORY: The visitor sees which series they are in on the obi, scans readable titles by STV number, plays one and keeps scanning while the deck shows its waveform, watches a video big in the pane, tucks it into a mini window or into the deck itself, and marks the list down to "caught up".

FIRST VIEWPORT: Desktop: a vertical red obi band (~88px) full height on the left with the series name set vertically in Archivo Expanded and "n unread"; the top bar right of it (wordmark, Timeline/Sources, group tabs). The release list fills the rest: sheet head (Archivo Expanded heading, Show read, filled Mark all read), rows of STV number, art, title over source, date, Play and Mark read. A waveform deck (96px) fixed across the bottom while something plays: art, STV number in Archivo Expanded red, title, source, play/pause, waveform with elapsed/total, minimise/expand for video, Stop. A playing video opens as a theatre pane right of the list; the list narrows to a 460px index and a ◂ control collapses it to a 128px STV strip. Phone: the obi turns horizontal under the bar; deck at the bottom; theatre full width above it.

FORM: OVA Obi Strip, pinned by the user in re-roll round 1 of seed 9e4512a5 (it was the model's third grounded candidate in round 0), fused with the three-pane deck and reader-takeover organisation the user chose; video placement chosen by the user: theatre in the pane on Play, with Mini and Deck as the other two placements.

Signature move: the obi band and the waveform deck. The obi carries the series like a cassette spine; the deck's waveform fills red as it plays and is the seek control; a Pane / Mini / Deck switch moves a playing video between theatre, a floating mini window and a 16:9 slot in the deck without restarting it. Motion: 150ms state changes, one 240ms pane/mini/collapse transition; none under reduced motion.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
