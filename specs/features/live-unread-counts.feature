Feature: Live unread counts
  The obi's "n unread" and the group navigation's "[n]" counts follow the
  owner's reading without a page load (#93). Every htmx request that marks an
  item read answers with the HX-Trigger event "starve:read". The timeline
  carries a listener for that event, which fetches "/feed/counts" for the view
  on screen and swaps the counts in out of band.

  Covers: the "starve:read" trigger on POST /items/{id}/read, and on GET
  /items/{id}/reader, /items/{id}/detail and /items/{id}/player; the counts
  listener on the timeline, which keeps the view's group, Favourites or Later
  selection and its read state; the "/feed/counts" fragment, holding the obi's
  unread count when the view has one, and the group navigation with its counts
  and current marker.
  Out of scope: counts on pages other than the timeline; Mark all read, which
  already swaps the bar and the obi (chronological-feed.feature).

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: Marking an item read via htmx asks the page to refresh its counts
    Given the source "Alpha" has an unread item "Done" published 1 days ago
    When I mark the item "Done" read via htmx
    Then the response asks the page to refresh its unread counts

  Scenario: Opening a post in the reader asks the page to refresh its counts
    Given the source "Alpha" has an unread item "Story" with the link "https://example.com/story" published 1 days ago
    When I request the reader of the item "Story" via htmx
    Then the response asks the page to refresh its unread counts

  Scenario: Playing an item asks the page to refresh its counts
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response asks the page to refresh its unread counts

  Scenario: Opening a detail view asks the page to refresh its counts
    Given the source "Cloud" has an item "Track" with the link "https://soundcloud.com/artist/track" published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with the author "Artist" and no description
    When I request the detail view of the item "Track" via htmx
    Then the response asks the page to refresh its unread counts

  Scenario: The timeline refreshes the counts of the view on screen when an item is read
    Given the group "Music" exists
    And the source "Alpha" has an unread item "One" published 1 days ago
    And the source "Alpha" is in the group "Music"
    When I request the timeline of the group "Music" showing read items
    Then the page refreshes its counts from "/feed/counts?show=all&group={Music}" when an item is read

  Scenario: The counts follow an item marked read
    Given the group "Music" exists
    And the source "Alpha" has an unread item "One" published 1 days ago
    And the source "Alpha" has an unread item "Two" published 2 days ago
    And the source "Beta" has an unread item "Three" published 3 days ago
    And the source "Alpha" is in the group "Music"
    When I mark the item "One" read via htmx
    And I request the counts of the group "Music"
    Then the counts show "1 unread" on the obi
    And the group navigation lists the entries
      | All [2]   |
      | Music [1] |
    And the group navigation marks "Music [1]" as current

  Scenario: The Favourites view's counts have no obi count
    Given the source "Alpha" has an unread item "One" published 1 days ago
    When I request the counts of the Favourites view
    Then the counts show no count on the obi
    And the group navigation lists the entries
      | All [1] |
