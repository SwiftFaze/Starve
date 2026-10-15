Feature: Post bodies stored from feeds and sanitised on ingest
  Each feed entry's body (content:encoded, else the description) is stored on
  its item, already sanitised with jsoup, so that whatever is stored is safe to
  render as HTML. Feed HTML is untrusted; nothing is sanitised at render time.

  Covers: choosing the body (content:encoded first, then the description, else
  none; Atom content/summary likewise via ROME); a nullable body column on
  items; sanitising at ingest by allow-list: removing script, iframe, object,
  embed, form, style and link elements, every on* event-handler attribute, the
  style attribute, and any URL whose scheme is not http, https or (for links
  only) mailto, e.g. javascript: and data:; keeping structural and text markup
  (paragraphs, headings, lists, quotes, code, tables, figures, links, images);
  heading permalink marks removed (a link inside a heading whose whole text is
  "#", "¶", "§" or "🔗", or one with the class "headerlink", as Sphinx and
  similar generators add); links gaining target="_blank" and rel="noopener noreferrer"; relative link
  and image URLs resolved against the item's link (dropped when the item has no
  link); a size cap of 200000 characters on the raw body before sanitising; a
  body being filled in for an already known item only when it had none (as
  thumbnails are), never overwritten; a body that sanitises to nothing being
  stored as no body; the migration adding the column, leaving existing items
  with no body.
  Supersedes: nothing. It extends the item fields ingested in
  feed-ingestion.feature.
  Out of scope: showing the body (reader-pane.feature); fetching the full page
  for teaser feeds; downloading or proxying images; search over bodies;
  extracting an item's own thumbnail from its body; pruning (retention still
  deletes whole items by age, which drops their bodies with them).

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: content:encoded is stored as the body
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <p>Hello <b>world</b></p>
      """
    When I add the feed URL "/blog.xml"
    Then the stored body of the item of "Blog" is "<p>Hello <b>world</b></p>"

  Scenario: The description is the fallback when there is no content:encoded
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the description
      """
      <p>From the description</p>
      """
    When I add the feed URL "/blog.xml"
    Then the stored body of the item of "Blog" is "<p>From the description</p>"

  Scenario: content:encoded wins over the description
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the description "<p>Teaser</p>" and the content:encoded "<p>Full post</p>"
    When I add the feed URL "/blog.xml"
    Then the stored body of the item of "Blog" is "<p>Full post</p>"

  Scenario: An entry with neither has no stored body
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" with 1 entries
    When I add the feed URL "/blog.xml"
    Then the item of "Blog" has no stored body

  Scenario: A body that sanitises to nothing is stored as no body
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <script>alert(1)</script>
      """
    When I add the feed URL "/blog.xml"
    Then the item of "Blog" has no stored body

  Scenario: An Atom entry's content is stored as the body
    Given the feed server serves "/atom.xml" as an Atom feed titled "Atomic" whose entry has the HTML content "<p>Atom body</p>"
    When I add the feed URL "/atom.xml"
    Then the stored body of the item of "Atomic" is "<p>Atom body</p>"

  Scenario: Scripts, iframes and other active elements are removed with their content
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <p>Safe</p><script>alert(1)</script><iframe src="https://evil.example/x"></iframe><object data="x"></object><embed src="x"><form action="/x"><input name="a"></form><style>p{display:none}</style>
      """
    When I add the feed URL "/blog.xml"
    Then the stored body of the item of "Blog" is "<p>Safe</p>"

  Scenario: Event handlers and style attributes are removed but the element stays
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <p onclick="steal()" style="color:red">Text</p><img src="https://img.example/a.png" onerror="steal()" alt="pic">
      """
    When I add the feed URL "/blog.xml"
    Then the stored body of the item of "Blog" contains "<p>Text</p>"
    And the stored body of the item of "Blog" contains no "onclick"
    And the stored body of the item of "Blog" contains no "onerror"
    And the stored body of the item of "Blog" contains no "style"
    And the stored body of the item of "Blog" contains an image from "https://img.example/a.png"

  Scenario: Unsafe URL schemes are removed from links and images
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <a href="javascript:alert(1)">bad</a> <a href="JaVaScRiPt:alert(1)">mixed</a> <a href="data:text/html,x">data</a> <img src="javascript:alert(1)"> <img src="data:image/png;base64,AAAA"> <a href="mailto:me@example.com">mail</a>
      """
    When I add the feed URL "/blog.xml"
    Then the stored body of the item of "Blog" contains no "javascript:"
    And the stored body of the item of "Blog" contains no "data:"
    And the stored body of the item of "Blog" contains the link "mailto:me@example.com"
    And the stored body of the item of "Blog" contains the text "bad"

  Scenario: Heading permalink marks are removed and the heading stays
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <h2>Workshop<a class="headerlink" href="#workshop" title="Link to this heading">#</a></h2><h3>Notes<a href="#notes">¶</a></h3><p>Text with <a href="#top">a link</a></p>
      """
    When I add the feed URL "/blog.xml"
    Then the stored body of the item of "Blog" contains "<h2>Workshop</h2>"
    And the stored body of the item of "Blog" contains "<h3>Notes</h3>"
    And the stored body of the item of "Blog" contains the text "a link"
    And the stored body of the item of "Blog" contains no "¶"

  Scenario: Links open externally without handing over the opener
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <p><a href="https://other.example/page" target="_self" rel="opener">Read</a></p>
      """
    When I add the feed URL "/blog.xml"
    Then every link in the stored body of the item of "Blog" has target "_blank" and rel "noopener noreferrer"

  Scenario: Relative links and images resolve against the item's link
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <p><a href="/about">About</a> <a href="more.html">More</a> <img src="/images/a.png"></p>
      """
    When I add the feed URL "/blog.xml"
    Then the stored body of the item of "Blog" contains the link "<feed server>/about"
    And the stored body of the item of "Blog" contains the link "<feed server>/blog.xml/more.html"
    And the stored body of the item of "Blog" contains an image from "<feed server>/images/a.png"

  Scenario: Sanitising is idempotent, so a stored body is already clean
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <p onclick="x()">Hi <a href="https://a.example/">there</a></p><script>x()</script>
      """
    When I add the feed URL "/blog.xml"
    Then sanitising the stored body of the item of "Blog" again leaves it unchanged

  Scenario: An oversized body is capped before it is stored
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has a content:encoded paragraph of 500000 characters
    When I add the feed URL "/blog.xml"
    Then the stored body of the item of "Blog" is at most 200000 characters long
    And sanitising the stored body of the item of "Blog" again leaves it unchanged

  Scenario: A refetch fills in a missing body but never overwrites a stored one
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded "<p>First</p>"
    And I have added the feed URL "/blog.xml"
    And the stored body of the item of "Blog" is removed
    When the feed server now serves "/blog.xml" with the content:encoded "<p>Second</p>"
    And the poller runs
    Then the stored body of the item of "Blog" is "<p>Second</p>"
    When the feed server now serves "/blog.xml" with the content:encoded "<p>Third</p>"
    And the poller runs
    Then the stored body of the item of "Blog" is "<p>Second</p>"

  Scenario: Items stored before the upgrade have no body
    Given the database is migrated only up to before post bodies
    And it holds a source "Blog" with a stored item titled "Old post"
    When the application is running
    Then the item "Old post" of "Blog" has no stored body
    And the source "Blog" has a stored item titled "Old post"

# Non-goals:
#   - Rewriting or proxying images; they load straight from their own hosts.
#   - Re-sanitising at render time; the stored body is the single clean copy.
#
# Risks:
#   - The allow-list is the security boundary. The scenarios pin the known
#     vectors (script/iframe/handlers/javascript:/data:/style); the hardener
#     should add unit tests for further jsoup edge cases (nested/malformed
#     markup, mixed-case schemes, SVG, <base>, <meta refresh>).
#   - A not-modified fetch (HTTP 304) never revisits a feed, so old items gain
#     a body only when the feed changes or its validators stop matching.
#
# Open questions:
#   - None.
