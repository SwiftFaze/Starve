# Architecture

## Overview

Starve is a single deployable Spring Boot (Kotlin) application: one jar, one
container. `dev.starve.StarveApplication` is the entry point. Controllers in
`web` render Thymeleaf templates (htmx is vendored at
`src/main/resources/static/htmx.min.js`, no CDN); data lives in one SQLite file
(`starve.db.path`, default `./data/starve.db`, `/data/starve.db` in Docker)
accessed with Spring `JdbcClient` (no JPA). Flyway applies
`src/main/resources/db/migration/V*.sql` on startup. V5 is the exception: a Java migration bean (`DecodeItemTitlesMigration`), so the next SQL migration is V6. Actuator exposes only
`/actuator/health`. There is no authentication (single user, self-hosted).

## Modules

All under `dev.starve`.

| Package | Responsibility | May depend on |
|---|---|---|
| `domain` | business rules, no framework code | nothing (JDK + Kotlin only) |
| `application` | use cases, orchestration | `domain` |
| `web` | controllers and templates | `application`, `domain` |
| `infra` | persistence and external adapters | `application`, `domain` |

## Feed ingestion

`domain` holds `Source`, `Feed`/`FeedItem` and `FeedRules` (item identity, URL
validity, retention cutoff). `application` defines the ports `FeedFetcher`,
`SourceRepository` and `ItemRepository`, and the services `SubscriptionService`
(subscribe/unsubscribe), `PollService` (poll all, isolating per-source failures),
`FeedIngestor` (records one fetch outcome) and `RetentionService`. `infra`
implements the ports: `RomeFeedFetcher` (JDK `HttpClient`, conditional GET, ROME
parsing, one timeout over the whole exchange) and the `Jdbc*Repository` classes;
`FeedConfig` wires the services and `SchedulingConfig` runs polling and
retention on `@Scheduled` timers. Items are unique per `(source, key)` where key
is the guid, else the link.

Item titles are HTML-entity-decoded once, at ingestion: `RomeFeedFetcher` runs each
title through `TitleEntities` (decimal, hex and named references, single pass; bare `&`
and unknown references are kept), then trims, so storage and templates see plain text.
`DecodeItemTitlesMigration` (V5) applied the same decoding to titles stored earlier.

Subscribing first resolves the pasted URL through the `FeedLocator` port
(`HttpFeedLocator`, sharing `BoundedHttp` with `RomeFeedFetcher`). Resolution
order: a non-HTML response is the feed itself; for HTML, the first RSS/Atom
`<link rel="alternate">` (YouTube pages carry one); else, on a configured
SoundCloud host with a one-segment path, the numeric user id in the page is
substituted into the feed URL template; else `NoFeedFound`. The HTML rules are
pure and live in `domain/FeedDiscovery`. The resolved URL is then fetched and
validated as before.

## Timeline and read state

`domain/Timeline.kt` holds `TimelineItem`, the keyset `Cursor` and `TimelineRules`
(page size, look-ahead split). Paging is keyset on `(published_at, id)`, not
offset, so new arrivals never shift a page; the repository fetches one extra row
to learn whether a next page exists. Read state is the nullable `items.read_at`
(migration V3). "Mark all read" carries the page's render timestamp and marks
only items with `first_seen_at` at or before it, so items ingested after the
page was rendered stay unread. `TimelineService` orchestrates; `HomeController`
only translates requests and formats views.

Favourites: `items.favourited_at` (V9, nullable; the timestamp is the mark) flags a
favourite. `FavouriteService` (wired by `FavouriteConfig`) stars and unstars through
`ItemRepository.favourite`/`unfavourite`, both idempotent and independent of read state.
`TimelineService.favourites` serves the `/?favourites=1` view through
`ItemRepository.favourites`, which ignores read state, show and group. The retention
exemption lives in the SQL of `deleteFirstSeenBefore` (`favourited_at IS NULL`), and
removing a source deletes its favourites through the items' `ON DELETE CASCADE`.
`FavouriteController` answers htmx toggles with the re-rendered `StarView`.

## Source groups

`domain/Group.kt` holds `Group` and `GroupRules.checkName` (trim, 1 to 50 chars).
Groups live in the `groups` table with a nullable `sources.group_id`
(`ON DELETE SET NULL`, migration V4), so a source has at most one group and
deleting a group ungroups it. `GroupService` owns the name rules, case-insensitive
uniqueness and the navigation counts. The group filter is an optional group id
passed through `TimelineService` to `ItemRepository.timeline` and `markAllRead`,
which restrict by `sources.group_id`; the render-timestamp cutoff still applies.

## Bulk import

`ImportService` takes the uploaded bytes, picks OPML (starts with `<`) or Takeout
CSV, and stores the resulting `ImportEntry` list through the existing source and
group repositories. CSV parsing is pure domain (`TakeoutCsv`); OPML parsing sits
behind the `OpmlReader` port, implemented by `infra/XmlOpmlReader` with DTDs and
external entities disabled. Nothing is fetched in the request: new source ids go to
`BackgroundPoller`, which polls them on a daemon executor.

## Inline playback

`domain/Playable.kt` is a sealed type (`YouTube`, `SoundCloud`) derived from an
item's link by `Playable.of`; the strict host and id checks live there, and
`TimelineItem.playable` tells the view whether to show Play. `PlayerService`
resolves `GET /items/{id}/player`, marks the item read and, for YouTube, asks the
`EmbedChecker` port. `YouTubeEmbedChecker` (infra) calls oEmbed: 401/403 means
embedding is disabled, anything else fails open. All outbound HTTP (feed fetch,
feed location, oEmbed) goes through the shared `BoundedHttp` helper, which bounds
the whole exchange, body included, by one timeout.

## Detail view

`DetailService` resolves `GET /items/{id}/detail` (`DetailController`, fragment
`templates/detail.html`, rendered into the reader pane). It returns null, a 404, for an
unknown item or one whose link is not `Playable`; otherwise it marks the item read and
lays SoundCloud's oEmbed over the stored item (artist, thumbnail, description), falling
back to the feed data. The `SoundCloudOembed` port is implemented by
`infra/SoundCloudOembedClient` through `BoundedHttp`, parsed by `infra/OembedJson`; a
successful answer is stored once per item in `items.oembed_json` (migration V8), a
failed one is not, so the next open retries. YouTube items use feed data only.

## Reader pane and post bodies

`RomeFeedFetcher` picks each entry's body (content, else description) and passes it
through `infra/BodySanitizer`, a jsoup allow-list (relaxed, minus style and data:
URLs, with links forced to `target=_blank rel="noopener noreferrer"`), capped at
200000 characters. It also drops blocks left with no text or image (empty `<p>`,
`&nbsp;` spacers) and collapses runs of `<br>`, since feeds pad posts with those.
Sanitising happens once, at ingestion; the stored `items.body`
(V6, nullable) is trusted at render time, and `ItemRepository` only fills a missing
body on a later insert. `ReaderService` (wired by `ReaderConfig`) resolves
`GET /items/{id}/reader`: it reads the `PostRef` through `ItemRepository.findPost`,
marks the item read, and returns null (404) for an unknown or playable item, which
keeps using the player. `ReaderController` renders `reader.html` as an htmx
fragment and offers "Open original" only for http(s) links.

Reader layout (`deck.js`, `starve.css`): the reader shares the list's pane layout, so the
`data-split` attribute on `body` (set while a video is in the pane or a post is open)
drives the list width and the vertical splitter. On desktop the post is a fixed column
that scrolls on its own, so the list scrolling never moves it. With a pane video, the
video is a 16:9 strip above the post, and the horizontal `video-splitter` sets its height
(`--video-h`, persisted as `starve.videoh`, Up/Down arrows step 32px, at least 120px for
the video and 200px for the post). Opening a post never moves a video out of the pane.

No layer contract changes: the sanitiser is an infra detail behind the existing fetcher port.

## Dependency rule

Dependencies point inward: `web`/`infra` -> `application` -> `domain`. Nothing
depends on `web`, and `web` does not touch `infra`. `ArchitectureTest`
(ArchUnit) enforces this under the verify command. A violation is fixed by
inverting the dependency or extracting an interface, never by weakening the
rule (`.claude/workflow.md`).

## Key decisions

- SQLite uses a single-connection pool (one writer, avoids "database is
  locked"); the parent directory of the DB file is created at startup.
- Kotlin targets JVM 21 bytecode so CI (Temurin 21) and newer local JDKs agree.
- Feature files stay in `specs/features/`; the build copies them onto the test
  classpath for Cucumber (see `docs/testing.md`).
