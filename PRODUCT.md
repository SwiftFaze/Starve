# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

The owner, who self-hosts Starve and administers it. They read it in a browser on
desktop and on phone, and neither one is the main device. On phone it
is short catch-ups; on desktop it is longer sessions, often watching or listening
inline. The job is to see everything new from the sources they chose, and nothing
else, then be done.

The owner may also let in a few people they know (a partner, a friend) with their
own accounts. Each account is a separate, private Starve: its own sources, groups,
read state, favourites and Later. Nobody sees anyone else's data, and only the
owner (the admin) manages the instance. Starve is still designed for one reader at
a time and one owner per instance, not for a community.

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

- Runs on a home server, LAN-first; it may sit behind a reverse proxy, so every
  page except the landing, `/docs` and the access pages needs a signed-in account.
- **Access model:** on a fresh database every page leads to `/setup`, where the
  first person claims the instance as admin with a one-time setup code printed to
  the application log. Sign-ups are off by default; the admin turns them on in
  Settings for as long as a friend needs to create an account, then off again.
  The admin can reset another user's password (a temporary one, shown once) or
  delete the user with all their data; everyone changes their own password on
  Account. Failed sign-in, setup and sign-up attempts are throttled per client
  address.
- Signed out, `/` is a public landing page that explains Starve with a short demo,
  and `/docs` serves the install and setup guide.
- One Docker image or one jar. Data lives in a single SQLite file.
- A background scheduler polls every source (every 30 minutes by default) using
  conditional GET.
- Typical use: open the timeline, optionally narrow it to a group ("Music", "Tech
  news"), play or open items, mark the rest read, and reach "caught up".
- Sources are managed on a separate Sources page, mostly by pasting a channel,
  profile or site URL.

## Capabilities and Constraints

- **Timeline** (`/feed`): unread items from all sources, newest first, 50 per page
  with keyset paging ("Load more"). Show/hide read, mark one read, mark all read
  (only items rendered on the page, so new arrivals stay unread).
- **Groups:** each source belongs to at most one group. A group nav shows unread
  counts and filters the timeline (`/feed?group=<id>`).
- **Smart-add** (`/sources`): paste a feed URL or a YouTube, SoundCloud or website
  URL and Starve resolves its feed, or rejects it with a reason. Each source shows
  its last fetch time and last error, because failures must never be silent.
- **Inline playback:** YouTube (via youtube-nocookie) and SoundCloud items play in
  place and are marked read. A player deck along the bottom of the timeline shows
  what is playing with play/pause, a seekable waveform, elapsed and total time,
  volume and Stop, and keeps playing while the list is filtered or paged. A video
  plays in a pane beside the list, a resizable mini window or a slot in the deck
  (phones: always above the deck). If embedding is disabled, a link out replaces
  the player. Every other item links out.
- **Retention:** items are kept 90 days, and unread items from the last 7 days are
  never deleted.
- **Bulk import** (`/import`): a YouTube Takeout subscriptions CSV or an OPML file
  subscribes to every source in it, with a summary of what was added, already
  subscribed and failed.
- **Accounts:** sign in (`/login`), first-run setup claim (`/setup`), optional
  self sign-up (`/signup`, admin-toggled), own password change (`/account`), and
  admin user management in Settings (`/settings`: sign-up switch, reset password,
  delete user). Every account's data is private to it.
- **Theme:** dark and light, following the OS; a switch on the obi overrides it
  and is remembered in that browser.
- **Out of scope:** X/Twitter and Instagram, keyword mute or filter rules, any
  ranking or weighting, native mobile apps, public or open registration (sign-ups
  stay an admin's deliberate, temporary choice), shared or social features between
  accounts.
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
