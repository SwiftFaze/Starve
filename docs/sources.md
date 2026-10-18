# Sources

How a pasted address becomes a subscription. Module layout and the rest of the server side are in
[`architecture.md`](architecture.md).

## Resolving a pasted URL

`SubscriptionService.subscribe` resolves the pasted URL through the `FeedLocator` port (`infra/HttpFeedLocator`,
sharing `BoundedHttp` with `RomeFeedFetcher`), checks the resolved URL for a duplicate, then fetches and validates it
as a feed before storing anything. Resolution order:

1. **Platform rules**, before anything is fetched. `domain/PlatformPages.match` recognises a page of a `PlatformSite`
   from its URL alone: a subreddit (`/r/<name>`), a Vimeo channel (`/channels/<name>`) or user (`/<name>`, never a
   numeric video id), a GitHub user (`/<user>`), an Apple Podcasts show (a path with `podcast`, ending in
   `id<digits>`). The match's key fills the site's template (`{key}`). For Apple Podcasts the template is the iTunes
   lookup API, which `HttpFeedLocator` fetches and `infra/ApplePodcastLookup` reads (`results[].feedUrl`); a lookup
   without one is `NoFeed`. Reddit pages are never fetched, which matters because Reddit often refuses non-browser
   clients: its feed's failures then show as ordinary source errors.
2. A non-HTML response is the feed itself.
3. For HTML, the first RSS/Atom `<link rel="alternate">` (YouTube, Bluesky, Mastodon, GitHub release pages and
   Substack carry one).
4. On a configured SoundCloud host with a one-segment path, the numeric user id in the page fills the SoundCloud
   feed-URL template.
5. Otherwise `NoFeedFound`.

The URL and HTML rules are pure and live in `domain` (`PlatformPages`, `FeedDiscovery`). `PlatformConfig` makes one
`PlatformSite` bean per platform from `starve.<platform>.hosts` and its template property, and Spring hands the list
to both `HttpFeedLocator` and `ImportService`. Keys only ever hold letters, digits, `_`, `-` and one `/`, so a template
cannot be steered elsewhere by the pasted URL.

## Polling

`SchedulingConfig` ticks every `starve.poll.tick` (one minute) and calls `PollService.pollDue`, which fetches the
sources `domain/PollRules.isDue` says are due: never tried, or their interval passed since their last attempt
(`last_fetched_at`, stamped on success and failure alike). The interval is the source's own `PollInterval`
(`sources.poll_interval_minutes`, V17, one of a fixed set; null or an unknown value means the default) or
`starve.poll.interval`. `SourceScheduleService` sets it (`SourceScheduleRepository`, owner-checked) and starts a
Fetch now through `BackgroundPoller`, which keeps an in-flight set: a source already queued or running is never
queued again, and `isFetching` is what renders a row as "Fetching now…". A fetching row polls
`GET /sources/{id}/row` every 2 seconds (`SourceScheduleController`, fragment `sources :: source-row`) until it
renders without the trigger. `web/SourceRoster` builds the rows for both controllers.

## Bulk import

`ImportService` never fetches during the upload, so it applies only the platform rules that need no fetch: a Reddit,
Vimeo or GitHub page entry is stored as its feed URL (and deduplicated as that URL). An Apple Podcasts entry would need
the lookup, so it is reported as a failed row asking for Smart-add instead.
