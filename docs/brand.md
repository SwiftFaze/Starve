# The Starve mark

The logo is the obi tile: a red square carrying two overlapping squares in obi ink,
built the way the trademarks in *Logo Modernism* are: one primitive (the square) and one
effect (overlay). The first square is solid. The second is only an outline. Where they
overlap, the tile's red shows through, so you see the overlap without a third colour.

Files:

- `docs/brand/starve-mark-dark.svg`, `docs/brand/starve-mark-light.svg`: 512px masters.
- `src/main/resources/static/favicon-dark.svg`, `favicon-light.svg`: the 32px favicon cut.

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
| Favicon | 32 | 8 | L from 4 to 20 | 12 to 28, 2px (0.25u) | 12, 8 wide |

The favicon sits on whole pixels and uses a heavier outline (0.25u instead of 0.16u), so
that it still reads at 16px. Make any new small size (an app icon, a touch icon) on whole
pixels the same way. Don't scale the master down for it.

## Colour

It uses the obi pair from `DESIGN.md`, and nothing else:

| Theme | Tile | Ink |
|---|---|---|
| Dark | `obi-red` `#ff3b2f` | `ink` `#0c0c12` |
| Light | `paper-red` `#ef3326` | `obi-ink-light` `#ffffff` |

## Rules

- Square corners only. Don't round the tile or the squares.
- No letters, wordmark or stripes inside the tile. The mark stands on its own. The STARVE
  wordmark, when it appears, goes beside it.
- Keep the tile. The mark on bare ground loses the red that makes the overlap.
- Smallest size is 16px, using the favicon cut.

## In the app

Each template's `<head>` has `<link rel="icon" id="favicon">` ahead of `theme.js`.
`icon()` in `theme.js` points it at `favicon-dark.svg` or `favicon-light.svg` for the
current theme. It runs on load, when the OS theme changes and when the obi switch is
pressed, so the tab icon flips with the shutter. With scripts off, it stays dark.

## Exporting a PNG

Any SVG renderer will do. With Chrome, which is already on the dev machine:

```sh
chrome --headless=new --hide-scrollbars --window-size=512,512 \
  --screenshot=starve-mark-dark.png docs/brand/starve-mark-dark.svg
```
