Feature: Item title entities
  Item titles hold real characters, not HTML character references, even when
  a feed double-escapes them (e.g. "There&amp;#8217;s" in the XML).

  Covers: single-pass decoding of decimal, hex and named HTML character
  references in item titles at ingestion; leaving bare ampersands and unknown
  references unchanged; a one-off migration that decodes item titles stored
  before this fix.
  Supersedes: nothing.
  Out of scope: rendering HTML markup in titles; source (feed) titles; authors
  and other item fields.

  Background:
    Given a fresh SQLite database file
    And a feed server is running

  Scenario: Character references in an item title are decoded
    Given the application is running
    And the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose item title in the XML is "There&amp;#8217;s &amp;#x2014; Rock &amp;amp; Roll"
    When I add the feed URL "/blog.xml"
    Then the source "Blog" has a stored item titled "There’s — Rock & Roll"
    And the timeline shows an item titled "There’s — Rock & Roll"

  Scenario: Bare ampersands and unknown references are kept as they are
    Given the application is running
    And the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose item title in the XML is "Tom &amp; Jerry &amp;notareal;"
    When I add the feed URL "/blog.xml"
    Then the source "Blog" has a stored item titled "Tom & Jerry &notareal;"

  Scenario: A title is decoded only once
    Given the application is running
    And the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose item title in the XML is "Fish &amp;amp;amp; Chips"
    When I add the feed URL "/blog.xml"
    Then the source "Blog" has a stored item titled "Fish &amp; Chips"

  Scenario: Titles stored before the fix are decoded on upgrade
    Given the database is migrated only up to before the title fix
    And it holds a source "Blog" with stored items titled "There&#8217;s more" and "Tom & Jerry"
    When the application is running
    Then the source "Blog" has a stored item titled "There’s more"
    And the source "Blog" has a stored item titled "Tom & Jerry"
