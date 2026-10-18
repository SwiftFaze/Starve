Feature: Broken sources and auto-healing moved feeds
  A failing source only showed its latest error, so a feed dead for weeks looked
  like one that hiccupped once, and nothing on the timeline said part of the
  feed was missing. Feeds that moved were followed silently on every poll and
  the stored URL never changed, so the source broke once the old address went
  away (#144).

  Covers: the start of a source's current failure streak, kept until its next
  successful fetch (a 304 counts as one); a source is broken once that streak is
  24 hours old, whatever its poll interval; a broken row on Sources reads
  "Broken", "failing since <time>" and its last error, where a failing one reads
  "Failed"; one quiet line on the timeline ("1 source is broken", "2 sources are
  broken") linking to Sources, counting only the signed-in user's sources and
  shown on New releases and All releases, not on Favourites, Later or search
  results; feed polls following redirects themselves, at most 5 hops, a loop or
  a longer chain being a fetch failure; the leading run of 301 and 308 hops
  rewriting the stored URL (items and read state stay with the source), while
  302, 303 and 307 hops are followed without saving; a permanent move to a URL
  the user already follows is not adopted, and the row says so until the
  redirect goes away.
  Out of scope: notifications outside Starve; unsubscribing broken sources
  automatically; redirects met by Smart-add's page resolution or by article
  fetches.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A source failing for less than a day is failed, not broken
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    And the feed server now responds to "/news.xml" with HTTP 500
    When the poller runs
    And 23 hours pass and the poller runs
    Then the source "Daily News" is failed, not broken
    And the timeline has no broken-sources notice

  Scenario: A source failing for a day is broken, counted from its first failure
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    And the feed server now responds to "/news.xml" with HTTP 404
    When the poller runs
    And 12 hours pass and the poller runs
    And 12 hours pass and the poller runs
    Then the source "Daily News" is broken, failing since 24 hours ago
    And the source "Daily News" shows the error "Could not be fetched: HTTP 404"

  Scenario: The timeline counts the broken sources of the signed-in user only
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" with 1 entries
    And the feed server serves "/fine.xml" as an RSS 2.0 feed titled "Fine" with 1 entries
    And I have added the feed URL "/news.xml"
    And I have added the feed URL "/fine.xml"
    And the user "amy" exists
    And "amy" has added the feed URL "/blog.xml"
    And the feed server now responds to "/news.xml" with HTTP 500
    And the feed server now responds to "/blog.xml" with HTTP 500
    When the poller runs
    And 24 hours pass and the poller runs
    Then the timeline notice reads "1 source is broken" and links to "/sources"
    When the feed server now responds to "/fine.xml" with HTTP 500
    And the poller runs
    And 24 hours pass and the poller runs
    Then the timeline notice reads "2 sources are broken" and links to "/sources"
    And the Favourites view has no broken-sources notice

  Scenario: A broken source recovers on its next success, and a new failure starts a new streak
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    And the feed server now responds to "/news.xml" with HTTP 500
    And the poller runs
    And 24 hours pass and the poller runs
    When the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 2 entries
    And the poller runs
    Then the source "Daily News" shows no error
    And the timeline has no broken-sources notice
    When the feed server now responds to "/news.xml" with HTTP 500
    And 1 hours pass and the poller runs
    Then the source "Daily News" is failed, not broken

  Scenario Outline: A permanent redirect to a feed rewrites the stored URL and keeps the items
    Given the feed server serves "/old.xml" as an RSS 2.0 feed titled "Daily News" with 2 entries
    And I have added the feed URL "/old.xml"
    And the feed server serves "/new.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And the feed server now redirects "/old.xml" to "/new.xml" with HTTP <status>
    When the poller runs
    Then the source "Daily News" has the feed URL "/new.xml"
    And the source "Daily News" has 5 stored items
    And the source "Daily News" shows no error
    When the feed server forgets the requests so far
    And the poller runs
    Then the feed server received 0 requests for "/old.xml"
    And the feed server received 1 requests for "/new.xml"

    Examples:
      | status |
      | 301    |
      | 308    |

  Scenario Outline: A temporary redirect is followed without saving the new URL
    Given the feed server serves "/old.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/old.xml"
    And the feed server serves "/new.xml" as an RSS 2.0 feed titled "Daily News" with 2 entries
    And the feed server now redirects "/old.xml" to "/new.xml" with HTTP <status>
    When the poller runs
    Then the source "Daily News" has the feed URL "/old.xml"
    And the source "Daily News" has 3 stored items
    When the feed server forgets the requests so far
    And the poller runs
    Then the feed server received 1 requests for "/old.xml"

    Examples:
      | status |
      | 302    |
      | 303    |
      | 307    |

  Scenario: A permanent move to a feed the user already follows is not adopted, and the row says why
    Given the feed server serves "/old.xml" as an RSS 2.0 feed titled "Old News" with 1 entries
    And the feed server serves "/new.xml" as an RSS 2.0 feed without a title with 1 entries
    And I have added the feed URL "/old.xml"
    And I have added the feed URL "/new.xml"
    And the feed server now redirects "/old.xml" to "/new.xml" with HTTP 301
    When the poller runs
    Then the source "Old News" has the feed URL "/old.xml"
    And the source "Old News" says it moved to "/new.xml", already one of my sources
    And there are 2 subscriptions
    When the feed server serves "/old.xml" as an RSS 2.0 feed titled "Old News" with 1 entries
    And the poller runs
    Then the source "Old News" does not say it moved

  Scenario: A redirect loop is a fetch failure
    Given the feed server serves "/a.xml" as an RSS 2.0 feed titled "Looping" with 1 entries
    And I have added the feed URL "/a.xml"
    And the feed server now redirects "/a.xml" to "/b.xml" with HTTP 301
    And the feed server now redirects "/b.xml" to "/a.xml" with HTTP 301
    When the poller runs
    Then the source "Looping" shows the error "Could not be fetched: redirect loop"
    And the source "Looping" has the feed URL "/a.xml"

  Scenario: A chain of more than 5 redirects is a fetch failure
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Far Away" with 1 entries
    And I have added the feed URL "/news.xml"
    And the feed server now redirects "/news.xml" through 6 hops of HTTP 302
    When the poller runs
    Then the source "Far Away" shows the error "Could not be fetched: more than 5 redirects"
