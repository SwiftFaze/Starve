Feature: Smart-add
  The single "add source" input on /sources accepts a pasted page URL (a
  YouTube channel page, a SoundCloud profile page, or any website) as well as
  a raw feed URL, resolves it to the underlying RSS/Atom feed URL and
  subscribes to that feed.

  Resolution order: (1) the URL fetches and parses as RSS/Atom, so it is used
  as-is; (2) it is an HTML page, so the first
  <link rel="alternate" type="application/rss+xml|application/atom+xml">
  in the document is used, with a relative href resolved against the page URL;
  (3) it is a SoundCloud profile page (a configured SoundCloud host, a path of
  exactly one segment) with no alternate link, so the numeric user id is read
  from "soundcloud://users:<id>" in the page and the configured SoundCloud
  feed-URL template is filled in.

  Covers: resolving page URLs to feed URLs; storing the resolved feed URL;
  deduplicating on the resolved feed URL; the "no feed found at this address"
  failure message.
  Supersedes: nothing in feed-ingestion.feature's scenario set, with one
  consequence: an HTML page without any feed link is now reported as "no feed
  found at this address" instead of "not a valid RSS or Atom feed", so the
  feed-ingestion scenario "A URL that is not a feed is rejected and not
  stored" (served as an HTML page) must be updated by the implementer. Raw
  feed URLs and the other validation messages keep working as specified there.
  Out of scope: bulk import (#6, which reuses the resolver); X/Instagram URLs;
  API keys; headless browsers; scraping channel ids out of YouTube markup.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A YouTube-style channel page resolves to its feed
    Given the feed server serves "/youtube/feeds/videos.xml" as an Atom feed titled "Some Creator" with 3 entries having thumbnails
    And the feed server serves "/@somecreator" as an HTML page whose head links the alternate feed "/youtube/feeds/videos.xml" of type "application/rss+xml"
    When I add the feed URL "/@somecreator"
    Then the sources page lists a source titled "Some Creator"
    And the source "Some Creator" has 3 stored items
    And the source "Some Creator" is subscribed at the feed URL "/youtube/feeds/videos.xml"

  Scenario: A news site page resolves to its Atom alternate link
    Given the feed server serves "/news/atom.xml" as an Atom feed titled "Site News" with 2 entries
    And the feed server serves "/news/" as an HTML page whose head links the alternate feed "/news/atom.xml" of type "application/atom+xml"
    When I add the feed URL "/news/"
    Then the sources page lists a source titled "Site News"
    And the source "Site News" has 2 stored items
    And the source "Site News" is subscribed at the feed URL "/news/atom.xml"

  Scenario: A relative alternate href is resolved against the page URL
    Given the feed server serves "/blog/feed.xml" as an RSS 2.0 feed titled "The Blog" with 2 entries
    And the feed server serves "/blog/index.html" as an HTML page whose head links the alternate feed "feed.xml" of type "application/rss+xml"
    When I add the feed URL "/blog/index.html"
    Then the sources page lists a source titled "The Blog"
    And the source "The Blog" is subscribed at the feed URL "/blog/feed.xml"

  Scenario: The first of several alternate links is chosen
    Given the feed server serves "/first.xml" as an RSS 2.0 feed titled "First Feed" with 1 entries
    And the feed server serves "/second.xml" as an RSS 2.0 feed titled "Second Feed" with 1 entries
    And the feed server serves "/multi" as an HTML page whose head links the alternate feeds "/first.xml" and "/second.xml"
    When I add the feed URL "/multi"
    Then the sources page lists a source titled "First Feed"
    And the sources page does not list a source titled "Second Feed"
    And there is 1 subscription

  Scenario: A SoundCloud profile page resolves to the user feed
    Given SoundCloud is configured to use the feed server as its host
    And the feed server serves "/feeds/users/soundcloud:users:78443/sounds.rss" as an RSS 2.0 feed titled "Some Artist" with 2 entries
    And the feed server serves "/someartist" as a SoundCloud profile page for user id "78443"
    When I add the feed URL "/someartist"
    Then the sources page lists a source titled "Some Artist"
    And the source "Some Artist" has 2 stored items
    And the source "Some Artist" is subscribed at the feed URL "/feeds/users/soundcloud:users:78443/sounds.rss"

  Scenario: A raw feed URL still works unchanged
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    When I add the feed URL "/news.xml"
    Then the sources page lists a source titled "Daily News"
    And the source "Daily News" has 3 stored items
    And the source "Daily News" is subscribed at the feed URL "/news.xml"

  Scenario: A page without any feed link is rejected and nothing is stored
    Given the feed server serves "/plain" as an HTML page without any feed link
    When I add the feed URL "/plain"
    Then the sources page shows the message "no feed found at this address"
    And there are no subscriptions

  Scenario: A page whose alternate link cannot be fetched is rejected
    Given the feed server serves "/broken-feed.html" as an HTML page whose head links the alternate feed "/gone.xml" of type "application/rss+xml"
    And the feed server responds to "/gone.xml" with HTTP 404
    When I add the feed URL "/broken-feed.html"
    Then the sources page shows the message "could not be fetched"
    And there are no subscriptions

  Scenario: A SoundCloud page without a user id is rejected
    Given SoundCloud is configured to use the feed server as its host
    And the feed server serves "/mystery" as an HTML page without any feed link
    When I add the feed URL "/mystery"
    Then the sources page shows the message "no feed found at this address"
    And there are no subscriptions

  Scenario: A SoundCloud page with a non-numeric user id marker is rejected
    Given SoundCloud is configured to use the feed server as its host
    And the feed server serves "/badid" as a SoundCloud profile page for user id "abc"
    When I add the feed URL "/badid"
    Then the sources page shows the message "no feed found at this address"
    And there are no subscriptions

  Scenario: A SoundCloud-style page with more than one path segment is not treated as a profile
    Given SoundCloud is configured to use the feed server as its host
    And the feed server serves "/someartist/tracks" as a SoundCloud profile page for user id "78443"
    When I add the feed URL "/someartist/tracks"
    Then the sources page shows the message "no feed found at this address"
    And there are no subscriptions

  Scenario: A page whose feed is already subscribed is a no-op with a notice
    Given the feed server serves "/youtube/feeds/videos.xml" as an Atom feed titled "Some Creator" with 3 entries having thumbnails
    And the feed server serves "/@somecreator" as an HTML page whose head links the alternate feed "/youtube/feeds/videos.xml" of type "application/rss+xml"
    And I have added the feed URL "/youtube/feeds/videos.xml"
    When I add the feed URL "/@somecreator"
    Then the sources page shows the message "already subscribed"
    And there is 1 subscription
    And the source "Some Creator" has 3 stored items

  Scenario: An unreachable page is rejected and not stored
    Given the feed server responds to "/missing-page" with HTTP 404
    When I add the feed URL "/missing-page"
    Then the sources page shows the message "could not be fetched"
    And there are no subscriptions

  Scenario: Content that is neither a feed nor an HTML page keeps the existing message
    Given the feed server serves "/garbage.xml" as malformed XML
    When I add the feed URL "/garbage.xml"
    Then the sources page shows the message "not a valid RSS or Atom feed"
    And there are no subscriptions

  Scenario: A non-http URL is still rejected
    When I add the raw feed URL "ftp://example.com/page"
    Then the sources page shows the message "must be an http or https URL"
    And there are no subscriptions

# Non-goals:
#   - Bulk import (#6); the resolver is built as a reusable application-level
#     port with an infrastructure implementation so #6 can call it.
#   - X/Instagram URLs; API keys; headless browsers.
#   - Reading YouTube "channelId" values out of page HTML (the first one on a
#     channel page can belong to a different, featured channel); only the
#     page's rel="alternate" link is trusted.
#   - Following redirects through consent walls or logging in.
#
# Risks:
#   - Sites change markup; YouTube or SoundCloud may drop the alternate link or
#     the "soundcloud://users:<id>" marker. Failures surface as "no feed found
#     at this address", never as a silent partial subscription.
#   - Consent walls (e.g. EU cookie interstitials) can serve a page without
#     the feed link; accepted, the user can paste the feed URL instead.
#   - Acceptance tests never touch the internet: the SoundCloud hosts and feed
#     URL template are properties (starve.soundcloud.hosts,
#     starve.soundcloud.feed-url-template), defaulting to the real values and
#     overridden to the fixture server by the step "SoundCloud is configured
#     to use the feed server as its host". The template takes the numeric id
#     as a placeholder.
#   - The existing feed-ingestion scenario for an HTML page changes message
#     (see Supersedes above).
#   - Outbound fetching of user-supplied URLs (SSRF) stays accepted for a
#     single-user self-hosted app.
#   - New steps and FeedServer fixtures are needed (HTML pages with alternate
#     links, SoundCloud profile pages, "is subscribed at the feed URL",
#     malformed XML served at a path).
#
# Open questions:
#   - None. Decisions are recorded under Clarifications in
#     specs/intent/smart-add.md.
