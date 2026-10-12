# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

One person: the owner, who self-hosts Starve and is its only user. They read it in
a browser on desktop and on phone, and neither one is the main device. On phone it
is short catch-ups; on desktop it is longer sessions, often watching or listening
inline. The job is to see everything new from the sources they chose, and nothing
else, then be done.

## Product Purpose

YouTube, SoundCloud and news sites each push their own recommendation algorithm,
and over time those feeds fill with things the owner never asked for. What they
actually follow is scattered across platforms and buried. Starve gathers the
sources the owner subscribes to (YouTube channels, SoundCloud artists, news sites,
all through RSS/Atom) into one strictly chronological timeline they control.

Success is a feed the owner can finish: they open it, see only their sources
newest first, play or read what they want, and reach the end.

## Positioning

The name means *starve the algorithms*: the owner gives recommendation engines no
attention. Starve is a deliberate diet, not an infinite scroll. It has no ranking,
no suggestions and no engagement loop. Seen items drop out and the feed has an end.
Video and audio play inline, so the owner never lands on a platform page that
recommends the next thing.

## Operating Context

- Runs on a home server and is reached on the home LAN only. It is never exposed
  publicly and has no authentication, by design.
- One Docker image or one jar. Data lives in a single SQLite file.
- A background scheduler polls every source (every 30 minutes by default) using
  conditional GET.
- Typical use: open the timeline, optionally narrow it to a group ("Music", "Tech
  news"), play or open items, mark the rest read, and reach "caught up".
- Sources are managed on a separate Sources page, mostly by pasting a channel,
  profile or site URL.

## Capabilities and Constraints

- **Timeline** (`/`): unread items from all sources, newest first, 50 per page
  with keyset paging ("Load more"). Show/hide read, mark one read, mark all read
  (only items rendered on the page, so new arrivals stay unread).
- **Groups:** each source belongs to at most one group. A group nav shows unread
  counts and filters the timeline (`/?group=<id>`).
- **Smart-add** (`/sources`): paste a feed URL or a YouTube, SoundCloud or website
  URL and Starve resolves its feed, or rejects it with a reason. Each source shows
  its last fetch time and last error, because failures must never be silent.
- **Inline playback:** YouTube (via youtube-nocookie) and SoundCloud items play in
  place and are marked read. If embedding is disabled, a link out replaces the
  player. Every other item links out.
- **Retention:** items are kept 90 days, and unread items from the last 7 days are
  never deleted.
- **Planned:** bulk import from a YouTube Takeout subscriptions CSV and from OPML
  (issue #6).
- **Out of scope:** X/Twitter and Instagram, keyword mute or filter rules, any
  ranking or weighting, authentication or multiple users, native mobile apps.
- **Stack:** Kotlin, Spring Boot, server-rendered Thymeleaf with htmx (vendored, no
  CDN), SQLite. Pages must work as plain HTML forms and links, with htmx as an
  enhancement.

## Evidence on Hand

None, and none is needed: this is a personal tool with no audience to persuade.
Do not invent testimonials, user counts or claims.

## Product Principles

1. **Only what was chosen.** Nothing appears that the owner did not subscribe to.
   There are no suggestions, trending items or "you might like".
2. **Strictly chronological.** Order is publish time and nothing else.
3. **Finishable.** The feed has an end, and reaching it is the goal. The
   caught-up state reads as "done", never as "come back for more".
4. **Never leak to the platforms.** Watch and listen in place. Linking out is the
   fallback, not the default, for media.
5. **Fail loudly and locally.** A broken source shows its status. One failure
   never hides the rest.
