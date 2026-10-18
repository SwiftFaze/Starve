Feature: Per-source poll interval and Fetch now
  Every source used to be polled on one global timer, so a daily news site and a
  monthly blog cost the same, and a fixed or new source could not be fetched
  before the next poll (#143).

  Covers: an interval selector on every Sources row offering "Default (every 30
  minutes)" (the configured starve.poll.interval) plus 15 minutes, 30 minutes,
  1 hour, 6 hours and Daily, saved at once (an empty choice goes back to the
  default, an unknown one is a 400, another user's source a 404); a scheduler
  tick (starve.poll.tick, every minute) that fetches only the sources whose
  interval has passed since their last attempt, success or failure; a Fetch now
  button that returns at once, shows the row as "Fetching now…" with the button
  disabled, refreshes that row every 2 seconds until the background fetch is
  over, and never queues a second fetch of a source already queued or running.
  Background scheduling stays switched off in these scenarios: "the scheduler
  ticks" runs one tick at the scenario's clock.
  Supersedes: the single global poll run; feed-ingestion.feature's "the poller
  runs" is now a tick in which every source on the default interval is due.
  Out of scope: adaptive intervals; a "fetch everything now" button; failure
  and broken state (broken-sources feature).

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: Every row offers the intervals, on the default until one is chosen
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    When I request "/sources"
    Then the interval choices of "Daily News" are "Default (every 30 minutes), 15 minutes, 30 minutes, 1 hour, 6 hours, Daily"
    And the interval of "Daily News" reads "Default (every 30 minutes)"

  Scenario: Choosing an interval keeps it, and the default can be chosen again
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    When I set the interval of "Daily News" to "DAILY"
    Then the interval of "Daily News" reads "Daily"
    When I set the interval of "Daily News" to ""
    Then the interval of "Daily News" reads "Default (every 30 minutes)"

  Scenario: An unknown interval is refused and another user's source is not found
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    And the user "amy" exists
    When I set the interval of "Daily News" to "HOURLY-ISH"
    Then the response status is 400
    When I sign in as "amy"
    And I set the interval of "Daily News" to "DAILY"
    Then the response status is 404

  Scenario: Each tick fetches only the sources whose interval has passed
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And the feed server serves "/quick.xml" as an RSS 2.0 feed titled "Quick" with 1 entries
    And the feed server serves "/slow.xml" as an RSS 2.0 feed titled "Slow" with 1 entries
    And I have added the feed URL "/news.xml"
    And I have added the feed URL "/quick.xml"
    And I have added the feed URL "/slow.xml"
    And I have set the interval of "Quick" to "FIFTEEN_MINUTES"
    And I have set the interval of "Slow" to "DAILY"
    And the feed server forgets the requests so far
    When 16 minutes pass and the scheduler ticks
    Then the feed server received 1 requests for "/quick.xml"
    And the feed server received 0 requests for "/news.xml"
    When 15 minutes pass and the scheduler ticks
    Then the feed server received 1 requests for "/news.xml"
    And the feed server received 2 requests for "/quick.xml"
    And the feed server received 0 requests for "/slow.xml"
    When 24 hours pass and the scheduler ticks
    Then the feed server received 1 requests for "/slow.xml"

  Scenario: A failed attempt waits for the interval like a successful one
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    And the feed server responds to "/news.xml" with HTTP 500
    And the feed server forgets the requests so far
    When 31 minutes pass and the scheduler ticks
    Then the feed server received 1 requests for "/news.xml"
    When 10 minutes pass and the scheduler ticks
    Then the feed server received 1 requests for "/news.xml"

  Scenario: Fetch now returns at once and the row shows the fetch running
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    And the feed server forgets the requests so far
    When I press Fetch now on "Daily News" via htmx
    Then the response is the row of "Daily News" saying "Fetching now…"
    And the row refreshes itself from "/sources/<id of Daily News>/row" every 2 seconds
    And the Fetch now button of "Daily News" is disabled
    And the feed server received 0 requests for "/news.xml"

  Scenario: Pressing Fetch now again while it runs queues nothing more
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    And the feed server forgets the requests so far
    When I press Fetch now on "Daily News" via htmx
    And I press Fetch now on "Daily News" via htmx
    And the background fetches complete
    Then the feed server received 1 requests for "/news.xml"

  Scenario: Once the fetch is over the row shows its outcome and stops refreshing
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    And the feed server responds to "/news.xml" with HTTP 404
    When I press Fetch now on "Daily News" via htmx
    And the background fetches complete
    And I request the row of "Daily News"
    Then the row of "Daily News" shows the error "Could not be fetched: HTTP 404"
    And the row of "Daily News" does not refresh itself
    And the Fetch now button of "Daily News" is enabled

  Scenario: Fetch now works without JavaScript and only on one's own sources
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    And the user "amy" exists
    When I press Fetch now on "Daily News"
    Then the response redirects to "/sources"
    When I sign in as "amy"
    And I press Fetch now on "Daily News"
    Then the response status is 404
