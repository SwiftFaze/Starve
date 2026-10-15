Feature: Per-user sources, groups and items
  Every source and group belongs to one user (its owner). Items, and therefore
  read state, favourites and read-later, belong to the owner of their source.
  Two users see and change only their own data, and can follow the same feed
  independently.

  Covers: migration V14 giving sources and groups a NOT NULL owner_id (FK to
  users, ON DELETE CASCADE, so deleting a user removes their sources, groups
  and items), with uniqueness (owner_id, url) for sources and (owner_id, name)
  for groups (still case-insensitive); the same URL or group name existing
  under two owners with independent items and read, favourite and later state;
  every user-facing read scoped to the signed-in user: timeline "/feed" (unread,
  "Show read", groups, Favourites and Later filters), group navigation, the
  sources page, full-text search (the global item_search index filtered by
  owner), site icons; every write scoped likewise: add source, remove source,
  set group, create/rename/delete group, mark read, "Mark all read",
  favourite, unfavourite, save for later, clear from later, OPML/Takeout import
  and its "already subscribed" counting; the reader, detail view, player and
  open endpoints; 404 (never the data, never 403) for another user's source,
  group or item id; background polling and retention still covering every
  owner's sources.
  Supersedes: nothing. Existing scenarios keep their meaning: they run as the
  single user "owner" (see accounts-login.feature) and so see only their own
  data. The accounts-login.feature non-goal "every signed-in user still sees
  the one shared dataset" no longer holds.
  Out of scope: signup (#44); user deletion UI and account pages (#45);
  sharing or de-duplicated fetching of a feed between users (a feed followed by
  two users is fetched twice); per-user retention; migrating a database that
  already holds sources or groups (fresh database, no backfill).

  Test harness (new steps, implemented by the coder): "Given the user {string}
  exists" seeds a non-admin user with password "correct-horse-1" through the
  user repository (signup is #44). "When I sign in as {string}" replaces the
  test client's session with that seeded user's, bypassing the login form.
  "Given the application is running" is still setup done for "owner" and the
  client signed in as them. Every existing seeding or acting step (sources,
  items, groups, add feed URL, import, favourite, later, mark read, search)
  acts for the user currently signed in; steps that name an item, source or
  group by title/name resolve its id across all owners so a user can target
  another user's row. "When I GET|POST {string} with the id of the item|source|
  group {string}" sends the request replacing "{id}" in the path with that
  row's id; "... and the group id of {string}" additionally sends that group's
  id as the form's group. Database steps ("the database rejects/accepts ...")
  insert rows directly with an explicit owner.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running
    And the user "alice" exists

  # ---------- Schema ----------

  Scenario: The database requires an owner on sources and groups
    Then the database rejects a source without an owner
    And the database rejects a group without an owner

  Scenario: The same URL or group name can exist under two owners but not twice under one
    Then the database accepts the source URL "https://example.test/a.xml" under both "owner" and "alice"
    And the database rejects the source URL "https://example.test/a.xml" a second time under "owner"
    And the database accepts the group name "Music" under both "owner" and "alice"
    And the database rejects the group name "music" a second time under "owner"

  Scenario: Deleting a user removes their sources, groups and items and nobody else's
    Given the source "Owner Blog" has an unread item "Owner post" published 1 days ago
    And the group "Owner Group" exists
    When I sign in as "alice"
    And the source "Alice Blog" has an unread item "Alice post" published 1 days ago
    And the group "Alice Group" exists
    And the user "alice" is deleted from the database
    Then no source titled "Alice Blog" exists
    And no group named "Alice Group" exists
    And the item "Alice post" no longer exists
    And the search index holds 1 entries
    And the item "Owner post" still exists
    And the source "Owner Blog" and the group "Owner Group" still exist

  # ---------- Timeline and read state ----------

  Scenario: Each user's timeline shows only their own items
    Given the source "Owner Blog" has an unread item "Owner post" published 2 days ago
    When I sign in as "alice"
    And the source "Alice Blog" has an unread item "Alice post" published 1 days ago
    And I request the home page "/feed"
    Then the timeline lists the items in this order
      | Alice post |
    And the response does not contain "Owner post"
    When I sign in as "owner"
    And I request the home page "/feed"
    Then the timeline lists the items in this order
      | Owner post |
    And the response does not contain "Alice post"

  Scenario: A new user starts with an empty timeline and no sources
    When I sign in as "alice"
    And I request the home page "/feed"
    Then the page contains "No sources yet"
    When I request "/sources"
    Then the response status is 200

  Scenario: Show read lists only the signed-in user's read items
    Given the source "Owner Blog" has a read item "Owner read" published 2 days ago
    When I sign in as "alice"
    And the source "Alice Blog" has a read item "Alice read" published 1 days ago
    And I request the home page "/feed?show=all"
    Then the timeline lists the items in this order
      | Alice read |

  Scenario: Mark all read affects only the signed-in user's items
    Given the source "Owner Blog" has an unread item "Owner post" published 1 days ago
    When I sign in as "alice"
    And the source "Alice Blog" has an unread item "Alice post" published 1 days ago
    And I mark all items read
    Then the item "Alice post" is read
    And the item "Owner post" is unread

  # ---------- Same feed, two users ----------

  Scenario: Two users can subscribe to the same URL independently
    Given the feed server serves "/shared.xml" as an RSS 2.0 feed titled "Shared" with 2 entries
    And I have added the feed URL "/shared.xml"
    When I sign in as "alice"
    And I add the feed URL "/shared.xml"
    Then the sources page lists a source titled "Shared"
    And the source "Shared" has 2 stored items
    When I sign in as "owner"
    And I request "/sources"
    Then the sources page lists a source titled "Shared"

  Scenario: Adding a URL the same user already follows is still rejected
    Given the feed server serves "/shared.xml" as an RSS 2.0 feed titled "Shared" with 1 entries
    And I have added the feed URL "/shared.xml"
    When I sign in as "alice"
    And I have added the feed URL "/shared.xml"
    And I sign in as "owner"
    And I add the feed URL "/shared.xml"
    Then the sources page shows the message "You are already subscribed to that feed"

  Scenario: Marking an item read does not touch another user's copy of the same feed
    Given the feed server serves "/shared.xml" as an RSS 2.0 feed titled "Shared" with 2 entries
    And I have added the feed URL "/shared.xml"
    When I sign in as "alice"
    And I have added the feed URL "/shared.xml"
    And I mark all items read
    And I sign in as "owner"
    And I request the home page "/feed"
    Then the timeline lists the items in this order
      | Shared 2 |
      | Shared 1 |

  Scenario: Removing one user's source leaves the other's source and items alone
    Given the feed server serves "/shared.xml" as an RSS 2.0 feed titled "Shared" with 2 entries
    And I have added the feed URL "/shared.xml"
    When I sign in as "alice"
    And I have added the feed URL "/shared.xml"
    And I remove the source "Shared"
    And I request the home page "/feed"
    Then the page contains "No sources yet"
    When I sign in as "owner"
    And I request the home page "/feed"
    Then the timeline lists the items in this order
      | Shared 2 |
      | Shared 1 |

  Scenario: The background poll fetches every user's sources
    Given the feed server serves "/owner.xml" as an RSS 2.0 feed titled "Owner Feed" with 1 entries
    And I have added the feed URL "/owner.xml"
    When I sign in as "alice"
    And the feed server serves "/alice.xml" as an RSS 2.0 feed titled "Alice Feed" with 1 entries
    And I have added the feed URL "/alice.xml"
    And the feed "/owner.xml" now has 2 entries
    And the feed "/alice.xml" now has 2 entries
    And the poller runs
    Then the source "Alice Feed" has 2 stored items
    And the source "Owner Feed" has 2 stored items

  # ---------- Groups ----------

  Scenario: Groups are listed and navigated per user
    Given the group "Music" exists
    When I sign in as "alice"
    And the group "News" exists
    And I request "/sources"
    Then the sources page lists the group "News"
    And the sources page does not list the group "Music"
    When I request the home page "/feed"
    Then the group navigation lists the entries
      | All (0)  |
      | News (0) |

  Scenario: Two users can each have a group of the same name
    Given the group "Music" exists
    When I sign in as "alice"
    And I create the group "music"
    Then the sources page lists the group "music"
    And there is 1 group

  Scenario: A user's group still rejects a duplicate name within their own groups
    Given the group "Music" exists
    When I sign in as "alice"
    And I create the group "Music"
    And I create the group "MUSIC"
    Then the sources page shows the message "a group with that name already exists"
    And there is 1 group

  Scenario: A group's timeline shows only the owner's items
    Given the group "Music" exists
    And the source "Owner Tunes" has an unread item "Owner song" published 1 days ago
    And the source "Owner Tunes" is in the group "Music"
    When I sign in as "alice"
    And the group "Music" exists
    And the source "Alice Tunes" has an unread item "Alice song" published 1 days ago
    And the source "Alice Tunes" is in the group "Music"
    And I request the timeline of the group "Music"
    Then the timeline lists the items in this order
      | Alice song |

  Scenario: Deleting one user's group does not ungroup the other user's sources
    Given the group "Music" exists
    And the source "Owner Tunes" is in the group "Music"
    When I sign in as "alice"
    And the group "Music" exists
    And the source "Alice Tunes" is in the group "Music"
    And I delete the group "Music"
    Then the source "Alice Tunes" is ungrouped
    And the source "Owner Tunes" is in the group "Music"

  Scenario: A source cannot be put in another user's group
    Given the group "Owner Group" exists
    When I sign in as "alice"
    And the source "Alice Blog" has an unread item "Alice post" published 1 days ago
    And I POST "/sources/{id}/group" with the id of the source "Alice Blog" and the group id of "Owner Group"
    Then the response status is 404
    And the source "Alice Blog" is ungrouped

  # ---------- Favourites and Later ----------

  Scenario: Favourites are per user
    Given the source "Owner Blog" has an unread item "Owner post" published 2 days ago
    And I favourite the item "Owner post"
    When I sign in as "alice"
    And the source "Alice Blog" has an unread item "Alice post" published 1 days ago
    And I request the home page "/feed?favourites=1"
    Then the timeline does not list "Owner post"
    When I favourite the item "Alice post"
    And I request the home page "/feed?favourites=1"
    Then the timeline lists the items in this order
      | Alice post |

  Scenario: Favouriting an item does not favourite another user's copy of the same post
    Given the feed server serves "/shared.xml" as an RSS 2.0 feed titled "Shared" with 1 entries
    And I have added the feed URL "/shared.xml"
    When I sign in as "alice"
    And I have added the feed URL "/shared.xml"
    And I favourite the item "Shared 1"
    And I sign in as "owner"
    Then the item "Shared 1" is not a favourite

  Scenario: Read later is per user
    Given the source "Owner Blog" has an unread item "Owner post" published 2 days ago
    And I save the item "Owner post" for later
    When I sign in as "alice"
    And the source "Alice Blog" has an unread item "Alice post" published 1 days ago
    And I request the home page "/feed?later=1"
    Then the timeline does not list "Owner post"
    And the timeline does not list "Alice post"

  Scenario: The Favourites and Later filters combined with a group stay within the user
    Given the group "Music" exists
    And the source "Owner Tunes" has an unread item "Owner song" published 1 days ago
    And the source "Owner Tunes" is in the group "Music"
    And I favourite the item "Owner song"
    And I save the item "Owner song" for later
    When I sign in as "alice"
    And the group "Music" exists
    And I request the timeline of the group "Music" with the Favourites filter
    Then the timeline does not list "Owner song"
    When I request the timeline of the group "Music" with the Later filter
    Then the timeline does not list "Owner song"

  # ---------- Search ----------

  Scenario: Search only returns the signed-in user's items
    Given the source "Owner Blog" has an unread item "Quantum owner post" published 2 days ago
    When I sign in as "alice"
    And the source "Alice Blog" has an unread item "Quantum alice post" published 1 days ago
    And I search for "quantum" via htmx
    Then the timeline lists the items in this order
      | Quantum alice post |
    And the response does not contain "Quantum owner post"

  Scenario: A search that matches only another user's items finds nothing
    Given the source "Owner Blog" has an unread item "Zeppelin post" published 2 days ago
    When I sign in as "alice"
    And I search for "zeppelin" via htmx
    Then the response contains "No matches"

  Scenario: Search matching a source name finds only the user's own sources
    Given the source "Zebra Weekly" has an unread item "Owner post" published 2 days ago
    When I sign in as "alice"
    And the source "Alice Blog" has an unread item "Alice post" published 1 days ago
    And I search for "zebra" via htmx
    Then the response contains "No matches"

  # ---------- Import ----------

  Scenario: Importing a file counts "already subscribed" per user
    Given the feed server serves "/a.xml" as an RSS 2.0 feed titled "A" with 1 entries
    And I have imported the file "feeds.opml" containing the feed server OPML:
      | /a.xml |
    When I sign in as "alice"
    And I import the file "feeds.opml" containing the feed server OPML:
      | /a.xml |
    Then the import summary shows 1 added, 0 already subscribed and 0 failed
    When I sign in as "owner"
    And I import the file "feeds.opml" containing the feed server OPML:
      | /a.xml |
    Then the import summary shows 0 added, 1 already subscribed and 0 failed

  Scenario: Imported sources belong to the importing user only
    Given the feed server serves "/a.xml" as an RSS 2.0 feed titled "A" with 1 entries
    When I sign in as "alice"
    And I have imported the file "feeds.opml" containing the feed server OPML:
      | /a.xml |
    And I sign in as "owner"
    And I request "/sources"
    Then the sources page does not list a source titled "A"

  # ---------- Site icons ----------

  Scenario: A source's site icon belongs to its own owner's source
    Given the source "Shared" has an unread item "Owner post" published 2 days ago
    And the source "Shared" has the favicon "https://example.test/icon.png"
    When I sign in as "alice"
    And the source "Shared" has an unread item "Alice post" published 1 days ago
    And I request the home page "/feed"
    Then the timeline entry "Alice post" shows no site icon
    And the source "Shared" has no stored favicon and was never checked

  # ---------- Another user's ids are 404 ----------

  Scenario Outline: Acting on another user's item is a 404 and changes nothing
    Given the source "Owner Blog" has an unread item "Owner post" published 1 days ago
    When I sign in as "alice"
    And I <method> "<path>" with the id of the item "Owner post"
    Then the response status is 404
    And the item "Owner post" is unread
    And the item "Owner post" is not a favourite
    And the item "Owner post" is not saved for later

    Examples:
      | method | path                    |
      | GET    | /items/{id}/open        |
      | POST   | /items/{id}/read        |
      | GET    | /items/{id}/reader      |
      | GET    | /items/{id}/player      |
      | GET    | /items/{id}/detail      |
      | POST   | /items/{id}/favourite   |
      | POST   | /items/{id}/unfavourite |
      | POST   | /items/{id}/later       |
      | POST   | /items/{id}/unlater     |

  Scenario Outline: Acting on another user's source is a 404 and changes nothing
    Given the source "Owner Blog" has an unread item "Owner post" published 1 days ago
    When I sign in as "alice"
    And I <method> "<path>" with the id of the source "Owner Blog"
    Then the response status is 404
    When I sign in as "owner"
    Then the source "Owner Blog" has 1 stored items
    And the source "Owner Blog" is ungrouped

    Examples:
      | method | path                 |
      | POST   | /sources/{id}/delete |
      | POST   | /sources/{id}/group  |

  Scenario Outline: Acting on another user's group is a 404 and changes nothing
    Given the group "Owner Group" exists
    When I sign in as "alice"
    And I <method> "<path>" with the id of the group "Owner Group"
    Then the response status is 404
    When I sign in as "owner"
    And I request "/sources"
    Then the sources page lists the group "Owner Group"

    Examples:
      | method | path                |
      | POST   | /groups/{id}/rename |
      | POST   | /groups/{id}/delete |
      | GET    | /feed?group={id}    |

  Scenario: Another user's item id answers exactly like an unknown id
    Given the source "Owner Blog" has an unread item "Owner post" published 1 days ago
    When I sign in as "alice"
    And I request the reader of the item "Owner post" via htmx
    Then the response status is 404
    When I request the reader of the unknown item 999999
    Then the response status is 404

  # Non-goals:
  #   - Signup (#44), account pages and user deletion UI (#45); only the
  #     schema-level cascade on owner delete is specified here.
  #   - De-duplicating or sharing fetches of a feed followed by two users.
  #   - Per-user retention or per-user poll schedules.
  #
  # Risks:
  #   - Missed query path: any raw SQL touching items/sources/groups without
  #     the owner filter leaks data. Candidates: JdbcItemRepository,
  #     JdbcSearchRepository (FTS join), JdbcFavouriteRepository,
  #     JdbcLaterRepository, JdbcArticleRepository, JdbcGroupRepository,
  #     JdbcSourceRepository, group unread counts. The 404 outlines are the
  #     net for id-taking routes.
  #   - SQLite table rebuild for sources/groups (V14) must keep AUTOINCREMENT
  #     ids, the group_id FK (ON DELETE SET NULL), the favicon columns, indexes
  #     and the items FK to sources; changing ids would orphan items and the
  #     item_search index.
  #   - Group assignment must check both source and group ownership; the FK
  #     alone does not stop owner A's source pointing at owner B's group.
  #   - Poller and retention are intentionally unscoped: do not "fix" them to
  #     a signed-in user (a background job has none).
  #   - Scenario "Marking an item read ..." relies on the shared feed's
  #     entries having stable titles "Shared 1", "Shared 2" in the fixture.
  #
  # Open questions: none (all auto-decided, see specs/intent/per-user-data.md;
  # the owner-delete cascade is the one product-visible decision).
