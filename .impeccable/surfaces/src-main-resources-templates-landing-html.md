---
version: 1
slug: "src-main-resources-templates-landing-html"
primary_target: "src/main/resources/templates/landing.html"
related_targets: ["src/main/resources/templates/login.html","src/main/resources/templates/signup.html","src/main/resources/templates/setup.html"]
---

# Landing (/) — public page for visitors

Mode: Persuade. Audience: someone the owner sends to this instance (a friend who may sign up while signups are open), or the owner themself signing in. Job: understand in seconds that Starve is a feed that ends, then sign in (or sign up when open).
Constraints: no <img> on the page (public-home-page.feature: "no screenshots"); every claim from PRODUCT.md only; no invented users, numbers or testimonials; works without JS; theme switch on the obi; phone width.

## Direction contract

THESIS: The landing page is the Starve cassette itself — a J-card cover whose release list visibly runs out. It refuses the category default of hero copy + screenshot + three feature cards.

OWN-WORLD: The Obi Deck at full volume: a wide red obi down the left carrying STARVE in vertical Akira and the chevron theme switch; ink (or bone) ground with film grain; hairline spec rules, registration crosses, STV catalogue numbers in Martian Mono, barcode block, Archivo for every sentence. Square corners, no shadows.

STORY: The visitor reads "Starve the algorithm", sees a demo release list (labelled as a demo) strike itself read row by row until it lands on "You're all caught up", reads the A/B-side tracklist of what it does and the spec block (no ranking, chronological, self-hosted, one SQLite file), and signs in.

FIRST VIEWPORT: Left: the obi, about 9rem wide on desktop, full height. Centre-left: the cover headline in Akira at display scale over a two-line pitch and the Sign in (filled) / Sign up (outline, only when open) actions. Right: the demo release list as a ruled spec panel, six rows, closing on the caught-up rule. Mono catalogue caption across the top of the cover.

FORM: Chosen form: the cassette J-card (grounded candidate 1 of the user-approved "Same world, full poster" direction, picked by the user in the question round; no concept-seed roll was run because the user locked the concept directly). Seed key: none (user-pinned).

Signature interaction: the demo list marks itself read once, staggered, ending on "You're all caught up" (the product's finishable feed, dramatised); reduced motion shows the finished state.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
