# Front end

The htmx contracts between the server's templates and the browser's scripts (`static/theme.js`, `static/deck.js`).
Module layout and the server side are in [`architecture.md`](architecture.md).

## Page switches

Every in-app navigation link carries `data-switch` plus htmx attributes (and keeps its `href`, so it works without
JavaScript); `static/theme.js` runs each behind the red shutter. Form posts are plain page loads. The contract the
templates share:

- Every page that can be swapped to wraps its sleeve and sheet in `.label#shell`. The signed-in top bar is the
  `#bar` header (`app-bar.html`, `bar-actions.html`; the public pages use `public-bar.html`); a switch swaps
  `#shell` with `hx-select="#shell"` and `hx-select-oob="#bar"` so the bar's current-page marks follow.
- Timeline and Sources swap only `#sheet` (plus `#bar,#series,#panel` out of band), which is what lets the player
  keep playing. The `#dock` sits outside the shell on Timeline, Sources, Account and Settings; a shell swap between
  two of them keeps the live dock. A page without a dock (Import, delete user, the temporary-password page, the
  public pages) removes it and so stops playback.
- Before a shell swap, `theme.js` (`htmx:beforeSwap`) copies the response body's class and `data-*` attributes
  (carrying `playing` and `video` over when the dock is kept), the dock and the meta description. A response without
  `#shell` (a fragment, an `HX-Redirect` after a session ends) is not swapped; the link is followed as a plain load.
- Pages loaded from a switch need their scripts: `deck.js` on the dock pages and Import, `docs.js` on the public
  pages (it also enhances code blocks after a swap), `htmx.min.js` everywhere.

## Live unread counts

Every htmx response that marks an item read raises the htmx event
`starve:read` through an `HX-Trigger` header (`ReadEvent.kt`). `HomeController.markRead`
sets it on its own `ResponseEntity`; the reader, detail and player handlers are marked
`@AnnouncesRead`, and `ReadEventInterceptor` adds the header in `postHandle`, so a 404
raises nothing and detekt's five-parameter limit needs no response parameter. The
timeline's hidden `#counts` listener (`hx-trigger="starve:read from:body"`) then fetches
`/feed/counts` for the view on screen (group, Favourites or Later, read state; never the
search or paging), whose `counts.html` fragment swaps the obi's `#series-unread` and the
links inside `#groups` out of band. That costs one small GET per read, which is nothing
on a LAN.
