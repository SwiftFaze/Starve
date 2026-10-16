# Starve

A self-hosted, single-user personal feed aggregator (RSS-first: YouTube,
SoundCloud, news). Built with Kotlin, Spring Boot, Thymeleaf/htmx and SQLite.
Feed features are tracked in the GitHub issues. Today you can subscribe to
RSS/Atom feeds; Starve fetches them in the background and shows their items in
a single chronological timeline.

## Install with one command

With Docker, this builds `master` straight from GitHub and starts Starve on port 8080,
keeping the database in the `starve-data` volume:

```
docker build -t starve https://github.com/SwiftFaze/Starve.git#master && docker run -d --name starve --restart unless-stopped -p 8080:8080 -v starve-data:/data starve
```

The full install and setup guide is also served by Starve itself at `/docs`.

## Run locally

Needs JDK 21+.

```
./mvnw spring-boot:run
```

Open <http://localhost:8080>: a public landing page with a **Sign in** link. Once
signed in you land on the timeline at `/feed`, and **Sign out** in the top bar
returns you to the landing page. The database is `./data/starve.db` (override with
`STARVE_DB_PATH` or `--starve.db.path`).

## First run: create the admin account

Starve needs an account. On a fresh database every page redirects to `/setup`,
which asks for a one-time setup code. The code is written to the application
log at startup (`Starve setup code: XXXX-XXXX`): in the terminal for
`./mvnw spring-boot:run`, or `docker compose logs` for Docker. It is held in
memory only and a new one is issued on each start until an account exists.
Enter it, choose a username and a password (at least 10 characters), and you
are signed in. After that `/setup` is closed.

Behind a reverse proxy, forward `X-Forwarded-For` and `X-Forwarded-Proto`
(`server.forward-headers-strategy: native` is set), so failed attempts are
throttled per real client address rather than per proxy.

## Letting a friend in

Signups are off by default. As the admin, open **Settings** in the top bar and
turn on **Allow new signups**; your friend visits `/signup`, picks a username
and a password and is signed in with their own empty account (never an admin).
Turn the switch off again when they are in. Failed signup attempts are throttled
per client address.

### Passwords and removing a user

Everyone can change their own password on **Account** (top bar): enter the
current one and a new one. If a friend forgets theirs, open **Settings** as the
admin and press **Reset password** next to their name: Starve shows a temporary
password once, which you hand over; they sign in with it and change it on
**Account**. To remove a friend, press **Delete** in the same list and confirm:
their sources, groups and items go with them, and any session they still have is
signed out. You cannot reset or delete your own account.

## Subscribing to feeds

Open **Sources** (<http://localhost:8080/sources>) and paste a feed URL, or just
the address of a page: a YouTube channel, a SoundCloud profile or any website that
advertises its feed (`http` or `https`). Starve fetches the URL, follows the page's
RSS/Atom `<link rel="alternate">` (or, for a SoundCloud profile, its public feed)
and subscribes to that feed. A page with no discoverable feed is rejected with a
message asking for the feed URL. Starve fetches the feed once to confirm it is
valid RSS or Atom before saving, and rejects duplicates (including a page whose
feed you already follow). Each source shows its title, when it
was last fetched and its last error, if any; unsubscribe from the same page.

Subscriptions are polled in the background with conditional GET (ETag /
Last-Modified), so unchanged feeds cost almost nothing. One failing feed never
stops the others. Items older than the retention age (measured from when Starve
first saw them) are deleted daily.

## Importing subscriptions

**Sources** also has an **Import** page (`/import`) that takes a YouTube Google
Takeout `subscriptions.csv` or an OPML file (up to 1 MB), detected from its
content. Each channel or outline becomes a subscription; feeds you already follow
are counted, not duplicated. OPML folders become groups (matched ignoring case,
created when missing), and a feed that already has a group keeps it. Unusable
rows are listed with their reason while the rest import. Imported feeds are not
fetched during the upload; they are polled in the background afterwards.

## Post images

Every post in the timeline and the reader shows an image. Starve takes the first
of the post's `media:thumbnail`, an image `media:content`, an image enclosure, its
`itunes:image`, or the first body image that is not a tracking pixel. A post with
none falls back to its site's favicon: the page's icon link, else `/favicon.ico`.
The favicon is looked up during polling (checked again weekly) and read from the
source at render time, so it is never copied onto items.

## Grouping sources

On **Sources** you can create groups (1 to 50 characters, unique ignoring case),
rename and delete them. Each source belongs to at most one group, chosen from the
selector beside it; "Ungrouped" clears it. Deleting a group ungroups its sources
and keeps their items.

## Reading the timeline

The timeline (`/feed`) lists every unread item from all sources, newest first, 50 at
a time with a **Load more** link. Opening an item marks it read and goes to its
link (blog posts open in the reader pane instead, see below); the mark-read button
marks it without opening. **Mark all read** marks
everything shown when the page was rendered, so items that arrive meanwhile stay
unread. **Show read** adds already-read items back to the list. Read items are
kept until the retention age removes them.

Above the list, a navigation shows **All** and each group (alphabetical) with its
unread count, e.g. `Music [12]`. Choosing a group (`/feed?group=<id>`) shows only its
sources' items with the same order, paging and read state; **Mark all read** and
**Load more** stay within the group. Unknown group ids give a 404.

## Favourites

Each timeline row, the reader pane, the player deck and the detail view of a YouTube or
SoundCloud item has a star. A star toggled in any of them updates the item's others at once. Starring an item keeps it: the
retention purge never deletes a favourite, and starring does not change whether
the item is read. **Favourites** in the navigation (`/feed?favourites=1`) lists the
starred items of every source, read or not, newest first, with the same paging.
Clicking the star again removes the favourite. Removing a source deletes its
favourites with the rest of its items.

## Later

Each timeline row, the reader pane and the detail view have a clock button that saves the
item for later. **Later** in the navigation (`/feed?later=1`, after Favourites) lists the
saved items of every source, read or not, earliest saved first, 50 at a time with
**Load more**; it has no **Show read** or **Mark all read**. Saving does not change
whether the item is read or a favourite, and opening, reading or playing it leaves it
saved. Saving an already saved item keeps its original time. Click the clock again to
clear it; a cleared row stays on screen until the next reload. The retention purge never
deletes a saved item, though removing a source still deletes its items. If both
`favourites=1` and `later=1` are given, Favourites wins. Searching from Later still searches
every item, saved or not, and saved items stay searchable; clearing the search returns to Later.

## Searching

The search box above the list finds items by words in their title, their
source's name or their post body (the text only; markup is ignored). Every word
you type must match, and the last word also matches as a prefix, so results
appear while you type. Results are newest first and include read items; the group
and **Show read** settings do not apply, and there is no **Mark all read**. The
URL (`/feed?q=...`) can be bookmarked, and **Load more** keeps the search. Search
syntax such as `OR` or `-word` is not interpreted: those are ordinary words.
Items stored before this feature are indexed automatically on upgrade.

## Reading posts in the reader pane

Clicking the title of a blog post (any item that is not a YouTube video or
SoundCloud track) shows it in a reader pane beside the list instead of leaving
for the original site. The list stays in place, the post is marked read, and
anything playing keeps playing. The reader shows the title, source, published
date and the post body, with **Open original** (new tab, http/https links only)
and **Close**. Without JavaScript the title still follows the link as before.

Starve stores each entry's body (the feed's full content, else its description)
when it first fetches the item, cleaned of scripts, frames, forms, styles, event
handlers, and any link or image URL that is not http(s) (links may also be
`mailto:`). Links open in a new tab. Bodies are capped at 200,000 characters. A
body is only filled in for an item that has none, never overwritten, so posts
fetched before this feature show the title and **Open original** only. A post
whose feed carried no body shows a short note instead of text.

When a post's stored body is only a teaser (under 500 characters of text), opening it
fetches the original article page once, and the reader shows the article's text instead
(the `<article>` or `<main>` content, cleaned as above). The page's `og:image` fills in
a missing image. The fetch gives up after 5 seconds, ignores non-HTML pages and pages
over 1 MB, and keeps the teaser if the page has no longer text. A failed or empty attempt
is retried at most once a day, so reopening a post does not hammer its site. A fetched
article is searchable like any other post body.

On a desktop the list, an open post and a playing video are tiled like tmux panels,
each scrolling on its own under a small title bar (see **Video layouts** below). Drag
the splitters between panels (or focus one and press the arrow keys; double-click
resets) to resize them; sizes are remembered. Drag a panel's title bar onto another
panel to swap the two; its × closes the post or ejects the video. The panel you last clicked is
outlined in red. Empty paragraphs and long runs of line breaks are dropped from posts,
so feed padding does not show as blank lines.

On a phone a post opens full screen above the player; the top bar, list and video step
aside until **Close**, which returns you to the same place in the list. The post's head
tucks away while you read down and comes back as soon as you scroll up, and a button
returns to the top of a long post or list.

## Detail view for tracks and videos

Clicking the title of a SoundCloud track or YouTube video opens a detail view in the
reader pane instead of leaving for the original site: large artwork, title,
artist or channel, published date and description, with **Open original**, **Close**
and a **Play** button (it reads **Pause** while that item is playing). Opening the
view marks the item read; Play loads the item into the player exactly as the row's
Play does, and a video already playing in the pane stays in the pane. Without
JavaScript the title still follows the link as before.

For SoundCloud, Starve asks SoundCloud's public oEmbed (no API key) for the artist,
artwork and description once per track and stores the answer with the item. If that
request fails, the view shows the feed's own data and asks again the next time it is
opened. YouTube items use the feed data only.

## Playing items inline

Items whose link is a YouTube video (`watch?v=`, `youtu.be`, `/shorts/`) or a
SoundCloud track show a **Play** control. It opens the player in place, without
leaving the timeline, and marks the item read. YouTube plays from
`youtube-nocookie.com`, SoundCloud through its widget. When the video owner has
disabled embedding, a message and a link to watch it on YouTube replace the
player. If YouTube cannot be asked, Starve assumes embedding is allowed. Other
items keep the open-link behavior only.

On a desktop a playing YouTube video gets a panel of its own beside the list and any open
post. Drag a panel's title bar onto another panel to split it on that side (left, right,
top or bottom), onto its middle to swap the two, or onto an edge of the window to give it
that whole edge, for example a full-width band along the bottom. A cyan preview shows
where it will land; the arrangement and every size are remembered. Inside a panel the
video keeps 16:9 at the largest size that fits, so there are no black bars. **Eject** stops playback and closes the deck. On a
phone the video always sits full width above the deck, and the waveform runs across
the top of the deck where it is easy to reach.

Switching between **Timeline**, **Sources**, **Account** and **Settings**, and adding, removing or grouping
sources, swaps the page around the player, so whatever is playing keeps playing.
A browser reload or opening an item still starts the page afresh.

## Configuration

Set as Spring properties (`--starve.poll.interval=PT15M`) or environment
variables (`STARVE_POLL_INTERVAL=PT15M`). Durations use ISO-8601.

| Property | Default | Meaning |
|---|---|---|
| `starve.db.path` | `./data/starve.db` | SQLite file |
| `starve.poll.interval` | `PT30M` | delay between polls of all sources |
| `starve.fetch.timeout` | `PT10S` | limit for one whole feed fetch, body included |
| `starve.soundcloud.hosts` | `soundcloud.com,m.soundcloud.com` | hosts whose single-segment URLs are treated as SoundCloud profiles |
| `starve.soundcloud.feed-url-template` | `https://feeds.soundcloud.com/users/soundcloud:users:{id}/sounds.rss` | feed URL for a profile; `{id}` is its numeric user id |
| `starve.soundcloud.oembed-url` | `https://soundcloud.com/oembed` | oEmbed endpoint used for the detail view of a SoundCloud track |
| `starve.youtube.oembed-url` | `https://www.youtube.com/oembed` | oEmbed endpoint used to check whether a video may be embedded |
| `starve.youtube.feed-url-template` | `https://www.youtube.com/feeds/videos.xml?channel_id={id}` | feed URL for an imported Takeout channel; `{id}` is its channel id |
| `starve.retention.max-age` | `P90D` | items first seen longer ago are purged |
| `starve.retention.interval` | `PT24H` | delay between retention purges |
| `starve.scheduling.enabled` | `true` | `false` turns off background polling and purging |

## Run with Docker

```
docker compose up --build
```

The app listens on port 8080; the SQLite file lives on the `starve-data` volume
mounted at `/data`, so data survives restarts.

## Develop

- `./mvnw -B verify` runs unit, architecture and Cucumber acceptance tests plus
  coverage, detekt and ktlint.
- See `docs/architecture.md` and `docs/testing.md`; the spec-first workflow is in
  `.claude/workflow.md`.
