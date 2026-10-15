Feature: Source groups
  Sources can be organised into named groups (at most one per source), and the
  home timeline can be filtered to one group.

  Covers: creating, renaming and deleting groups on "/sources" (names trimmed,
  1-50 characters, unique case-insensitively); assigning a source to a group
  or back to "Ungrouped"; the group navigation on "/feed" listing "All" plus each
  group alphabetically (case-insensitive) with its unread count; the filtered
  timeline "/feed?group=<id>" with the same ordering, paging and read state as the
  main timeline; "Mark all read" limited to the current group; "Load more"
  keeping the group filter; deleting a group ungrouping (not deleting) its
  sources; 404 for an unknown group id; a groups table and a nullable
  group_id on sources (migration V4).
  Supersedes: nothing. The existing timeline scenarios in
  chronological-feed.feature are unchanged (no group parameter means "All").
  Out of scope: tags or multiple groups per source; smart/keyword groups;
  OPML folder import (#6); an "Ungrouped" filter; inline playback (#7).

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: Creating a group lists it on the sources page
    When I create the group "Music"
    Then the sources page lists the group "Music"

  Scenario: Group names are trimmed
    When I create the group "  News  "
    Then the sources page lists the group "News"

  Scenario Outline: Invalid group names are rejected
    Given the group "Music" exists
    When I create the group "<name>"
    Then the sources page shows the message "<message>"
    And there is 1 group

    Examples:
      | name                                                | message                               |
      |                                                     | group name is required                |
      | Music                                               | a group with that name already exists |
      | music                                               | a group with that name already exists |
      | 012345678901234567890123456789012345678901234567890 | at most 50 characters                 |

  Scenario: Renaming a group keeps its sources
    Given the group "Tunes" exists
    And the source "Alpha" is in the group "Tunes"
    When I rename the group "Tunes" to "Music"
    Then the sources page lists the group "Music"
    And the sources page does not list the group "Tunes"
    And the source "Alpha" is in the group "Music"

  Scenario: Renaming a group to another group's name is rejected
    Given the group "Music" exists
    And the group "News" exists
    When I rename the group "News" to "music"
    Then the sources page shows the message "a group with that name already exists"
    And the sources page lists the group "News"

  Scenario: A group's timeline shows only its sources' items, newest first
    Given the group "Music" exists
    And the source "Alpha" has an unread item "A-old" published 3 days ago
    And the source "Beta" has an unread item "B-mid" published 2 days ago
    And the source "Gamma" has an unread item "G-new" published 1 days ago
    And the source "Alpha" is in the group "Music"
    And the source "Gamma" is in the group "Music"
    When I request the timeline of the group "Music"
    Then the response status is 200
    And the timeline lists the items in this order
      | G-new |
      | A-old |

  Scenario: Assigning a source to a different group moves it
    Given the group "Music" exists
    And the group "Video" exists
    And the source "Alpha" has an unread item "A-1" published 1 days ago
    And the source "Alpha" is in the group "Music"
    When I assign the source "Alpha" to the group "Video"
    And I request the timeline of the group "Music"
    Then the timeline lists no items
    When I request the timeline of the group "Video"
    Then the timeline lists "A-1"

  Scenario: Choosing Ungrouped removes a source from its group
    Given the group "Music" exists
    And the source "Alpha" has an unread item "A-1" published 1 days ago
    And the source "Alpha" is in the group "Music"
    When I assign the source "Alpha" to "Ungrouped"
    And I request the timeline of the group "Music"
    Then the timeline lists no items
    When I request the home page "/feed"
    Then the timeline lists "A-1"

  Scenario: The All view still shows every source including ungrouped ones
    Given the group "Music" exists
    And the source "Alpha" has an unread item "A-1" published 3 days ago
    And the source "Beta" has an unread item "B-1" published 2 days ago
    And the source "Gamma" has an unread item "G-1" published 1 days ago
    And the source "Alpha" is in the group "Music"
    When I request the home page "/feed"
    Then the timeline lists the items in this order
      | G-1 |
      | B-1 |
      | A-1 |

  Scenario: A group's view respects Show read
    Given the group "Music" exists
    And the source "Alpha" has an unread item "Fresh" published 1 days ago
    And the source "Alpha" has a read item "Old" published 2 days ago
    And the source "Beta" has a read item "Other" published 3 days ago
    And the source "Alpha" is in the group "Music"
    When I request the timeline of the group "Music" showing read items
    Then the timeline lists the items in this order
      | Fresh |
      | Old   |
    And the timeline entry "Old" is marked as read

  Scenario: The group navigation shows per-group unread counts
    Given the group "Music" exists
    And the group "News" exists
    And the source "Alpha" has an unread item "A-1" published 1 days ago
    And the source "Alpha" has an unread item "A-2" published 2 days ago
    And the source "Alpha" has a read item "A-3" published 3 days ago
    And the source "Beta" has an unread item "B-1" published 1 days ago
    And the source "Gamma" has an unread item "G-1" published 1 days ago
    And the source "Alpha" is in the group "Music"
    And the source "Beta" is in the group "News"
    When I request the home page "/feed"
    Then the group navigation lists the entries
      | All (4)   |
      | Music (2) |
      | News (1)  |

  Scenario: The current group is marked in the navigation
    Given the group "Music" exists
    And the source "Alpha" has an unread item "A-1" published 1 days ago
    And the source "Alpha" is in the group "Music"
    When I request the timeline of the group "Music"
    Then the group navigation marks "Music (1)" as current
    When I request the home page "/feed"
    Then the group navigation marks "All (1)" as current

  Scenario: Groups are listed alphabetically regardless of case
    Given the group "zebra" exists
    And the group "Apple" exists
    And the group "mango" exists
    When I request the home page "/feed"
    Then the group navigation lists the groups in this order
      | Apple |
      | mango |
      | zebra |
    And the sources page lists the groups in this order
      | Apple |
      | mango |
      | zebra |

  Scenario: Mark all read inside a group leaves other groups unread
    Given the group "Music" exists
    And the group "News" exists
    And the source "Alpha" has an unread item "A-1" published 2 days ago
    And the source "Beta" has an unread item "B-1" published 1 days ago
    And the source "Gamma" has an unread item "G-1" published 1 days ago
    And the source "Alpha" is in the group "Music"
    And the source "Beta" is in the group "News"
    When I request the timeline of the group "Music"
    And I mark all read from the page I received
    And I request the timeline of the group "Music"
    Then the timeline lists no items
    And the page contains "You're all caught up"
    And the item "A-1" is read
    And the item "B-1" is unread
    And the item "G-1" is unread
    When I request the home page "/feed"
    Then the timeline lists "B-1"
    And the timeline lists "G-1"
    And the timeline does not list "A-1"

  Scenario: Items arriving after the group page was rendered survive mark all read
    Given the group "Music" exists
    And the source "Alpha" has an unread item "Seen" published 2 days ago
    And the source "Alpha" is in the group "Music"
    When I request the timeline of the group "Music"
    And the source "Alpha" receives a new unread item "Late" after that page was rendered
    And I mark all read from the page I received
    And I request the timeline of the group "Music"
    Then the timeline lists "Late"
    And the timeline does not list "Seen"

  Scenario: Deleting a group ungroups its sources and keeps their items
    Given the group "Music" exists
    And the source "Alpha" has an unread item "A-1" published 1 days ago
    And the source "Alpha" is in the group "Music"
    When I delete the group "Music"
    Then the sources page does not list the group "Music"
    And the sources page lists a source titled "Alpha"
    And the source "Alpha" is ungrouped
    When I request the home page "/feed"
    Then the timeline lists "A-1"
    And the group navigation lists the entries
      | All (1) |

  Scenario: A long group timeline is paged and keeps the group filter
    Given the group "Music" exists
    And the source "Alpha" has 120 unread items published at one-minute intervals
    And the source "Beta" has an unread item "Elsewhere" published 1 days ago
    And the source "Alpha" is in the group "Music"
    When I request the timeline of the group "Music"
    Then the timeline lists 50 items
    And the timeline offers "Load more"
    And the "Load more" link keeps the group filter
    When I load more
    Then the timeline lists 100 items
    When I load more
    Then the timeline lists 120 items
    And the timeline does not list "Elsewhere"
    And the timeline does not offer "Load more"
    And the page contains "You're all caught up"

  Scenario Outline: An unknown group returns 404
    When I request "<path>"
    Then the response status is 404

    Examples:
      | path              |
      | /feed?group=99999 |
      | /feed?group=abc   |

# Non-goals:
#   - Tags / multiple groups per source, smart or keyword groups.
#   - OPML folder import (#6); an "Ungrouped" filter view.
#   - Manual ordering or drag-and-drop of groups.
#   - Visual layout verification of the nav; the playtest covers it.
#
# Risks:
#   - Migration V4 is reserved for groups; a parallel branch adding a later
#     migration must not collide on the version number.
#   - SQLite foreign keys are off by default; ON DELETE SET NULL only works if
#     the connection enables PRAGMA foreign_keys, otherwise delete must
#     ungroup explicitly. The delete scenario guards this.
#   - The nav count query adds load to every home page render; an index on
#     items(source_id, read_at) may be needed for large item tables.
#   - Mark-all-read in a group must bound by group AND render timestamp; the
#     "Late" scenario guards the timestamp, the leave-others-unread scenario
#     guards the group.
#   - Group names are rendered into HTML and links and must be escaped.
#   - New step phrasings (group creation, assignment, group nav assertions)
#     need step definitions; existing timeline phrasings are reused. The
#     "item is read/unread" steps are assumed to find items by title across
#     sources.
#   - Cucumber trims table cells, so the blank-name row cannot express
#     whitespace-only input; the empty case stands in for it.
#
# Open questions:
#   - None. Decisions are recorded under Clarifications in
#     specs/intent/source-groups.md.
