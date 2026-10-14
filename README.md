# Starve

A self-hosted, single-user personal feed aggregator (RSS-first: YouTube,
SoundCloud, news). Built with Kotlin, Spring Boot, Thymeleaf/htmx and SQLite.
Feed features are tracked in the GitHub issues. Today you can subscribe to
RSS/Atom feeds; Starve fetches them in the background and shows their items in
a single chronological timeline.

## Run locally

Needs JDK 21+.

```
./mvnw spring-boot:run
```

Open <http://localhost:8080>. The database is `./data/starve.db` (override with
`STARVE_DB_PATH` or `--starve.db.path`).

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

The home page (`/`) lists every unread item from all sources, newest first, 50 at
a time with a **Load more** link. Opening an item marks it read and goes to its
link (blog posts open in the reader pane instead, see below); the mark-read button
marks it without opening. **Mark all read** marks
everything shown when the page was rendered, so items that arrive meanwhile stay
unread. **Show read** adds already-read items back to the list. Read items are
kept until the retention age removes them.

Above the list, a navigation shows **All** and each group (alphabetical) with its
unread count, e.g. `Music (12)`. Choosing a group (`/?group=<id>`) shows only its
sources' items with the same order, paging and read state; **Mark all read** and
**Load more** stay within the group. Unknown group ids give a 404.

## Favourites

Each timeline row and the reader pane has a star. Starring an item keeps it: the
retention purge never deletes a favourite, and starring does not change whether
the item is read. **Favourites** in the navigation (`/?favourites=1`) lists the
starred items of every source, read or not, newest first, with the same paging.
Clicking the star again removes the favourite. Removing a source deletes its
favourites with the rest of its items.

## Searching

The search box above the list finds items by words in their title, their
source's name or their post body (the text only; markup is ignored). Every word
you type must match, and the last word also matches as a prefix, so results
appear while you type. Results are newest first and include read items; the group
and **Show read** settings do not apply, and there is no **Mark all read**. The
URL (`/?q=...`) can be bookmarked, and **Load more** keeps the search. Search
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

The post sits in a fixed column that scrolls on its own, so scrolling the list never
moves it. Drag the splitter between the list and the post to resize them; the width is
remembered. A video playing in the pane stays there when a post opens and sits above the
post as a 16:9 strip; drag the horizontal splitter under it (or press the Up and Down
arrow keys) to resize it, and the height is remembered too. The list, the video and the
post share the window, so resizing one never hides another. Empty paragraphs and runs
of line breaks are dropped from posts, so feed padding does not show as blank lines.

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

Switching between **Timeline** and **Sources**, and adding, removing or grouping
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
