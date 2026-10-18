# The Starve mark

The logo is the obi tile: a red square carrying two overlapping squares in obi ink,
built the way the trademarks in *Logo Modernism* are: one primitive (the square) and one
effect (overlay). The first square is solid. The second is only an outline. Where they
overlap, the tile's red shows through, so you see the overlap without a third colour.

Files:

- `docs/brand/starve-mark-dark.svg`, `docs/brand/starve-mark-light.svg`: 512px masters.
- `docs/brand/starve-favicon-dark.svg`, `docs/brand/starve-favicon-light.svg`: 512px masters of the favicon.
- `src/main/resources/static/favicon-dark.svg`, `favicon-light.svg`: the favicon's 16px pixel-fitted cut.

All of them are hand-written SVG with four shapes and no fonts. Edit the numbers directly.

## Construction

Everything is in units of `u`, the overlap's side. Each square is `2u` and the second is
offset by `u` on both axes, so the overlap is exactly one quarter of each square. The two
squares together are `3u`, centred on the tile.

Paint in this order:

1. **Tile:** the full square in the obi red.
2. **Ink square:** `2u` at the mark's top-left, in obi ink. Draw it as an L-shaped path
   that leaves out the overlap quarter, not as a full square. If its edges coincide with the
   overlap's edges, anti-aliasing leaves a faint seam.
3. **Outline square:** `2u` offset by `(u, u)`, stroked in obi ink, with the stroke kept
   inside the square's edge (inset the rect by half the stroke width).
4. **Overlap:** `u` square at `(u, u)` from the mark's origin, filled with the tile red. It
   covers the outline's corner, so the outline seems to pass under the solid square.

| Cut | Tile | `u` | Ink square | Outline square, stroke | Overlap |
|---|---|---|---|---|---|
| Master | 512 | 108 | L from 94 to 310 | 202 to 418, 17.28 (0.16u) | 202, 108 wide |
## Colour

It uses the obi pair from `DESIGN.md`, and nothing else:

| Theme | Tile | Ink |
|---|---|---|
| Dark | `obi-red` `#ff3b2f` | `ink` `#0c0c12` |
| Light | `paper-red` `#ef3326` | `obi-ink-light` `#ffffff` |

The favicon is a separate icon, not a cut of the mark (see below), because the overlap mark
blurs into a speck at 16px.

## Rules

- Square corners only. Don't round the tile or the squares.
- No letters, wordmark or stripes inside the tile. The mark stands on its own. The STARVE
  wordmark, when it appears, goes beside it.
- Keep the tile. On bare ground the overlap has nothing to show through it.
- The mark's smallest size is 32px. Below that, use the favicon.

## The favicon

The tab icon is the feed in frame, running out: three rows that shorten, inside four corner
brackets, with a small red live square in the top-right corner, on the ground colour (ink in
dark, paper in light; the rows and brackets in the text colour). The 512px master also carries
small tick marks at the middle of each edge.

The 16px cut is drawn on whole pixels:

- the brackets are 1px lines, 3px long;
- the rows are 2px tall, 7, 6 and 4px long, from x=4, at y=4, 7 and 10;
- the red square is 2x2 at (12, 3);
- the tick marks are left out.

It doubles cleanly to 32px for high-density tabs. Don't scale the master down for it.

## In the app

Each template's `<head>` has `<link rel="icon" id="favicon">` ahead of `theme.js`.
`icon()` in `theme.js` points it at `favicon-dark.svg` or `favicon-light.svg` for the
current theme. It runs on load, when the OS theme changes and when the obi switch is
pressed, so the tab icon flips with the shutter. With scripts off, it stays dark.

The same `icon()` sets the two `theme-color` metas (ink `#0c0c12` for dark, paper
`#ebe8e0` for light, each for its `prefers-color-scheme`) to the theme in use, so the
browser's and the installed app's own chrome follow the obi switch too.

## The app icons

`manifest.webmanifest` (served as `application/manifest+json`, public like the icons)
installs Starve as a home-screen app that opens `/feed` standalone. Its icons are the
dark mark, full-bleed tile, cut from the master geometry above:

- `icon-192.png`, `icon-512.png`: the mark as drawn.
- `icon-maskable-512.png`: the tile stays full bleed and the squares shrink to 80% about
  the centre, so their corners sit inside the maskable safe circle (radius 40%).
- `apple-touch-icon.png`, 180px: iOS rounds the corners itself, so it is the plain mark.

Every page's head links the manifest and the touch icon after the favicon.

## Exporting a PNG

Any SVG renderer will do. With Chrome, which is already on the dev machine:

```sh
chrome --headless=new --hide-scrollbars --window-size=512,512 \
  --screenshot=starve-mark-dark.png docs/brand/starve-mark-dark.svg
```
