Feature: Chronological feed with read state
  The home page "/feed" is a single, strictly chronological, unranked timeline of
  unread items from all sources, newest first, with read state, "mark all
  read", a "Show read" toggle, paging via htmx, and an explicit end-of-feed
  state.

  Covers: the unread timeline on "/feed" (ordering newest first by published date,
  ties by item id descending); item display (source title, item title,
  published date, thumbnail); opening an item via /items/{id}/open (marks it
  read, redirects 302 to its link); marking one item read via POST
  /items/{id}/read; "Mark all read" limited to items first seen at or before
  the page render time; the "Show read" toggle (/feed?show=all); paging of 50
  items with "Load more"; the "You're all caught up" end-of-feed state; 404
  for unknown item ids; a nullable read_at column on items.
  Superseded in part by end-of-list.feature: "You're all caught up" now shows only
  when the view has nothing unread; a list with unread items left ends with
  "End of list · N unread".
  Supersedes: the placeholder nature of the home page in project-skeleton.feature.
  Its scenario "Home page shows the app name and an empty state" keeps its
  expectations (status 200, "Starve", "No sources yet" when there are no
  sources), so that scenario is not changed; the home page is now the timeline,
  with "No sources yet" as its no-sources state.
  Superseded in part by reader-pane.feature: with JavaScript, a blog post's title
  now opens the in-app reader; /items/{id}/open (mark read, 302) remains the
  no-JS path and the behaviour for YouTube and SoundCloud items.
  Out of scope: groups and folder filtering (#5); inline playback (#7);
  smart-add (#4); mute/keyword rules; authentication.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: Unread items from several sources are shown newest first
    Given the source "Alpha" has an unread item "A-old" published 3 days ago
    And the source "Beta" has an unread item "B-mid" published 2 days ago
    And the source "Alpha" has an unread item "A-new" published 1 days ago
    When I request the home page "/feed"
    Then the response status is 200
    And the timeline lists the items in this order
      | A-new |
      | B-mid |
      | A-old |

  Scenario: Items with the same published date are ordered by id descending
    Given the source "Alpha" has unread items "First", "Second" and "Third" stored in that order with the same published date
    When I request the home page "/feed"
    Then the timeline lists the items in this order
      | Third  |
      | Second |
      | First  |

  Scenario: Each item shows its source, title, date and thumbnail
    Given the source "Alpha" has an unread item "With Pic" published 1 days ago with a thumbnail
    And the source "Alpha" has an unread item "No Pic" published 2 days ago
    When I request the home page "/feed"
    Then the timeline entry "With Pic" shows the source "Alpha"
    And the timeline entry "With Pic" shows a published date
    And the timeline entry "With Pic" shows a thumbnail
    And the timeline entry "No Pic" shows no thumbnail

  Scenario: Opening an item marks it read and redirects to its link
    Given the source "Alpha" has an unread item "Story" published 1 days ago
    When I open the item "Story"
    Then the response status is 302
    And the response redirects to the link of "Story"
    And the item "Story" is read
    When I request the home page "/feed"
    Then the timeline does not list "Story"

  Scenario: Scrolling past an item does not mark it read
    Given the source "Alpha" has an unread item "Story" published 1 days ago
    When I request the home page "/feed"
    And I request the home page "/feed"
    Then the item "Story" is unread
    And the timeline lists "Story"

  Scenario: Marking one item read removes it from the unread view
    Given the source "Alpha" has an unread item "Keep" published 1 days ago
    And the source "Alpha" has an unread item "Done" published 2 days ago
    When I mark the item "Done" read
    Then the item "Done" is read
    When I request the home page "/feed"
    Then the timeline lists "Keep"
    And the timeline does not list "Done"

  Scenario: Marking one item read via htmx succeeds without a redirect
    Given the source "Alpha" has an unread item "Done" published 1 days ago
    When I mark the item "Done" read via htmx
    Then the response status is 200
    And the item "Done" is read

  Scenario: Mark all read leads to the all-caught-up state
    Given the source "Alpha" has an unread item "One" published 1 days ago
    And the source "Beta" has an unread item "Two" published 2 days ago
    When I request the home page "/feed"
    And I mark all read from the page I received
    And I request the home page "/feed"
    Then the timeline lists no items
    And the page contains "You're all caught up"

  Scenario: Items that arrive after the page was rendered survive mark all read
    Given the source "Alpha" has an unread item "Seen" published 2 days ago
    When I request the home page "/feed"
    And the source "Alpha" receives a new unread item "Late" after that page was rendered
    And I mark all read from the page I received
    And I request the home page "/feed"
    Then the timeline lists "Late"
    And the timeline does not list "Seen"

  Scenario: Show read lists read items marked as read
    Given the source "Alpha" has an unread item "Fresh" published 1 days ago
    And the source "Alpha" has a read item "Old" published 2 days ago
    When I request the home page "/feed?show=all"
    Then the timeline lists the items in this order
      | Fresh |
      | Old   |
    And the timeline entry "Old" is marked as read
    And the timeline entry "Fresh" is not marked as read

  Scenario: The default view hides read items
    Given the source "Alpha" has a read item "Old" published 2 days ago
    When I request the home page "/feed"
    Then the timeline does not list "Old"
    And the page contains "You're all caught up"

  Scenario: No sources shows the empty state with a link to sources
    When I request the home page "/feed"
    Then the response status is 200
    And the page contains "Starve"
    And the page contains "No sources yet"
    And the page contains "/sources"

  Scenario: The page is usable at phone width
    When I request the home page "/feed"
    Then the page contains a viewport meta tag

  Scenario: Long timelines are paged 50 at a time with Load more
    Given the source "Alpha" has 120 unread items published at one-minute intervals
    When I request the home page "/feed"
    Then the timeline lists 50 items
    And the timeline offers "Load more"
    And the page does not contain "You're all caught up"
    When I load more
    Then the timeline lists 100 items
    And the timeline offers "Load more"
    When I load more
    Then the timeline lists 120 items
    And the timeline does not offer "Load more"
    And the list ends with "End of list · 120 unread"

  Scenario: Pages do not repeat or skip items
    Given the source "Alpha" has 120 unread items published at one-minute intervals
    When I request the home page "/feed"
    And I load more
    And I load more
    Then the timeline lists every item exactly once, newest first

  Scenario Outline: Unknown item ids return 404
    When I <action> the unknown item 99999
    Then the response status is 404

    Examples:
      | action    |
      | open      |
      | mark read |

# Non-goals:
#   - Groups/folder filtering (#5), inline playback (#7), smart-add (#4),
#     mute/keyword rules, authentication.
#   - Ranking or personalization of any kind.
#   - Automatic read marking on scroll.
#   - Visual layout verification beyond the viewport meta tag; the 375px
#     readability check is covered by the human playtest.
#
# Risks:
#   - The skeleton scenario "Home page shows the app name and an empty state"
#     in project-skeleton.feature should still pass unchanged (200, "Starve",
#     "No sources yet"). If the home page rewrite breaks it, the coder
#     handles it (this spec does not edit project-skeleton.feature).
#   - The ingestion scenario "The home page links to the sources page"
#     requests "/" with no sources and expects "/sources"; the no-sources
#     state must keep that link.
#   - New migration adds a nullable read_at column to items; existing rows
#     become unread. Existing ingestion steps that insert items directly must
#     keep working.
#   - Keyset cursor on (published date, id) must be stable when many items
#     share a published date; tested by the ties and paging scenarios.
#   - The mark-all-read cutoff uses first-seen time vs. the render timestamp;
#     the clock should be injectable so "Late" can be stored after render.
#   - htmx behavior is verified at the HTTP level (HX-Request header, partial
#     response), not in a browser; the playtest covers the real interaction.
#   - SQLite single-writer locking when the poller and mark-all-read overlap.
#   - New step phrasings (item seeding, timeline assertions) need step
#     definitions; existing phrasings are reused where they fit.
#
# Open questions:
#   - None. Decisions are recorded under Clarifications in
#     specs/intent/chronological-feed.md.
