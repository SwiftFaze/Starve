---
version: 1
slug: "src-main-resources-templates-sources-html"
primary_target: "src/main/resources/templates/sources.html"
related_targets: []
---

# Sources surface brief

Scope: `/sources` (sources.html). Mode: Operate. Code-led. Extends the established Label Catalogue world; no DESIGN.md system change beyond documenting the first built fields.
Audience: the single owner, desktop and phone; visits are occasional and short: paste a URL, check that nothing is failing, file a source into a group, prune.
Task: add a source by URL and read the outcome; see every source's last fetch and any error at a glance; assign groups; create, rename and delete groups.
Constraints: template + CSS only (no controller change); plain HTML forms, no JS required; acceptance-test markup contracts kept (`<li class="source"` with data-title, `class="last-fetched"`, `class="error"`, `<li class="group"` with data-group, the message text, form actions and field names).
Decisions (user, 2026-10-12): rows start with `SRC` + zero-padded source id; the smart-add form lives in the orange sleeve; a failing source shows an ink "Failed" tag with the message in ink.

## Direction contract

THESIS: The Sources page is the label's roster: the same catalogue sheet as the timeline, listing who the label carries instead of what it released. It refuses the settings-page default of stacked fieldsets and bullet lists.

OWN-WORLD: Inherited unchanged: off-white paper, ink, signal orange sleeve; Akira Expanded throughout, outline cut for the sheet heading; square corners; 1px rules between rows, 2px for structure; outlined controls that invert on hover; fields square, 1.5px ink stroke, paper ground.

STORY: The owner sees how many sources the label carries and whether any are failing, pastes a URL in the sleeve and reads the result, then scans the roster by number, files sources into groups and prunes.

FIRST VIEWPORT: Ink bar as on the timeline: wordmark, a Timeline / Sources tab pair (Sources current, orange underline). Left, the sticky orange sleeve: SOURCES in display caps, counts (sources, groups, failing), the status message, then "Add a source": URL field and a filled ink Add. Right, the sheet: outline heading "Subscriptions", ruled rows SRC 0007 | title over URL | status (fetched time, or ink Failed tag + message) | group select + Set group | Remove. Below, a second outline heading "Groups": a create row, then ruled group rows (name field, source count, Rename, Delete). Phone: sleeve is a band holding the form; rows stack.

FORM: Brief-pinned by DESIGN.md ("a sheet of ruled source rows beside the same sleeve"); no roll. Seed key: none (pinned).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
