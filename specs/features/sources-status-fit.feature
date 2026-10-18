Feature: Sources roster copy that fits
  The Sources page shows each source's health and the roster's size in words
  that fit their space (#104).

  Covers: a source's fetch time written short enough to show in full beside
  its controls: "17:13 UTC" when it was fetched today (UTC), "yesterday 09:02
  UTC" the day before, and "2026-10-12 14:30 UTC" before that, after "Fetched"
  (or "Last tried" when the source is failing), always as a <time> whose
  datetime is the exact instant and whose title is the full timestamp with
  seconds; the obi meta saying "No groups" when there are none ("In 1 group",
  "In 3 groups" otherwise); the add-source field's short placeholder "Paste a
  feed, channel or site URL", with no hint under the field, so the Add a source
  and New group fields start level and the two lists line up row for row.
  Supersedes: the "yyyy-MM-dd HH:mm:ss UTC" fetch time of feed-ingestion.feature
  (it still asserts only that a fetch time is shown).
  Out of scope: the two-column Sources layout; time zones other than UTC; the
  row grid's breakpoints (checked by eye, not by these scenarios).

  Background:
    Given a fresh SQLite database file
    And the application is running

  Scenario: An older fetch time shows its date and minutes, and keeps the full timestamp
    Given the source "Daily News" was last fetched at "2026-10-12T14:30:45Z"
    When I request "/sources"
    Then the source "Daily News" says "Fetched 2026-10-12 14:30 UTC"
    And the fetch time of "Daily News" is a time element for "2026-10-12T14:30:45Z" titled "2026-10-12 14:30:45 UTC"

  Scenario: The obi meta says No groups when there are none
    Given the source "Daily News" was last fetched at "2026-10-12T14:30:45Z"
    When I request "/sources"
    Then the Sources meta reads "1 source No groups"

  Scenario: The obi meta counts the groups when there are some
    Given the group "Music" exists
    When I request "/sources"
    Then the Sources meta reads "0 sources In 1 group"

  Scenario: The add-source field has a short placeholder and no hint under it
    When I request "/sources"
    Then the add-source field's placeholder is "Paste a feed, channel or site URL"
    And the add-source field has no hint
