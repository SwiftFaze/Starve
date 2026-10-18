Feature: Source health and pruning stats
  Each row on Sources says how alive the source is and how much of it gets read,
  so that pruning the roster is not guesswork (#142).

  Covers: a stats line on every source row: when its newest kept item was
  published ("Last post today", "yesterday", "N days ago", "N weeks ago", "N
  months ago", "N years ago", or "No recent posts" when none is kept), posts
  per week over the last 28 days (one decimal under 10, whole from 10), its
  unread backlog, and its skip rate: the share of items read since open
  tracking began that were never opened. The skip rate shows only from 20 such
  reads; below that the row says how many it has so far, and with none it
  leaves the skip rate out. A source whose newest
  post is over 60 days old (or that has none kept, once fetched) is marked
  Quiet; one skipped 80% or more over at least 20 reads is marked Mostly
  skipped. Opening an item through its title link, the reader, the detail view
  or the player records the open; marking it read (one item, or Mark all read)
  does not. Items read before open tracking existed never count.
  Out of scope: fetch scheduling and failure state (per-source-interval and
  broken-sources features); any ranking of the timeline; the row's layout at
  each breakpoint (checked by eye).

  Background:
    Given a fresh SQLite database file
    And the application is running

  Scenario: A row shows its last post, posts per week and unread backlog
    Given the source "Daily News" has 8 unread items published 3 days ago
    And the source "Daily News" has 2 unread items published 40 days ago
    When I request "/sources"
    Then the stats of the source "Daily News" read "Last post 3 days ago · 2/week · 10 unread"
    And the source "Daily News" is not marked

  Scenario: A slow source shows a fractional rate
    Given the source "Monthly" has 1 unread item published 20 days ago
    When I request "/sources"
    Then the stats of the source "Monthly" read "Last post 2 weeks ago · 0.3/week · 1 unread"

  Scenario: A source with no post in 60 days is marked quiet
    Given the source "Dormant" has 3 unread items published 100 days ago
    When I request "/sources"
    Then the stats of the source "Dormant" read "Last post 3 months ago · 0/week · 3 unread"
    And the source "Dormant" is marked "Quiet"

  Scenario: A fetched source with nothing kept is marked quiet
    Given the source "Empty" was last fetched at "2026-10-12T14:30:45Z"
    When I request "/sources"
    Then the stats of the source "Empty" read "No recent posts · 0/week · 0 unread"
    And the source "Empty" is marked "Quiet"

  Scenario: The skip rate waits for enough reads and says so
    Given the source "Busy" has 5 items read without being opened
    When I request "/sources"
    Then the stats of the source "Busy" read "Last post today · 1.3/week · 0 unread · skip rate after 20 reads (5 so far)"
    And the source "Busy" is not marked

  Scenario: A mostly skipped source shows its skip rate and is marked
    Given the source "Busy" has 19 items read without being opened
    And the source "Busy" has 1 item read after being opened
    When I request "/sources"
    Then the stats of the source "Busy" read "Last post today · 5/week · 0 unread · skipped 95%"
    And the source "Busy" is marked "Mostly skipped"

  Scenario: A source mostly opened shows its skip rate unmarked
    Given the source "Loved" has 5 items read without being opened
    And the source "Loved" has 15 items read after being opened
    When I request "/sources"
    Then the stats of the source "Loved" read "Last post today · 5/week · 0 unread · skipped 25%"
    And the source "Loved" is not marked

  Scenario: Opening an item records the open, marking it read does not
    Given the source "Blog" has an unread item "Opened" with the link "https://blog.test/opened" published 1 days ago
    And the source "Blog" has an unread item "Skipped" with the link "https://blog.test/skipped" published 1 days ago
    And the source "Blog" has an unread item "Swept" with the link "https://blog.test/swept" published 1 days ago
    When I open the item "Opened"
    And I mark the item "Skipped" as read
    And I mark all items read
    Then the item "Opened" is recorded as opened
    And the item "Skipped" is read but not recorded as opened
    And the item "Swept" is read but not recorded as opened

  Scenario: Reading a post in the reader records the open
    Given the source "Blog" has an unread item "Essay" with the link "https://blog.test/essay" and the stored body "<p>Words</p>" published 1 days ago
    When I request the reader of the item "Essay" via htmx
    Then the item "Essay" is recorded as opened

  Scenario: Opening a video's detail view or player records the open
    Given the source "Channel" has a YouTube item "Clip" published 1 days ago
    And the source "Channel" has a YouTube item "Show" published 1 days ago
    When I request the detail view of the item "Clip" via htmx
    And I request the player of the item "Show" via htmx
    Then the item "Clip" is recorded as opened
    And the item "Show" is recorded as opened

  Scenario: Items read before open tracking existed never count as skipped
    Given the database is migrated only up to before open tracking
    And it holds a source "Archive" with 25 items read before the upgrade
    And the application is running
    When I request "/sources"
    Then the stats of the source "Archive" read "Last post today · 6.3/week · 0 unread"
    And the source "Archive" is not marked
