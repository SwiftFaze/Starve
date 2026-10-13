Feature: Favourite items
  An item can be marked as a favourite with a star toggle on each timeline row
  and in the reader pane. Favourites are listed by a Favourites entry in the
  left navigation, whether read or not, and are never removed by retention.

  Covers: a favourite flag on items (migration V9, nullable favourited_at,
  items already stored are not favourites); POST /items/{id}/favourite and
  POST /items/{id}/unfavourite, both idempotent, answering an htmx request
  with the re-rendered star (filled or empty) and a plain request with a
  redirect to "/"; the star on every timeline row and in the reader; a
  toggle made in the reader also updating that item's row (out-of-band swap);
  favouriting leaving read state alone; a "Favourites" entry in the left
  navigation after "All" and the groups, linking to "/?favourites=1"; the
  Favourites view listing favourited items newest first (published date, then
  id descending) whether read or not, paged 50 at a time with "Load more"
  keeping the filter, with no "Show read" or "Mark all read" control and an
  empty state; retention skipping favourited items, and deleting them again
  once unfavourited; 404 for an unknown item id.
  Supersedes: in part feed-ingestion.feature, whose retention scenario
  ("deletes items first seen more than 90 days ago") now holds only for items
  that are not favourites.
  Supersedes: in part reader-pane.feature, where the reader showed no actions
  besides Open original and Close.
  Out of scope: read-later (#32), a separate flag reusing this plumbing;
  keeping favourites when their source is removed (removal still deletes every
  item of the source, favourites included); a combined group-plus-favourites
  filter (the Favourites view ignores a group parameter); a favourites count
  in the navigation; the star in the player dock or in the detail view for
  YouTube/SoundCloud items (#30); what the browser does with the swaps (no
  reload, focus, animation): browser behaviour covered by the human playtest;
  search; bulk favouriting; export.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  # ---- the star on rows ----------------------------------------------------------------

  Scenario: Every timeline row has an empty star that favourites via htmx
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    When I request the home page "/"
    Then the timeline entry "Song" has an empty star
    And the star of "Song" favourites it via htmx without a page reload

  Scenario: Favouriting an item via htmx returns the filled star
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    When I favourite the item "Song" via htmx
    Then the response status is 200
    And the response shows a filled star that unfavourites "Song" via htmx
    And the item "Song" is a favourite

  Scenario: Clicking the filled star clears the favourite
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    And the item "Song" is a favourite
    When I unfavourite the item "Song" via htmx
    Then the response status is 200
    And the response shows an empty star that favourites "Song" via htmx
    And the item "Song" is not a favourite

  Scenario: A favourited item shows a filled star on the timeline
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    And the source "Alpha" has an unread item "Talk" published 2 days ago
    And the item "Song" is a favourite
    When I request the home page "/"
    Then the timeline entry "Song" has a filled star
    And the timeline entry "Talk" has an empty star

  Scenario: Favouriting twice leaves the item a favourite
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    When I favourite the item "Song" via htmx
    And I favourite the item "Song" via htmx
    Then the item "Song" is a favourite

  Scenario: Unfavouriting an item that is not a favourite changes nothing
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    When I unfavourite the item "Song" via htmx
    Then the response status is 200
    And the item "Song" is not a favourite

  Scenario: Favouriting without JavaScript redirects back to the timeline
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    When I favourite the item "Song"
    Then the response redirects to "/"
    And the item "Song" is a favourite

  Scenario: Favouriting does not change read state
    Given the source "Alpha" has an unread item "Fresh" published 1 days ago
    And the source "Alpha" has a read item "Old" published 2 days ago
    When I favourite the item "Fresh" via htmx
    And I favourite the item "Old" via htmx
    Then the item "Fresh" is unread
    And the item "Old" is read

  Scenario: A read favourite stays out of the default timeline but keeps its star with Show read
    Given the source "Alpha" has a read item "Old" published 2 days ago
    And the item "Old" is a favourite
    When I request the home page "/"
    Then the timeline does not list "Old"
    When I request the home page "/?show=all"
    Then the timeline entry "Old" has a filled star

  Scenario Outline: Favouriting or unfavouriting an unknown item returns 404
    When I <action> the unknown item <id> via htmx
    Then the response status is 404

    Examples:
      | action      | id   |
      | favourite   | 9999 |
      | unfavourite | 9999 |
      | favourite   | abc  |
      | unfavourite | abc  |

  # ---- the star in the reader ----------------------------------------------------------

  Scenario: The reader has an empty star for an item that is not a favourite
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader has an empty star
    And the reader's star favourites "Post" via htmx

  Scenario: The reader has a filled star for a favourite
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    And the item "Post" is a favourite
    When I request the reader of the item "Post" via htmx
    Then the reader has a filled star
    And the reader's star unfavourites "Post" via htmx

  Scenario: A post without a body still has its star in the reader
    Given the source "Blog" has an item "Teaser" with the link "https://blog.example/teaser" and no stored body published 1 days ago
    When I request the reader of the item "Teaser" via htmx
    Then the reader has an empty star

  Scenario: Favouriting from the reader also refreshes the star on the timeline row
    Given the source "Blog" has an unread item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I favourite the item "Post" via htmx from the reader
    Then the response shows a filled star that unfavourites "Post" via htmx
    And the response also updates the star of the timeline entry "Post" out of band

  Scenario: Favouriting from the reader leaves a video in the pane alone
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I favourite the item "Post" via htmx from the reader
    Then the response does not ask a video in the pane to step down

  # ---- the Favourites filter -----------------------------------------------------------

  Scenario: The left navigation offers Favourites after All and the groups
    Given the group "Music" exists
    When I request the home page "/"
    Then the navigation ends with the "Favourites" entry linking to "/?favourites=1"
    And the "Favourites" entry follows the group "Music"

  Scenario: The Favourites view lists favourites newest first, read or not
    Given the source "Alpha" has an unread item "Middle" published 3 days ago
    And the source "Alpha" has a read item "Newest" published 1 days ago
    And the source "Alpha" has an unread item "Oldest" published 5 days ago
    And the source "Alpha" has an unread item "Plain" published 2 days ago
    And the item "Middle" is a favourite
    And the item "Newest" is a favourite
    And the item "Oldest" is a favourite
    When I request the home page "/?favourites=1"
    Then the timeline lists the items in this order
      | Newest |
      | Middle |
      | Oldest |
    And the timeline does not list "Plain"

  Scenario: Favourites with the same published date are ordered by id descending
    Given the source "Alpha" has unread items "First", "Second" and "Third" stored in that order with the same published date
    And the item "First" is a favourite
    And the item "Second" is a favourite
    And the item "Third" is a favourite
    When I request the home page "/?favourites=1"
    Then the timeline lists the items in this order
      | Third  |
      | Second |
      | First  |

  Scenario: The Favourites entry is marked as current in the Favourites view
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    And the item "Song" is a favourite
    When I request the home page "/?favourites=1"
    Then the "Favourites" entry is marked as current
    And no group entry is marked as current

  Scenario: Favourites from every source and group are listed together
    Given the group "Music" exists
    And the source "Alpha" is in the group "Music"
    And the source "Alpha" has an unread item "Song" published 2 days ago
    And the source "Beta" has an unread item "Talk" published 1 days ago
    And the item "Song" is a favourite
    And the item "Talk" is a favourite
    When I request the home page "/?favourites=1"
    Then the timeline lists the items in this order
      | Talk |
      | Song |

  Scenario: The Favourites view ignores a group parameter
    Given the group "Music" exists
    And the source "Alpha" is in the group "Music"
    And the source "Alpha" has an unread item "Song" published 2 days ago
    And the source "Beta" has an unread item "Talk" published 1 days ago
    And the item "Song" is a favourite
    And the item "Talk" is a favourite
    When I request the timeline of the group "Music" with the Favourites filter
    Then the timeline lists "Song"
    And the timeline lists "Talk"

  Scenario: Favouriting an item makes it appear in the Favourites view
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    When I request the home page "/?favourites=1"
    Then the timeline does not list "Song"
    When I favourite the item "Song" via htmx
    And I request the home page "/?favourites=1"
    Then the timeline lists "Song"

  Scenario: Unfavouriting an item removes it from the Favourites view
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    And the item "Song" is a favourite
    When I unfavourite the item "Song" via htmx
    And I request the home page "/?favourites=1"
    Then the timeline lists no items

  Scenario: The Favourites view has a star on every row and no read controls
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    And the item "Song" is a favourite
    When I request the home page "/?favourites=1"
    Then the timeline entry "Song" has a filled star
    And the page has no "Mark all read" control
    And the page has no "Show read" control

  Scenario: The Favourites view ignores the show parameter
    Given the source "Alpha" has a read item "Old" published 2 days ago
    And the item "Old" is a favourite
    When I request the home page "/?favourites=1&show=all"
    Then the timeline lists "Old"

  Scenario: Favourites with nothing favourited shows an empty state
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    When I request the home page "/?favourites=1"
    Then the timeline lists no items
    And the page contains "No favourites yet"
    And the page does not contain "You're all caught up"

  Scenario: Long Favourites lists are paged 50 at a time and Load more keeps the filter
    Given the source "Alpha" has 120 unread items published at one-minute intervals
    And every item is a favourite
    And the source "Alpha" has an unread item "Plain" published 60 days ago
    When I request the home page "/?favourites=1"
    Then the timeline lists 50 items
    When I load more
    And I load more
    Then the timeline lists every item exactly once, newest first
    And the timeline lists 120 items
    And the timeline does not list "Plain"

  # ---- retention -----------------------------------------------------------------------

  Scenario: Retention keeps a favourite older than the retention window
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And I have added the feed URL "/news.xml"
    And the stored items of "Daily News" were first seen 91, 89 and 3 days ago
    And the item first seen 91 days ago is a favourite
    When the retention job runs
    Then the source "Daily News" has 3 stored items
    And the items first seen 91, 89 and 3 days ago still exist

  Scenario: Retention still deletes old items that are not favourites
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And I have added the feed URL "/news.xml"
    And the stored items of "Daily News" were first seen 200, 100 and 3 days ago
    And the item first seen 200 days ago is a favourite
    When the retention job runs
    Then the source "Daily News" has 2 stored items
    And the item first seen 100 days ago no longer exists
    And the items first seen 200 and 3 days ago still exist

  Scenario: Unfavouriting makes an old item eligible for deletion again
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And I have added the feed URL "/news.xml"
    And the stored items of "Daily News" were first seen 91, 89 and 3 days ago
    And the item first seen 91 days ago is a favourite
    When the retention job runs
    And I unfavourite the item first seen 91 days ago
    And the retention job runs
    Then the source "Daily News" has 2 stored items
    And the item first seen 91 days ago no longer exists

  Scenario: A read favourite older than the retention window survives retention
    Given the source "Alpha" has a read item "Old gem" published 400 days ago
    And the item "Old gem" was first seen 400 days ago
    And the item "Old gem" is a favourite
    When the retention job runs
    Then the item "Old gem" still exists

  Scenario: Removing a source still deletes its favourites
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    And the item "Song" is a favourite
    When I remove the source "Alpha"
    Then the item "Song" no longer exists

  # ---- upgrade -------------------------------------------------------------------------

  Scenario: Items stored before the upgrade are not favourites
    Given the database is migrated only up to before favourites
    And it holds a source "Blog" with a stored item titled "Old post"
    And the application is running
    When I request the home page "/?favourites=1"
    Then the timeline lists no items
    When I favourite the item "Old post" via htmx
    Then the item "Old post" is a favourite

# Non-goals:
#   - Read-later (#32); it will add its own flag and view on top of this
#     plumbing, and gets its own feature file.
#   - Keeping favourites when their source is removed.
#   - Combining the Favourites view with a group filter.
#   - A count on the Favourites navigation entry.
#   - Ordering favourites by when they were favourited.
#
# Risks:
#   - Data loss: retention is `DELETE FROM items WHERE first_seen_at < ?`; the
#     scenarios above pin that favourites are skipped. Any future cleanup that
#     deletes items must honour the same flag.
#   - The group navigation scenarios in source-groups.feature count every
#     navigation link; the Favourites entry must not break them (it is
#     asserted separately in this file).
#   - Migration V9 is numbered after the in-flight V7 (#28) and V8 (#30);
#     renumber if those land differently.
#   - A refetch of a feed must not clear the flag (insertNew dedups on
#     existing rows); not pinned by a scenario of its own.
#
# Open questions:
#   - None outstanding; see the intent doc's Clarifications for auto-decisions.
