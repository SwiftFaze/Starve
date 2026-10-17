Feature: The end of the list and the caught-up state
  Reaching the bottom of the timeline and having nothing left to read are two
  different moments (#95). A list that still has unread items ends with a quiet
  "End of list" marker carrying the view's unread count. "You're all caught up"
  appears only when the view has no unread items left. It says how often the
  sources are checked, and Mark all read is not offered then. The end marker
  listens for the "starve:read" event (live-unread-counts.feature) and fetches
  itself again from "/feed/end", so it turns into the caught-up state when the
  last item is read.

  Covers: the end marker of the timeline, a group, the Show read view,
  Favourites and Later (no count there); the caught-up state and its note; Mark
  all read hidden when the view has nothing unread; the "/feed/end" fragment
  and the marker's refresh listener.
  Supersedes: the "You're all caught up" end-of-feed state of
  chronological-feed.feature, which showed whenever a list had no next page.
  Out of scope: search results, which end without a marker; the empty states of
  Favourites, Later and a timeline with no sources.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A list with unread items left ends with its unread count
    Given the source "Alpha" has an unread item "One" published 1 days ago
    And the source "Alpha" has an unread item "Two" published 2 days ago
    When I request the home page "/feed"
    Then the list ends with "End of list · 2 unread"
    And the page does not contain "You're all caught up"

  Scenario: The end of a long list counts every unread item, not just the last page
    Given the source "Alpha" has 60 unread items published at one-minute intervals
    When I request the home page "/feed"
    And I load more
    Then the list ends with "End of list · 60 unread"

  Scenario: With nothing unread the timeline is caught up and says when sources are checked
    Given the source "Alpha" has a read item "Old" published 2 days ago
    When I request the home page "/feed"
    Then the page contains "You're all caught up"
    And the page contains "Sources are checked every 30 minutes"
    And the list has no end marker
    And the page does not contain "Mark all read"

  Scenario: Mark all read is offered while something is unread
    Given the source "Alpha" has an unread item "One" published 1 days ago
    When I request the home page "/feed"
    Then the page contains "Mark all read"

  Scenario: The Show read view ends with the unread count of its view
    Given the source "Alpha" has an unread item "Fresh" published 1 days ago
    And the source "Alpha" has a read item "Old" published 2 days ago
    When I request the home page "/feed?show=all"
    Then the list ends with "End of list · 1 unread"

  Scenario: A group is caught up when it has nothing unread, whatever the other groups hold
    Given the group "Music" exists
    And the source "Alpha" has a read item "Old" published 2 days ago
    And the source "Beta" has an unread item "News" published 1 days ago
    And the source "Alpha" is in the group "Music"
    When I request the timeline of the group "Music"
    Then the page contains "You're all caught up"

  Scenario: Favourites end without an unread count
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    And the item "Song" is a favourite
    When I request the home page "/feed?favourites=1"
    Then the list ends with "End of list"
    And the page does not contain "You're all caught up"

  Scenario: The end marker refreshes itself when an item is read
    Given the source "Alpha" has an unread item "One" published 1 days ago
    When I request the home page "/feed"
    Then the end marker refreshes from "/feed/end" when an item is read

  Scenario: The end fragment turns caught up once the last item is read
    Given the source "Alpha" has an unread item "One" published 1 days ago
    When I mark the item "One" read via htmx
    And I request "/feed/end"
    Then the page contains "You're all caught up"
    And the list has no end marker
