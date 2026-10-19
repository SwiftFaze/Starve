---
version: 1
slug: "src-main-resources-templates-sources-html"
primary_target: "src/main/resources/templates/sources.html"
related_targets: []
---

# Sources surface brief

Scope: `/sources` (sources.html, its source-row fragment). Mode: Operate. Code-led. Issue #153 (milestone 8 closing check), redone at the owner's request because the Tags section read badly.
Audience: the owner and each signed-in user, desktop and phone; occasional short visits: paste a URL, see that nothing is failing, file a source into a group, make or tune a tag, prune.
Task: add a source and read the outcome; see each source's health at a glance; move a source between groups; create, rename and delete groups; create, edit and delete tags.
Constraints: Obi Deck world unchanged (DESIGN.md); plain HTML forms with htmx as an enhancement, every fold a native details element; acceptance markup kept: `<li class="source" data-title` rows with no nested li, `<li class="group" … data-group`, `<li class="tag" data-tag="…" data-terms="…"`, `form class="new-tag…"`, `#add-url`, `#message`, the interval select's aria-label, hx-target/select/select-oob on every sheet form.

## Direction contract

THESIS: Sources is the label's catalogue filed by series: every source sits under its group's head, so where a source lives is read from the page, not from a chooser; tags run beside as saved views. It refuses the settings-page default of one long list with five controls a row and stacked edit forms.

OWN-WORLD: The Obi Deck as built: ink or bone paper, red obi on the left, Archivo Expanded for heads (sheet heads and group section heads), Archivo for names, Martian Mono for SRC numbers, health lines, counts and term chips; hairline rows, strong-hairline section rules, outlined square controls, one filled control per surface.

STORY: The owner scans group by group, sees health under each name and a red tag on a failing one, opens a row's Settings to move it, change its interval, fetch it now or remove it; renames a group by editing its head; reads each tag as its name, scope and term chips, and opens Edit only to change one.

FIRST VIEWPORT: Desktop: obi; left about three fifths: SUBSCRIPTIONS head with counts, then the add line (URL field, filled Add, Import, New group), then section heads MUSIC [2], TECH NEWS [2], PRIVACY [1], UNGROUPED [1], each over one-line source rows (SRC number, name over a mono health line, Settings). Right: TAGS head with New tag, then compact tag blocks of name, scope and hairline term chips, each with Edit. Both columns scroll under pinned heads. Phone: one column, Tags after the roster.

FORM: Roster filed by group, third of my seven grounded structures, chosen by the user from the dealt hand (seed key a9d4e18a).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
