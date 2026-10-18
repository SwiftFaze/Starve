Feature: Items without a title are titled from their text
  Bluesky and Mastodon items have no title, only the post's text, so they were
  all stored as "(untitled)" and could not be told apart on the timeline or in
  the reader. An item whose title is missing or blank now takes the start of
  its body's plain text as its title.

  Covers: at ingestion, for an item of any feed whose title is absent or
  decodes to blank, the title taken from its sanitised body's plain text,
  with placeholders in square brackets (such as "[contains quote post or
  other embedded content]") left out, spaces collapsed, cut at the last word
  boundary within 64 characters with an ellipsis (…) when cut, and trailing
  commas, semicolons or colons dropped before the ellipsis; an item with no
  text either staying "(untitled)"; items with a real title unchanged;
  migration V21 re-titling items already stored as "(untitled)" from their
  stored body, in items and in the search index (pinned by
  DeriveItemTitlesMigrationTest and ItemTitlesTest).
  Supersedes: in part title-entities.feature and feed-ingestion.feature,
  where a missing item title was always "(untitled)".
  Out of scope: the reader layout of Bluesky posts; source (feed) titles.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A post without a title is titled from the start of its text
    Given the feed server serves "/sky.xml" as an RSS 2.0 feed titled "Sky" whose item has no title and the description "<p>Happy opening day of hockey season, NHL fans! Keep up with all 1,344 regular season games right here.</p>"
    When I add the feed URL "/sky.xml"
    Then the source "Sky" has a stored item titled "Happy opening day of hockey season, NHL fans! Keep up with all…"
    And the timeline shows an item titled "Happy opening day of hockey season, NHL fans! Keep up with all…"

  Scenario: A short post is its own title, without its placeholders
    Given the feed server serves "/sky.xml" as an RSS 2.0 feed titled "Sky" whose item has no title and the description "<p>Look at this <b>one</b> [contains quote post or other embedded content]</p>"
    When I add the feed URL "/sky.xml"
    Then the source "Sky" has a stored item titled "Look at this one"

  Scenario: A derived title is searchable
    Given the feed server serves "/sky.xml" as an RSS 2.0 feed titled "Sky" whose item has no title and the description "<p>Puffins nesting on the cliffs</p>"
    When I add the feed URL "/sky.xml"
    And I search for "puffins"
    Then the timeline lists "Puffins nesting on the cliffs"

  Scenario: A post with neither a title nor text stays untitled
    Given the feed server serves "/sky.xml" as an RSS 2.0 feed titled "Sky" whose item has no title and the description "<p>[contains quote post or other embedded content]</p>"
    When I add the feed URL "/sky.xml"
    Then the source "Sky" has a stored item titled "(untitled)"

  Scenario: A post with a title keeps it
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose item title in the XML is "A real title"
    When I add the feed URL "/blog.xml"
    Then the source "Blog" has a stored item titled "A real title"

# Non-goals:
#   - Deriving source titles; changing titles that exist.
#
# Risks:
#   - Square-bracketed text that is not a placeholder ("[Video]") is left out
#     of a derived title too.
#   - Items that left their feed before V21 still get re-titled, from their
#     stored body.
#
# Open questions:
#   - None (64 characters and the bracket rule are recorded as auto-decided in
#     the intent doc).
