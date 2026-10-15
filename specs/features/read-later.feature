Feature: Save items for later
  An item can be saved to read or watch later with a ⏲ toggle on each timeline
  row, in the reader pane and in the detail view. Saved items are listed by a
  Later entry in the left navigation, earliest-saved first, whether read or
  not. Nothing clears them automatically, and retention never removes them,
  and they stay searchable. Searching from the Later view searches everything;
  clearing the search returns to Later.

  Covers: a read-later flag on items, separate from favourites (migration V12,
  nullable later_at holding when the item was saved; items already stored are
  not saved); POST /items/{id}/later and POST /items/{id}/unlater, both
  idempotent, answering an htmx request with the re-rendered ⏲ (saved or
  unsaved) and a plain request with a redirect to "/feed"; saving an item that is
  already saved keeping its original saved time; the ⏲ on every timeline row,
  in the reader and in the detail view; a toggle made in the reader or the
  detail view also updating that item's row (out-of-band swap); saving
  leaving read state and the favourite flag alone, and opening, reading or
  playing an item leaving it saved; a "Later" entry in the left navigation
  after "Favourites", linking to "/feed?later=1"; the Later view listing saved
  items earliest-saved first (saved time, then id ascending) whether read or
  not, paged 50 at a time with "Load more" keeping the filter, with no "Show
  read" or "Mark all read" control and an empty state; Favourites winning when
  both filters are given; retention skipping saved items, and deleting them
  again once cleared; 404 for an unknown item id; Later as an origin view of
  full-text search (full-text-search.feature): the search form carrying
  "later" as "1", clearing a search in Later returning to Later, and the
  search index (like the items) keeping saved items through retention.
  Supersedes: in part feed-ingestion.feature, whose retention scenario
  ("deletes items first seen more than 90 days ago") now holds only for items
  that are neither favourites nor saved for later.
  Supersedes: in part reader-pane.feature and media-detail-view.feature, where
  the reader and the detail view showed no actions besides Open original,
  Close (and Play in the detail view).
  Out of scope: favourites themselves (favourites.feature); automatic
  clearing, so opening or playing an item never removes it from Later;
  keeping saved items when their source is removed (removal still deletes
  every item of the source); a combined group-plus-Later or
  Favourites-plus-Later filter; a count on the Later entry; reordering the
  queue by hand; the ⏲ in the player dock; what the browser does with the swaps
  (no reload, focus, animation): browser behaviour covered by the human
  playtest; narrowing search results to saved items (results ignore the view
  by design); bulk saving; export.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  # ---- the ⏲ on rows -------------------------------------------------------------------

  Scenario: Every timeline row has an unsaved ⏲ that saves via htmx
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    When I request the home page "/feed"
    Then the timeline entry "Long talk" has an unsaved later toggle
    And the later toggle of "Long talk" saves it via htmx without a page reload

  Scenario: Saving an item via htmx returns the saved ⏲
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    When I save the item "Long talk" for later via htmx
    Then the response status is 200
    And the response shows a saved later toggle that clears "Long talk" via htmx
    And the item "Long talk" is saved for later

  Scenario: Clicking the saved ⏲ clears it
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    And the item "Long talk" is saved for later
    When I clear the item "Long talk" from later via htmx
    Then the response status is 200
    And the response shows an unsaved later toggle that saves "Long talk" via htmx
    And the item "Long talk" is not saved for later

  Scenario: A saved item shows a saved ⏲ on the timeline
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    And the source "Alpha" has an unread item "Short" published 2 days ago
    And the item "Long talk" is saved for later
    When I request the home page "/feed"
    Then the timeline entry "Long talk" has a saved later toggle
    And the timeline entry "Short" has an unsaved later toggle

  Scenario: Saving twice leaves the item saved
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    When I save the item "Long talk" for later via htmx
    And I save the item "Long talk" for later via htmx
    Then the item "Long talk" is saved for later

  Scenario: Saving twice keeps the original position
    Given the source "Alpha" has an unread item "First" published 3 days ago
    And the source "Alpha" has an unread item "Second" published 2 days ago
    When I save the item "First" for later via htmx
    And I save the item "Second" for later via htmx
    And I save the item "First" for later via htmx
    And I request the home page "/feed?later=1"
    Then the timeline lists the items in this order
      | First  |
      | Second |

  Scenario: Clearing an item that is not saved changes nothing
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    When I clear the item "Long talk" from later via htmx
    Then the response status is 200
    And the item "Long talk" is not saved for later

  Scenario: Saving without JavaScript redirects back to the timeline
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    When I save the item "Long talk" for later
    Then the response redirects to "/feed"
    And the item "Long talk" is saved for later

  Scenario: Saving does not change read state
    Given the source "Alpha" has an unread item "Fresh" published 1 days ago
    And the source "Alpha" has a read item "Old" published 2 days ago
    When I save the item "Fresh" for later via htmx
    And I save the item "Old" for later via htmx
    Then the item "Fresh" is unread
    And the item "Old" is read

  Scenario: Later and favourite are independent flags
    Given the source "Alpha" has an unread item "Both" published 1 days ago
    And the source "Alpha" has an unread item "Star only" published 2 days ago
    And the item "Star only" is a favourite
    When I save the item "Both" for later via htmx
    And I favourite the item "Both" via htmx
    And I save the item "Star only" for later via htmx
    And I clear the item "Both" from later via htmx
    Then the item "Both" is a favourite
    And the item "Both" is not saved for later
    And the item "Star only" is a favourite
    And the item "Star only" is saved for later

  Scenario: A saved read item stays out of the default timeline but keeps its ⏲ with Show read
    Given the source "Alpha" has a read item "Old" published 2 days ago
    And the item "Old" is saved for later
    When I request the home page "/feed"
    Then the timeline does not list "Old"
    When I request the home page "/feed?show=all"
    Then the timeline entry "Old" has a saved later toggle

  Scenario Outline: Saving or clearing an unknown item returns 404
    When I <action> the unknown item <id> via htmx
    Then the response status is 404

    Examples:
      | action | id   |
      | save   | 9999 |
      | clear  | 9999 |
      | save   | abc  |
      | clear  | abc  |

  # ---- the ⏲ in the reader and the detail view ---------------------------------------

  Scenario: The reader has an unsaved ⏲ for an item that is not saved
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader has an unsaved later toggle
    And the reader's later toggle saves "Post" via htmx

  Scenario: The reader has a saved ⏲ for a saved item
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    And the item "Post" is saved for later
    When I request the reader of the item "Post" via htmx
    Then the reader has a saved later toggle
    And the reader's later toggle clears "Post" via htmx

  Scenario: A post without a body still has its ⏲ in the reader
    Given the source "Blog" has an item "Teaser" with the link "https://blog.example/teaser" and no stored body published 1 days ago
    When I request the reader of the item "Teaser" via htmx
    Then the reader has an unsaved later toggle

  Scenario: Saving from the reader also refreshes the ⏲ on the timeline row
    Given the source "Blog" has an unread item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I save the item "Post" for later via htmx from the reader
    Then the response shows a saved later toggle that clears "Post" via htmx
    And the response also updates the later toggle of the timeline entry "Post" out of band

  Scenario: Saving from the reader leaves a video in the pane alone
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I save the item "Post" for later via htmx from the reader
    Then the response does not ask a video in the pane to step down

  Scenario: The detail view has an unsaved ⏲ for a playable item that is not saved
    Given the source "Tube" has a YouTube item "Clip" published 1 days ago
    When I request the detail view of the item "Clip" via htmx
    Then the detail view has an unsaved later toggle
    And the detail view's later toggle saves "Clip" via htmx

  Scenario: The detail view has a saved ⏲ for a saved item
    Given the source "Tube" has a YouTube item "Clip" published 1 days ago
    And the item "Clip" is saved for later
    When I request the detail view of the item "Clip" via htmx
    Then the detail view has a saved later toggle
    And the detail view's later toggle clears "Clip" via htmx

  Scenario: Saving from the detail view also refreshes the ⏲ on the timeline row
    Given the source "Tube" has a YouTube item "Clip" published 1 days ago
    When I save the item "Clip" for later via htmx from the detail view
    Then the response shows a saved later toggle that clears "Clip" via htmx
    And the response also updates the later toggle of the timeline entry "Clip" out of band
    And the response does not ask a video in the pane to step down

  # ---- opening never clears -----------------------------------------------------------

  Scenario: Opening a saved item in the reader leaves it saved
    Given the source "Blog" has an unread item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    And the item "Post" is saved for later
    When I request the reader of the item "Post" via htmx
    Then the item "Post" is read
    And the item "Post" is saved for later

  Scenario: Opening a saved video's detail view leaves it saved
    Given the source "Tube" has a YouTube item "Clip" published 1 days ago
    And the item "Clip" is saved for later
    When I request the detail view of the item "Clip" via htmx
    Then the item "Clip" is saved for later

  Scenario: Following a saved item's open link leaves it saved
    Given the source "Alpha" has an unread item "Long talk" with the link "https://alpha.example/talk" published 1 days ago
    And the item "Long talk" is saved for later
    When I open the item "Long talk"
    Then the item "Long talk" is read
    And the item "Long talk" is saved for later

  Scenario: A read item saved for later is still listed in the Later view
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    And the item "Long talk" is saved for later
    When I mark the item "Long talk" as read
    And I request the home page "/feed?later=1"
    Then the timeline lists "Long talk"

  Scenario: Mark all read leaves saved items saved
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    And the item "Long talk" is saved for later
    When I mark all items read
    And I request the home page "/feed?later=1"
    Then the timeline lists "Long talk"

  # ---- the Later filter ---------------------------------------------------------------

  Scenario: The left navigation offers Later after Favourites
    Given the group "Music" exists
    When I request the home page "/feed"
    Then the navigation ends with the "Later" entry linking to "/feed?later=1"
    And the "Later" entry follows the "Favourites" entry

  Scenario: The Later view lists items earliest-saved first, read or not
    Given the source "Alpha" has an unread item "Third saved" published 1 days ago
    And the source "Alpha" has a read item "First saved" published 5 days ago
    And the source "Alpha" has an unread item "Second saved" published 3 days ago
    And the source "Alpha" has an unread item "Plain" published 2 days ago
    When I save the item "First saved" for later via htmx
    And I save the item "Second saved" for later via htmx
    And I save the item "Third saved" for later via htmx
    And I request the home page "/feed?later=1"
    Then the timeline lists the items in this order
      | First saved  |
      | Second saved |
      | Third saved  |
    And the timeline does not list "Plain"

  Scenario: Items saved at the same instant are ordered by id ascending
    Given the source "Alpha" has unread items "First", "Second" and "Third" stored in that order with the same published date
    And the items "Third", "First" and "Second" are saved for later at the same instant
    When I request the home page "/feed?later=1"
    Then the timeline lists the items in this order
      | First  |
      | Second |
      | Third  |

  Scenario: Clearing and saving again moves an item to the back
    Given the source "Alpha" has an unread item "First" published 3 days ago
    And the source "Alpha" has an unread item "Second" published 2 days ago
    When I save the item "First" for later via htmx
    And I save the item "Second" for later via htmx
    And I clear the item "First" from later via htmx
    And I save the item "First" for later via htmx
    And I request the home page "/feed?later=1"
    Then the timeline lists the items in this order
      | Second |
      | First  |

  Scenario: The Later entry is marked as current in the Later view
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    And the item "Long talk" is saved for later
    When I request the home page "/feed?later=1"
    Then the "Later" entry is marked as current
    And the "Favourites" entry is not marked as current
    And no group entry is marked as current

  Scenario: Saved items from every source and group are listed together
    Given the group "Music" exists
    And the source "Alpha" is in the group "Music"
    And the source "Alpha" has an unread item "Song" published 2 days ago
    And the source "Beta" has an unread item "Talk" published 1 days ago
    When I save the item "Talk" for later via htmx
    And I save the item "Song" for later via htmx
    And I request the home page "/feed?later=1"
    Then the timeline lists the items in this order
      | Talk |
      | Song |

  Scenario: The Later view ignores a group parameter
    Given the group "Music" exists
    And the source "Alpha" is in the group "Music"
    And the source "Alpha" has an unread item "Song" published 2 days ago
    And the source "Beta" has an unread item "Talk" published 1 days ago
    And the item "Song" is saved for later
    And the item "Talk" is saved for later
    When I request the timeline of the group "Music" with the Later filter
    Then the timeline lists "Song"
    And the timeline lists "Talk"

  Scenario: Saving an item makes it appear in the Later view
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    When I request the home page "/feed?later=1"
    Then the timeline does not list "Long talk"
    When I save the item "Long talk" for later via htmx
    And I request the home page "/feed?later=1"
    Then the timeline lists "Long talk"

  Scenario: Clearing an item removes it from the Later view
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    And the item "Long talk" is saved for later
    When I clear the item "Long talk" from later via htmx
    And I request the home page "/feed?later=1"
    Then the timeline lists no items

  Scenario: The Later view has a ⏲ on every row and no read controls
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    And the item "Long talk" is saved for later
    When I request the home page "/feed?later=1"
    Then the timeline entry "Long talk" has a saved later toggle
    And the page has no "Mark all read" control
    And the page has no "Show read" control

  Scenario: The Later view ignores the show parameter
    Given the source "Alpha" has a read item "Old" published 2 days ago
    And the item "Old" is saved for later
    When I request the home page "/feed?later=1&show=all"
    Then the timeline lists "Old"

  Scenario: A favourite that is not saved is not in the Later view
    Given the source "Alpha" has an unread item "Song" published 1 days ago
    And the item "Song" is a favourite
    When I request the home page "/feed?later=1"
    Then the timeline lists no items

  Scenario: Favourites wins when both filters are given
    Given the source "Alpha" has an unread item "Starred" published 2 days ago
    And the source "Alpha" has an unread item "Queued" published 1 days ago
    And the item "Starred" is a favourite
    And the item "Queued" is saved for later
    When I request the home page "/feed?favourites=1&later=1"
    Then the timeline lists "Starred"
    And the timeline does not list "Queued"

  Scenario: Later with nothing saved shows an empty state
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    When I request the home page "/feed?later=1"
    Then the timeline lists no items
    And the page contains "Nothing saved for later"
    And the page does not contain "You're all caught up"

  Scenario: Long Later lists are paged 50 at a time and Load more keeps the filter
    Given the source "Alpha" has 120 unread items published at one-minute intervals
    And every item is saved for later in timeline order, oldest saved first
    And the source "Alpha" has an unread item "Plain" published 60 days ago
    When I request the home page "/feed?later=1"
    Then the timeline lists 50 items
    When I load more
    And I load more
    Then the timeline lists every item exactly once, earliest saved first
    And the timeline lists 120 items
    And the timeline does not list "Plain"

  # ---- retention ----------------------------------------------------------------------

  Scenario: Retention keeps a saved item older than the retention window
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And I have added the feed URL "/news.xml"
    And the stored items of "Daily News" were first seen 91, 89 and 3 days ago
    And the item first seen 91 days ago is saved for later
    When the retention job runs
    Then the source "Daily News" has 3 stored items
    And the items first seen 91, 89 and 3 days ago still exist

  Scenario: Retention still deletes old items that are not saved
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And I have added the feed URL "/news.xml"
    And the stored items of "Daily News" were first seen 200, 100 and 3 days ago
    And the item first seen 200 days ago is saved for later
    When the retention job runs
    Then the source "Daily News" has 2 stored items
    And the item first seen 100 days ago no longer exists
    And the items first seen 200 and 3 days ago still exist

  Scenario: Clearing makes an old item eligible for deletion again
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And I have added the feed URL "/news.xml"
    And the stored items of "Daily News" were first seen 91, 89 and 3 days ago
    And the item first seen 91 days ago is saved for later
    When the retention job runs
    And I clear the item first seen 91 days ago from later
    And the retention job runs
    Then the source "Daily News" has 2 stored items
    And the item first seen 91 days ago no longer exists

  Scenario: A read item saved for later survives retention
    Given the source "Alpha" has a read item "Old gem" published 400 days ago
    And the item "Old gem" was first seen 400 days ago
    And the item "Old gem" is saved for later
    When the retention job runs
    Then the item "Old gem" still exists

  Scenario: Removing a source still deletes its saved items
    Given the source "Alpha" has an unread item "Long talk" published 1 days ago
    And the item "Long talk" is saved for later
    When I remove the source "Alpha"
    Then the item "Long talk" no longer exists

  # ---- upgrade ------------------------------------------------------------------------

  Scenario: Items stored before the upgrade are not saved for later
    Given the database is migrated only up to before read-later
    And it holds a source "Blog" with a stored item titled "Old post"
    And the application is running
    When I request the home page "/feed?later=1"
    Then the timeline lists no items
    When I save the item "Old post" for later via htmx
    Then the item "Old post" is saved for later

  Scenario: Clearing a search in the Later view returns to Later
    Given the source "Alpha" has an unread item "Post saved" published 2 days ago
    And the source "Alpha" has an unread item "Post plain" published 1 days ago
    And the item "Post saved" is saved for later
    When I request the home page "/feed?later=1"
    Then the search form carries "later" as "1"
    When I search for "post" while viewing the Later
    Then the timeline lists the items in this order
      | Post plain |
      | Post saved |
    When I clear the search via htmx while viewing the Later
    Then the timeline lists "Post saved"
    And the timeline does not list "Post plain"

# Non-goals:
#   - Automatic clearing on open, read or play.
#   - Keeping saved items when their source is removed.
#   - Combining the Later view with a group or Favourites filter.
#   - A count on the Later navigation entry; manual queue ordering.
#   - The ⏲ in the player dock.
#
# Risks:
#   - Data loss: retention must honour the later flag in addition to the
#     favourite flag (deleteFirstSeenBefore); any future cleanup that deletes
#     items must honour both.
#   - The group navigation scenarios in source-groups.feature and the
#     Favourites ones count navigation links; the Later entry must not break
#     them (asserted separately here).
#   - Migration V12 is numbered after the reserved V10 (#33) and V11 (#29);
#     the numbering must be revisited if those land differently.
#   - A refetch of a feed must not clear the flag (insertNew dedups on
#     existing rows); not pinned by a scenario of its own.
#   - The Later order is a product decision (earliest-saved first,
#     auto-decided as REAL); a flip to newest-first changes only the
#     ordering scenarios.
#   - Several step phrasings (detail view of a YouTube item, "mark all items
#     read", "open the item") may need step definitions the existing steps lack.
#
# Open questions:
#   - Confirm the Later order (earliest-saved first) at playtest.
