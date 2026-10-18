Feature: Keyword tags
  A tag is a saved keyword view across sources: its terms are matched against
  item titles (and bodies, if asked) at query time, so a topic such as AI that
  cuts across broad sources gets its own view. Tags add a view; they never
  move an item out of its source's group.

  Covers: creating, editing and deleting tags in the Tags section of
  "/sources" (name trimmed, 1-50 characters, unique ignoring case among the
  user's tags, may equal a group's name); terms entered comma-separated, each
  one or more words of letters or digits (several words are a phrase) with an
  optional trailing * as a prefix wildcard, OR'd together, matched as whole
  words through the item_search FTS5 index; at least 1 and at most 20 terms,
  anything else rejected with a reason when saving; "Match the body too"
  (off by default: titles only); "Only within group" limiting the tag to one
  group's sources; the navigation listing tags after the groups,
  alphabetically, as "#<name> [<unread>]"; the tag view "/feed?tag=<id>" with
  the timeline's order, paging, Show read and Mark all read; reading an item
  in any view marking it read in every view; editing a tag changing its view
  at once, stored items included; deleting a tag (after a confirmation) or
  its limiting group deleting no items, a deleted limiting group removing the
  limit; 404 for an unknown or another user's tag; a tags table (V20).
  Supersedes: nothing. source-groups.feature still holds: groups stay
  source-based, and the navigation still starts with All and the groups.
  Out of scope: hiding or muting items by keyword; tagging items by hand;
  counting a post under a tag instead of its group.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A tag lists every source's items whose title has one of its terms
    Given the source "Tech One" has an unread item "OpenAI ships GPT-6" published 3 days ago
    And the source "Tech Two" has an unread item "The LLM race" published 2 days ago
    And the source "Tech Two" has an unread item "New phones" published 1 days ago
    When I create the tag "AI" with the terms "AI, LLM, OpenAI"
    And I request the timeline of the tag "AI"
    Then the response status is 200
    And the timeline lists the items in this order
      | The LLM race      |
      | OpenAI ships GPT-6 |

  Scenario: Terms match whole words only
    Given the source "Tech" has an unread item "Said the minister" published 3 days ago
    And the source "Tech" has an unread item "Thailand travel" published 2 days ago
    And the source "Tech" has an unread item "AI in schools" published 1 days ago
    When I create the tag "AI" with the terms "AI"
    And I request the timeline of the tag "AI"
    Then the timeline lists the items in this order
      | AI in schools |

  Scenario: A term ending in * matches words that start with it, and a phrase matches its words in order
    Given the source "Tech" has an unread item "GPT4o launches" published 3 days ago
    And the source "Tech" has an unread item "Machine learning basics" published 2 days ago
    And the source "Tech" has an unread item "Learning machine tools" published 1 days ago
    When I create the tag "Models" with the terms "GPT*, machine learning"
    And I request the timeline of the tag "Models"
    Then the timeline lists the items in this order
      | Machine learning basics |
      | GPT4o launches          |

  Scenario: A tag limited to a group leaves out other groups' items
    Given the group "Tech" exists
    And the source "Tech Blog" has an unread item "AI at work" published 2 days ago
    And the source "Music Blog" has an unread item "AI makes music" published 1 days ago
    And the source "Tech Blog" is in the group "Tech"
    When I create the tag "AI" with the terms "AI" limited to the group "Tech"
    And I request the timeline of the tag "AI"
    Then the timeline lists the items in this order
      | AI at work |

  Scenario: Only titles match unless the tag matches the body too
    Given the source "Tech" has an item "Weekly notes" with the stored body "<p>This week: AI everywhere.</p>" published 1 days ago
    When I create the tag "AI" with the terms "AI"
    And I request the timeline of the tag "AI"
    Then the timeline lists no items
    When I create the tag "AI anywhere" with the terms "AI" matching the body too
    And I request the timeline of the tag "AI anywhere"
    Then the timeline lists "Weekly notes"

  Scenario: Tags follow the groups in the navigation with their unread counts
    Given the group "Tech" exists
    And the source "Tech Blog" has an unread item "AI at work" published 3 days ago
    And the source "Tech Blog" has a read item "AI last week" published 4 days ago
    And the source "Tech Blog" has an unread item "Rust 2.0" published 2 days ago
    And the source "Tech Blog" is in the group "Tech"
    When I create the tag "Rust" with the terms "rust"
    And I create the tag "ai" with the terms "AI"
    And I request the home page "/feed"
    Then the group navigation lists the entries
      | All [2]   |
      | Tech [2]  |
      | #ai [1]   |
      | #Rust [1] |

  Scenario: The current tag is marked in the navigation and names the series
    Given the source "Tech" has an unread item "AI at work" published 1 days ago
    When I create the tag "AI" with the terms "AI"
    And I request the timeline of the tag "AI"
    Then the group navigation marks "#AI [1]" as current
    And the page contains "#AI"

  Scenario: Reading an item in a tag marks it read in its group too
    Given the group "Tech" exists
    And the source "Tech Blog" has an unread item "OpenAI ships GPT-6" published 2 days ago
    And the source "Tech Blog" has an unread item "Rust 2.0" published 1 days ago
    And the source "Tech Blog" is in the group "Tech"
    When I create the tag "AI" with the terms "OpenAI"
    And I request the timeline of the tag "AI"
    And I open the item "OpenAI ships GPT-6"
    And I request the home page "/feed"
    Then the group navigation lists the entries
      | All [1]  |
      | Tech [1] |
      | #AI [0]  |
    When I request the timeline of the group "Tech"
    Then the timeline does not list "OpenAI ships GPT-6"

  Scenario: Editing a tag's terms changes its view at once, stored items included
    Given the source "Tech" has an unread item "AI at work" published 2 days ago
    And the source "Tech" has an unread item "The LLM race" published 1 days ago
    When I create the tag "AI" with the terms "AI"
    And I edit the tag "AI" to the terms "LLM"
    And I request the timeline of the tag "AI"
    Then the timeline lists the items in this order
      | The LLM race |
    And the sources page lists the tag "AI" with the terms "LLM"

  Scenario: Mark all read in a tag marks only its items, and only those already shown
    Given the source "Tech" has an unread item "AI at work" published 3 days ago
    And the source "Tech" has an unread item "Rust 2.0" published 2 days ago
    When I create the tag "AI" with the terms "AI"
    And I request the timeline of the tag "AI"
    And the source "Tech" receives a new unread item "AI arrives late" after that page was rendered
    And I mark all read from the page I received
    Then the item "AI at work" is read
    And the item "Rust 2.0" is unread
    And the item "AI arrives late" is unread

  Scenario: A tag's view respects Show read
    Given the source "Tech" has an unread item "AI now" published 1 days ago
    And the source "Tech" has a read item "AI before" published 2 days ago
    When I create the tag "AI" with the terms "AI"
    And I request the timeline of the tag "AI"
    Then the timeline lists the items in this order
      | AI now |
    When I request the timeline of the tag "AI" showing read items
    Then the timeline lists the items in this order
      | AI now    |
      | AI before |

  Scenario: Load more keeps the tag
    Given the source "Tech" has 60 unread items published at one-minute intervals all titled with "AI"
    And the source "Other" has an unread item "Unrelated" published 1 days ago
    When I create the tag "AI" with the terms "AI"
    And I request the timeline of the tag "AI"
    And I load more
    Then the timeline lists every item exactly once, newest first
    And the timeline does not list "Unrelated"

  Scenario: Deleting a tag asks first and keeps every item
    Given the source "Tech" has an unread item "AI at work" published 1 days ago
    When I create the tag "AI" with the terms "AI"
    And I ask to delete the tag "AI"
    Then the confirmation is titled "Delete tag"
    When I delete the tag "AI"
    Then the sources page does not list the tag "AI"
    And there are 0 tags
    When I request the home page "/feed"
    Then the timeline lists "AI at work"
    And the group navigation lists the entries
      | All [1] |

  Scenario: Deleting a tag's limiting group removes the limit
    Given the group "Tech" exists
    And the source "Tech Blog" has an unread item "AI at work" published 2 days ago
    And the source "Music Blog" has an unread item "AI makes music" published 1 days ago
    And the source "Tech Blog" is in the group "Tech"
    When I create the tag "AI" with the terms "AI" limited to the group "Tech"
    And I delete the group "Tech"
    And I request the timeline of the tag "AI"
    Then the timeline lists the items in this order
      | AI makes music |
      | AI at work     |

  Scenario: A tag may share a group's name
    Given the group "AI" exists
    When I create the tag "AI" with the terms "AI"
    Then the sources page lists the tag "AI" with the terms "AI"

  Scenario Outline: Invalid tags are rejected with a reason
    Given the tag "AI" exists with the terms "AI"
    When I create the tag "<name>" with the terms '<terms>'
    Then the sources page shows the message '<message>'
    And there is 1 tag

    Examples:
      | name                                                | terms        | message                                                                         |
      |                                                     | AI           | tag name is required                                                            |
      | ai                                                  | LLM          | a tag with that name already exists                                             |
      | 012345678901234567890123456789012345678901234567890 | AI           | at most 50 characters                                                           |
      | Code                                                | ,  ,         | at least one term is required                                                   |
      | Code                                                | Rust, C++    | "C++" is not a valid term: use letters, digits and spaces, with * only at the end |
      | Code                                                | "rust"       | ""rust"" is not a valid term: use letters, digits and spaces, with * only at the end |
      | Code                                                | *            | "*" is not a valid term: use letters, digits and spaces, with * only at the end |

  Scenario: A tag has at most 20 terms
    When I create the tag "Many" with the terms "a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21"
    Then the sources page shows the message "at most 20 terms"
    And there are 0 tags

  Scenario: A rejected new tag keeps what was typed
    Given the tag "AI" exists with the terms "AI"
    When I create the tag "ai" with the terms "LLM, GPT*"
    Then the new tag form holds the name "ai" and the terms "LLM, GPT*"

  Scenario Outline: An unknown or another user's tag is not found
    Given the user "alice" exists
    And the tag "Owner tag" exists with the terms "AI"
    When I sign in as "alice"
    And I <method> "<path>" with the id of the tag "Owner tag"
    Then the response status is 404

    Examples:
      | method | path                |
      | GET    | /feed?tag={id}      |
      | POST   | /tags/{id}          |
      | POST   | /tags/{id}/delete   |

  Scenario: An unknown tag id is not found
    When I request the home page "/feed?tag=999"
    Then the response status is 404

# Non-goals:
#   - Muting, manual tagging, per-item tag storage.
#
# Risks:
#   - FTS5 operators typed as words ("rust NOT go") are safe: every term is
#     quoted as a phrase, so NOT is just a word.
#   - "GPT-4" is rejected (the hyphen); "GPT 4" is the phrase that matches it.
#   - Nav counts run one FTS count per tag; fine for a personal tag list.
#
# Open questions:
#   - None (term syntax, name rules, delete confirmation, tag-wins-over-group
#     and the edit form are recorded as auto-decided in the intent doc).
