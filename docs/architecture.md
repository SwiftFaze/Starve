# Architecture

## Overview

Starve is a single deployable Spring Boot (Kotlin) application: one jar, one
container. `dev.starve.StarveApplication` is the entry point. Controllers in
`web` render Thymeleaf templates (htmx is vendored at
`src/main/resources/static/htmx.min.js`, no CDN); data lives in one SQLite file
(`starve.db.path`, default `./data/starve.db`, `/data/starve.db` in Docker)
accessed with Spring `JdbcClient` (no JPA). Flyway applies
`src/main/resources/db/migration/V*.sql` on startup. Actuator exposes only
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

## Source groups

`domain/Group.kt` holds `Group` and `GroupRules.checkName` (trim, 1 to 50 chars).
Groups live in the `groups` table with a nullable `sources.group_id`
(`ON DELETE SET NULL`, migration V4), so a source has at most one group and
deleting a group ungroups it. `GroupService` owns the name rules, case-insensitive
uniqueness and the navigation counts. The group filter is an optional group id
passed through `TimelineService` to `ItemRepository.timeline` and `markAllRead`,
which restrict by `sources.group_id`; the render-timestamp cutoff still applies.

## Inline playback

`domain/Playable.kt` is a sealed type (`YouTube`, `SoundCloud`) derived from an
item's link by `Playable.of`; the strict host and id checks live there, and
`TimelineItem.playable` tells the view whether to show Play. `PlayerService`
resolves `GET /items/{id}/player`, marks the item read and, for YouTube, asks the
`EmbedChecker` port. `YouTubeEmbedChecker` (infra) calls oEmbed: 401/403 means
embedding is disabled, anything else fails open. All outbound HTTP (feed fetch,
feed location, oEmbed) goes through the shared `BoundedHttp` helper, which bounds
time and body size.

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
