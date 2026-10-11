Feature: Feed ingestion
  Subscribe to RSS/Atom feed URLs on a /sources page, poll them (on add and on
  a schedule), and store deduplicated items per source, with per-source fetch
  status and age-based retention.

  Covers: adding and removing a subscription by raw feed URL; parsing RSS 2.0
  and Atom; storing items (title, link, published date, author, thumbnail,
  first-seen time); per-source deduplication; conditional GET (ETag /
  Last-Modified, 304 treated as success); per-source last-fetch time and last
  error with failure isolation; validation of added URLs; deletion of a
  source's items on removal; retention of items by first-seen age (90 days).
  Supersedes: nothing.
  Out of scope: timeline UI and read state (#3; so "never delete unread items
  from the last 7 days" holds trivially, as nothing under 90 days is deleted);
  smart-add of non-feed URLs such as YouTube channel pages (#4); groups (#5);
  bulk import (#6); playback (#7); per-source poll intervals.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: Add an RSS 2.0 feed and see its items stored
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    When I add the feed URL "/news.xml"
    Then the sources page lists a source titled "Daily News"
    And the source "Daily News" has 3 stored items
    And the stored items of "Daily News" have a title, a link and a published date
    And the source "Daily News" shows a last fetched time
    And the source "Daily News" shows no error

  Scenario: Add an Atom feed with thumbnails
    Given the feed server serves "/channel.atom" as an Atom feed titled "Some Channel" with 2 entries having thumbnails
    When I add the feed URL "/channel.atom"
    Then the sources page lists a source titled "Some Channel"
    And the source "Some Channel" has 2 stored items
    And every stored item of "Some Channel" has a thumbnail URL

  Scenario: Feed without a title falls back to its URL
    Given the feed server serves "/untitled.xml" as an RSS 2.0 feed without a title with 1 entries
    When I add the feed URL "/untitled.xml"
    Then the sources page lists a source titled with the feed URL "/untitled.xml"

  Scenario: Entry without a published date uses the fetched time
    Given the feed server serves "/nodate.xml" as an RSS 2.0 feed titled "No Dates" with 1 entries lacking a published date
    When I add the feed URL "/nodate.xml"
    Then the source "No Dates" has 1 stored items
    And the stored items of "No Dates" have a published date equal to their first-seen time

  Scenario: Re-polling does not duplicate items
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And I have added the feed URL "/news.xml"
    When the poller runs
    And the poller runs
    Then the source "Daily News" has 3 stored items

  Scenario: Entries without a guid are deduplicated by link
    Given the feed server serves "/noguid.xml" as an RSS 2.0 feed titled "No Guids" with 2 entries lacking a guid
    And I have added the feed URL "/noguid.xml"
    When the poller runs
    Then the source "No Guids" has 2 stored items

  Scenario: New entries appear on the next poll
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And I have added the feed URL "/news.xml"
    And the feed "/news.xml" now has 5 entries
    When the poller runs
    Then the source "Daily News" has 5 stored items

  Scenario: Conditional GET with 304 leaves items unchanged
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries and an ETag
    And I have added the feed URL "/news.xml"
    When the poller runs
    Then the feed server received a conditional request for "/news.xml" with the stored ETag
    And the source "Daily News" has 3 stored items
    And the source "Daily News" shows no error

  Scenario: A failing source shows its error and does not block another source
    Given the feed server serves "/good.xml" as an RSS 2.0 feed titled "Good Feed" with 2 entries
    And the feed server serves "/flaky.xml" as an RSS 2.0 feed titled "Flaky Feed" with 1 entries
    And I have added the feed URL "/good.xml"
    And I have added the feed URL "/flaky.xml"
    And the feed server now responds to "/flaky.xml" with HTTP 500
    And the feed "/good.xml" now has 4 entries
    When the poller runs
    Then the source "Flaky Feed" shows an error
    And the source "Flaky Feed" shows a last fetched time
    And the source "Flaky Feed" has 1 stored items
    And the source "Good Feed" shows no error
    And the source "Good Feed" has 4 stored items

  Scenario Outline: Different kinds of fetch failure are recorded as a source error
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    And the feed server now responds to "/news.xml" with <failure>
    When the poller runs
    Then the source "Daily News" shows an error
    And the source "Daily News" has 1 stored items

    Examples:
      | failure               |
      | HTTP 404              |
      | HTTP 503              |
      | malformed XML         |
      | an HTML page          |
      | no response in time   |

  Scenario: A later successful fetch clears the error
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    And the feed server now responds to "/news.xml" with HTTP 500
    And the poller runs
    And the source "Daily News" shows an error
    And the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 2 entries
    When the poller runs
    Then the source "Daily News" shows no error
    And the source "Daily News" has 2 stored items

  Scenario Outline: Invalid URLs are rejected on add
    When I add the raw feed URL "<url>"
    Then the sources page shows the message "<message>"
    And there are no subscriptions

    Examples:
      | url                   | message                      |
      | ftp://example.com/rss | must be an http or https URL |
      | not a url             | must be an http or https URL |
      | file:///etc/passwd    | must be an http or https URL |

  Scenario: A URL that is not a feed is rejected and not stored
    Given the feed server serves "/page.html" as an HTML page
    When I add the feed URL "/page.html"
    Then the sources page shows the message "not a valid RSS or Atom feed"
    And there are no subscriptions

  Scenario: An unreachable URL is rejected and not stored
    Given the feed server responds to "/missing.xml" with HTTP 404
    When I add the feed URL "/missing.xml"
    Then the sources page shows the message "could not be fetched"
    And there are no subscriptions

  Scenario: Adding an already-subscribed URL is a no-op with a notice
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And I have added the feed URL "/news.xml"
    When I add the feed URL "/news.xml"
    Then the sources page shows the message "already subscribed"
    And there is 1 subscription
    And the source "Daily News" has 3 stored items

  Scenario: Removing a source removes its items
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And the feed server serves "/other.xml" as an RSS 2.0 feed titled "Other" with 2 entries
    And I have added the feed URL "/news.xml"
    And I have added the feed URL "/other.xml"
    When I remove the source "Daily News"
    Then the sources page does not list a source titled "Daily News"
    And no items are stored for "Daily News"
    And the source "Other" has 2 stored items

  Scenario: The home page links to the sources page
    When I request the home page "/"
    Then the page contains "/sources"

  Scenario: Retention deletes items first seen more than 90 days ago and keeps newer ones
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And I have added the feed URL "/news.xml"
    And the stored items of "Daily News" were first seen 91, 89 and 3 days ago
    When the retention job runs
    Then the source "Daily News" has 2 stored items
    And the item first seen 91 days ago no longer exists
    And the items first seen 89 and 3 days ago still exist

# Non-goals:
#   - Timeline UI and read/unread state (#3); retention is purely age-based
#     at 90 days, so no item is ever "unread-protected" here.
#   - Resolving non-feed URLs such as YouTube channel pages (#4).
#   - Groups (#5), bulk import (#6), playback (#7), per-source poll intervals.
#   - Waiting on the real scheduler in tests; the poll use case is invoked
#     directly.
#
# Risks:
#   - New dependency: ROME (com.rometools:rome) for RSS/Atom parsing.
#   - The "no response in time" case needs a fixture handler that stalls past
#     the read timeout; make the timeout configurable (short) in tests to
#     stay fast. Fixture server ports must be ephemeral.
#   - Retention test backdates first-seen times directly in SQLite; the clock
#     should be injectable.
#   - SQLite single-writer locking when the poller and web requests overlap.
#   - Outbound fetching of user-supplied URLs (SSRF) is accepted for a
#     single-user self-hosted app; no allowlist is added.
#   - Relative feed URLs in steps ("/news.xml") resolve against the fixture
#     server's base URL.
#
# Open questions:
#   - None. Decisions are recorded under Clarifications in
#     specs/intent/feed-ingestion.md.
