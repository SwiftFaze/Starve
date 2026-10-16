Feature: Full-text search across titles, sources and post bodies
  A search box at the head of the timeline's list finds items by words in their title, their
  source's name or their post body, as you type. Results use the normal list
  layout, newest first, and ignore the group and Show read filters. The search
  is kept in a SQLite FTS5 index that stays in step with the items table.

  Covers: GET /feed?q=<text> as the search URL (bookmarkable and reloadable; the
  page renders the box filled in and the results); a search box at the head of the list
  requesting that URL via htmx on typing with a debounce, updating the list in
  place and pushing the URL, and clearing the box returning to the timeline, or to the Favourites view or
  group the search was started from (the form carrying `favourites`, `group`
  and `show` as hidden fields, and "Load more" keeping them);
  matching words in the item title, the source name and the post body, case-
  and accent-insensitively, every typed word having to match (AND) and the
  last word matching as a prefix, so partial words work while typing; the body
  being searched as text only, never its markup (tag names and attribute
  values do not match); results being all items of all sources, read or
  unread, newest first by published date with ties by id descending, paged 50
  at a time with "Load more" keeping the search; result rows being the timeline
  rows (title opening the reader or the open endpoint, Play for playable
  items); a "No matches" empty state; input containing FTS5 syntax characters
  (quotes, *, -, OR, AND, NEAR, parentheses, colons) being treated as plain
  words and never failing; a query with no word characters showing the "No
  matches" state; a query of only whitespace behaving as no query; the index
  covering every item on insert, when a missing body is filled in on refetch,
  when the source is renamed, when retention deletes items and when a source is
  removed; migration V10 creating the index and back-filling it from the items
  and bodies that already exist.
  Supersedes: nothing. It adds a view beside the timeline of
  chronological-feed.feature and source-groups.feature, and the "search over
  bodies" left out of post-bodies.feature and reader-pane.feature.
  Out of scope: narrowing search by the Favourites filter (#31) or any other
  filter, which results ignore by design (the view is only remembered so that
  clearing the search returns to it); the
  Mark all read control in search results (not offered); advanced query syntax
  in the UI; highlighting or snippets of the matched text; ranking by
  relevance; searching items already deleted by retention; the browser
  behaviour of debounce, caret and focus (human playtest); the star and
  snooze controls, whose own specs apply to any row once they land.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A word in the title finds the item
    Given the source "Blog" has an unread item "Rust async runtimes" published 2 days ago
    And the source "Blog" has an unread item "Gardening notes" published 1 days ago
    When I search for "rust"
    Then the response status is 200
    And the timeline lists the items in this order
      | Rust async runtimes |

  Scenario: A word in the source name finds that source's items
    Given the source "Retro Computing Weekly" has an unread item "Episode 12" published 2 days ago
    And the source "Other" has an unread item "Episode 13" published 1 days ago
    When I search for "retro"
    Then the timeline lists the items in this order
      | Episode 12 |

  Scenario: A word in the post body finds the item
    Given the source "Blog" has an item "Weekend post" with the stored body "<p>We discussed the zeppelin revival</p>" published 1 days ago
    And the source "Blog" has an item "Other post" with the stored body "<p>Nothing relevant</p>" published 2 days ago
    When I search for "zeppelin"
    Then the timeline lists the items in this order
      | Weekend post |

  Scenario: Markup in a body is not searchable, only its text
    Given the source "Blog" has an item "Linky" with the stored body "<p>Plain <a href='https://zzz.example/page' class='fancyclass'>words</a></p>" published 1 days ago
    When I search for "fancyclass"
    Then the timeline lists no items
    When I search for "href"
    Then the timeline lists no items
    When I search for "words"
    Then the timeline lists "Linky"

  Scenario: Every typed word must match
    Given the source "Blog" has an unread item "Rust async runtimes" published 2 days ago
    And the source "Blog" has an unread item "Rust compiler errors" published 1 days ago
    When I search for "rust async"
    Then the timeline lists the items in this order
      | Rust async runtimes |

  Scenario: Words may match in different fields of the same item
    Given the source "Retro Computing Weekly" has an item "Episode 12" with the stored body "<p>All about zeppelins</p>" published 1 days ago
    When I search for "retro zeppelins"
    Then the timeline lists "Episode 12"

  Scenario: The last word matches as a prefix so results appear while typing
    Given the source "Blog" has an unread item "Understanding concurrency" published 1 days ago
    When I search for "underst"
    Then the timeline lists "Understanding concurrency"

  Scenario: Only the last word is a prefix; earlier words must match whole
    Given the source "Blog" has an unread item "Understanding concurrency" published 1 days ago
    When I search for "underst concur"
    Then the timeline lists no items

  Scenario Outline: Matching ignores case and accents
    Given the source "Blog" has an unread item "<title>" published 1 days ago
    When I search for "<query>"
    Then the timeline lists "<title>"

    Examples:
      | title         | query  |
      | Café Culture  | cafe   |
      | Café Culture  | CAFÉ   |
      | Zurich Notes  | zürich |
      | LOUD HEADLINE | loud   |

  Scenario: Results are newest first and include read items
    Given the source "Alpha" has an unread item "Match old" published 3 days ago
    And the source "Beta" has a read item "Match mid" published 2 days ago
    And the source "Alpha" has an unread item "Match new" published 1 days ago
    When I search for "match"
    Then the timeline lists the items in this order
      | Match new |
      | Match mid |
      | Match old |

  Scenario: Results ignore the group filter
    Given the group "Music" exists
    And the source "Alpha" is in the group "Music"
    And the source "Alpha" has an unread item "Match in group" published 2 days ago
    And the source "Beta" has an unread item "Match outside" published 1 days ago
    When I search for "match" while viewing the group "Music"
    Then the timeline lists the items in this order
      | Match outside  |
      | Match in group |

  Scenario: Results ignore the Show read setting
    Given the source "Alpha" has a read item "Match read" published 2 days ago
    When I search for "match" while showing only unread items
    Then the timeline lists "Match read"

  Scenario: Nothing matching shows a clear empty state
    Given the source "Alpha" has an unread item "Something else" published 1 days ago
    When I search for "nonexistent"
    Then the response status is 200
    And the timeline lists no items
    And the page contains "No matches"
    And the page does not contain "all caught up"

  Scenario: The search page keeps the box filled in so it can be bookmarked and reloaded
    Given the source "Alpha" has an unread item "Match here" published 1 days ago
    When I request the home page "/feed?q=match"
    Then the response status is 200
    And the search box contains "match"
    And the timeline lists "Match here"

  Scenario: Typing requests the results via htmx and pushes the search URL
    When I request the home page "/feed"
    Then the timeline's sheet starts with the search form
    And the search box requests the timeline via htmx as you type with a debounce
    And the search box pushes the search URL into the browser history

  Scenario: An htmx search returns only the results, not the whole page
    Given the source "Alpha" has an unread item "Match here" published 1 days ago
    When I search for "match" via htmx
    Then the response status is 200
    And the timeline lists "Match here"
    And the response is a fragment without the top bar

  Scenario: An empty or blank query returns the normal timeline
    Given the source "Alpha" has an unread item "Unread one" published 1 days ago
    And the source "Alpha" has a read item "Read one" published 2 days ago
    When I search for ""
    Then the timeline lists the items in this order
      | Unread one |
    When I search for "   "
    Then the timeline lists the items in this order
      | Unread one |

  Scenario: Result rows behave like timeline rows
    Given the source "Blog" has an unread item "Match post" with the link "https://blog.example/post" published 1 days ago
    And the source "Tube" has an unread playable item "Match video" published 2 days ago
    When I search for "match"
    Then the title of "Match post" requests its reader into the reader pane via htmx
    And the title of "Match post" still links to its open endpoint without JavaScript
    And the timeline entry "Match video" offers Play

  Scenario: Search results are paged 50 at a time and Load more keeps the search
    Given the source "Alpha" has 60 unread items published at one-minute intervals all titled with "Match"
    When I search for "match"
    Then the timeline lists 50 items
    And the timeline offers "Load more"
    When I load more
    Then the timeline lists every item exactly once, newest first

  Scenario: Clearing the search in Favourites returns to Favourites
    Given the source "Alpha" has an unread item "Post starred" published 2 days ago
    And the source "Alpha" has an unread item "Post plain" published 1 days ago
    And the item "Post starred" is a favourite
    When I search for "post" while viewing the Favourites
    Then the timeline lists the items in this order
      | Post plain   |
      | Post starred |
    When I clear the search via htmx while viewing the Favourites
    Then the timeline lists "Post starred"
    And the timeline does not list "Post plain"

  Scenario: Clearing the search in a group returns to that group
    Given the group "Music" exists
    And the source "Alpha" is in the group "Music"
    And the source "Alpha" has an unread item "Match in group" published 2 days ago
    And the source "Beta" has an unread item "Other outside" published 1 days ago
    When I clear the search via htmx while viewing the group "Music"
    Then the timeline lists "Match in group"
    And the timeline does not list "Other outside"

  Scenario: The search form carries the view it was opened from
    Given the group "Music" exists
    And the source "Alpha" has an unread item "Post one" published 1 days ago
    When I request the home page "/feed?favourites=1"
    Then the search form carries "favourites" as "1"
    When I search for "post" while viewing the group "Music"
    Then the search form carries the group "Music"

  Scenario: A search URL that carries favourites still searches everything
    Given the source "Alpha" has an unread item "Post starred" published 2 days ago
    And the source "Alpha" has an unread item "Post plain" published 1 days ago
    And the item "Post starred" is a favourite
    When I request the home page "/feed?q=plain&favourites=1"
    Then the timeline lists "Post plain"
    And the page does not contain "No favourites yet"

  Scenario: Mark all read is not offered in search results
    Given the source "Alpha" has an unread item "Match here" published 1 days ago
    When I search for "match"
    Then the timeline does not offer "Mark all read"

  Scenario Outline: FTS syntax characters in the input are plain text and never fail
    Given the source "Blog" has an unread item "Plain title about cats" published 1 days ago
    When I search for "<query>"
    Then the response status is 200

    Examples:
      | query           |
      | 'unbalanced     |
      | "unbalanced     |
      | "cats"          |
      | cats*           |
      | *               |
      | -cats           |
      | cats OR dogs    |
      | cats AND        |
      | NOT cats        |
      | NEAR(cats dogs) |
      | title:cats      |
      | (cats           |
      | ^cats           |
      | a"b'c           |
      | ~!@#$%&()       |

  Scenario: Operator words are searched as ordinary words
    Given the source "Blog" has an unread item "Cats OR dogs debate" published 2 days ago
    And the source "Blog" has an unread item "Cats alone" published 1 days ago
    When I search for "cats OR dogs"
    Then the timeline lists the items in this order
      | Cats OR dogs debate |

  Scenario: Punctuation around words does not stop them matching
    Given the source "Blog" has an unread item "Hello, world! (a test)" published 1 days ago
    When I search for "'world' -test"
    Then the timeline lists "Hello, world! (a test)"

  Scenario: A query with no word characters shows the no-matches state
    Given the source "Blog" has an unread item "Plain title" published 1 days ago
    When I search for "*"
    Then the response status is 200
    And the timeline lists no items
    And the page contains "No matches"

  Scenario: An item added by ingest is searchable
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <p>Fresh ingest zeppelin</p>
      """
    When I add the feed URL "/blog.xml"
    And I search for "zeppelin"
    Then the timeline lists 1 items

  Scenario: A body filled in by a refetch becomes searchable, once
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded "<p>First</p>"
    And I have added the feed URL "/blog.xml"
    And the stored body of the item of "Blog" is removed
    When the feed server now serves "/blog.xml" with the content:encoded "<p>Second zeppelin</p>"
    And the poller runs
    And I search for "zeppelin"
    Then the timeline lists 1 items
    When the feed server now serves "/blog.xml" with the content:encoded "<p>Third dirigible</p>"
    And the poller runs
    And I search for "dirigible"
    Then the timeline lists no items
    When I search for "zeppelin"
    Then the timeline lists 1 items

  Scenario: Renaming a source updates what its items are found by
    Given the source "Old Name" has an unread item "Episode" published 1 days ago
    When the source "Old Name" is renamed to "Brand New"
    And I search for "brand"
    Then the timeline lists "Episode"
    When I search for "old"
    Then the timeline lists no items

  Scenario: Retention removes deleted items from search
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 3 entries
    And I have added the feed URL "/news.xml"
    And the stored items of "Daily News" were first seen 91, 89 and 3 days ago
    When the retention job runs
    And I search for "Daily"
    Then the timeline lists 2 items
    And the search index holds 2 entries

  Scenario: Removing a source removes its items from search
    Given the source "Gone" has an unread item "Match here" published 1 days ago
    And the source "Kept" has an unread item "Match there" published 2 days ago
    When I remove the source "Gone"
    And I search for "match"
    Then the timeline lists the items in this order
      | Match there |
    And the search index holds 1 entries

  Scenario: Items stored before the upgrade become searchable after migration
    Given the database is migrated only up to before full-text search
    And it holds a source "Old Blog" with a stored item titled "Old post" having the stored body "<p>Ancient zeppelin lore</p>"
    When the application is running
    And I search for "zeppelin"
    Then the timeline lists "Old post"
    When I search for "old blog"
    Then the timeline lists "Old post"
    When I search for "old post"
    Then the timeline lists "Old post"

# Non-goals:
#   - Relevance ranking, snippets, highlighting, typo tolerance or stemming.
#   - Narrowing search by the Favourites filter (#31) or other filters.
#   - A search-only index of deleted items.
#
# Risks:
#   - The FTS5 module must be present in the bundled sqlite-jdbc build; the
#     intent doc records this as a decision to be confirmed by a spike before
#     the migration is written.
#   - Migration numbering: V7 (#28), V8 (#30) and V9 (#31) are in flight, so
#     this migration is V10 and must be renumbered if any of them changes.
#   - The index holds the body as text with markup stripped; the sanitised
#     body is capped at 200000 characters, so one index row is bounded.
#   - Play on result rows comes for free only if results reuse the timeline
#     row template; the star and snooze controls are not asserted here.
#
# Open questions:
#   - None (see the intent doc's Clarifications for the auto-decided ones).
