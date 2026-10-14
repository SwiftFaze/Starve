Feature: Full article text and og:image fetched on open for teaser-only posts
  When a blog post is opened in the reader and its stored body is missing or
  only a teaser, Starve fetches the article page once, extracts the main text,
  sanitises it like any feed body and stores it as the body. The same fetch
  gives the item the page's og:image when it has no thumbnail.

  Covers: fetching only when the reader opens an item with a link that is not
  a YouTube video or SoundCloud track and whose stored body is absent or has
  under 500 characters of plain text; never fetching an item whose body is
  already that long, and never on a poll, the home page or the timeline;
  extracting the main text with jsoup (the <article> element, else <main>,
  else the block with the most paragraph text, without nav, header, footer,
  aside, form, script and style) and storing it through the same sanitiser as
  feed bodies (relative links and images resolved against the item's link);
  the extracted text replacing the body only when its plain text is longer
  than the teaser's, else the teaser is kept and the reader shows it with
  "Open original"; storing the result so the next open does not fetch;
  the page's og:image (an http or https URL, relative resolved against the
  page) becoming the item's thumbnail only when it has none, even when text
  extraction fails, so it ranks after feed and body images and ahead of the
  source favicon; bounded fetching: a 5 second timeout, a 1 MB size cap, http
  and https links only, HTML responses only, any failure (timeout, error
  status, too large, not HTML, unparseable) leaving the teaser and Open
  original; every attempt being recorded in a nullable article_checked_at
  column on items (migration V11) and an item being fetched again only when
  its last attempt is over 24 hours old; opening still marking the item read
  whatever the fetch did; the migration leaving existing items unchecked;
  after a teaser post's article is fetched, a search for a word only in the
  article finds the post.
  Supersedes: in part reader-pane.feature, whose no-body state for an item
  with a link now applies only once a fetch has been tried and given nothing.
  post-bodies.feature (bodies stay never overwritten at ingest) and
  post-images.feature (feed images keep their order; the favicon stays the
  render-time fallback) still hold.
  Out of scope: fetching at ingest; site-specific extractors; paywalled or
  login-only pages; JavaScript-rendered pages; downloading or proxying images;
  playable items; how the extracted article looks: the human playtest.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A post with no body is fetched and its extracted article is shown and stored
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text
    When I request the reader of the item "Post" via htmx
    Then the response status is 200
    And the reader body contains the article text
    And the stored body of the item "Post" contains the article text

  Scenario: A teaser body is replaced by the longer extracted article
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and the stored body "<p>Just a teaser.</p>" published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text
    When I request the reader of the item "Post" via htmx
    Then the reader body contains the article text
    And the reader body does not contain "Just a teaser."

  Scenario: The second open does not fetch the page again
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text
    When I request the reader of the item "Post" via htmx
    And I request the reader of the item "Post" via htmx
    Then the feed server received 1 requests for "/posts/one"
    And the reader body contains the article text

  Scenario: An item with a full-length body is never fetched
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and a stored body of 800 characters of text published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 900 characters of text
    When I request the reader of the item "Post" via htmx
    Then the feed server received 0 requests for "/posts/one"
    And the reader body is the stored body

  Scenario: Polling and the home page never fetch article pages
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the link "<feed server>/posts/one" and the description "<p>Teaser</p>"
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text
    When I add the feed URL "/blog.xml"
    And I request the home page "/"
    Then the feed server received 0 requests for "/posts/one"

  Scenario Outline: Playable items and items without a link are never fetched
    Given the source "Media" has an item "Thing" with <link> and no stored body published 1 days ago
    When I request the reader of the item "Thing" via htmx
    Then no article page is requested
    And the response status is <status>

    Examples:
      | link                                                   | status |
      | the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" | 404    |
      | the link "https://soundcloud.com/artist/track"         | 404    |
      | no link                                                | 200    |

  Scenario: The extracted article is sanitised like a feed body
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the feed server serves "/posts/one" as an HTML page whose article contains 800 characters of text, a script element, an onclick attribute, a javascript: link and a relative link "/about" and image "pic.png"
    When I request the reader of the item "Post" via htmx
    Then the reader body has no script element
    And the reader body has no onclick attribute
    And the reader body has no javascript: URL
    And the reader body links to "<feed server>/about" opening in a new tab with rel "noopener noreferrer"
    And the reader body shows the image "<feed server>/posts/pic.png"

  Scenario: Page chrome outside the article is not extracted
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with a nav reading "Site navigation", an article of 800 characters of text and a footer reading "Copyright footer"
    When I request the reader of the item "Post" via htmx
    Then the reader body contains the article text
    And the reader body does not contain "Site navigation"
    And the reader body does not contain "Copyright footer"

  Scenario: A page without an article element uses its main text block
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with 800 characters of paragraph text in a plain div among short link lists
    When I request the reader of the item "Post" via htmx
    Then the reader body contains the paragraph text

  Scenario: An extraction no longer than the teaser keeps the teaser and Open original
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and a stored body of 300 characters of text published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 100 characters of text
    When I request the reader of the item "Post" via htmx
    Then the reader body is the stored body
    And the stored body of the item "Post" is unchanged
    And the reader has an "Open original" link to "<feed server>/posts/one"

  Scenario: A page that yields no text keeps the no-body state
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an empty body
    When I request the reader of the item "Post" via htmx
    Then the response status is 200
    And the reader has no body
    And the reader has an "Open original" link to "<feed server>/posts/one"

  Scenario Outline: A failing fetch leaves the teaser and Open original
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and the stored body "<p>Just a teaser.</p>" published 1 days ago
    And the feed server <failure> for "/posts/one"
    When I request the reader of the item "Post" via htmx
    Then the response status is 200
    And the reader body contains "Just a teaser."
    And the reader has an "Open original" link to "<feed server>/posts/one"
    And the stored body of the item "Post" is unchanged
    And the item "Post" is read

    Examples:
      | failure                  |
      | answers 404              |
      | answers 500              |
      | does not respond in time |
      | serves a 2 MB HTML page  |
      | serves a PDF             |

  Scenario: A slow page does not hold the reader beyond the timeout
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the feed server does not respond in time for "/posts/one"
    When I request the reader of the item "Post" via htmx
    Then the response status is 200
    And the reader answered within 8 seconds

  Scenario: A slow page does not block the timeline
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the source "Blog" has an unread item "Other" published 2 days ago
    And the feed server does not respond in time for "/posts/one"
    When I request the reader of the item "Post" via htmx and, meanwhile, the home page "/"
    Then the home page answers without waiting for the reader
    And the timeline lists "Other"

  Scenario: A failed attempt is not repeated on the next open
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the feed server answers 500 for "/posts/one"
    When I request the reader of the item "Post" via htmx
    And I request the reader of the item "Post" via htmx
    Then the feed server received 1 requests for "/posts/one"

  Scenario: A failed attempt is retried once it is over 24 hours old
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the article of "Post" was last checked 25 hours ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text
    When I request the reader of the item "Post" via htmx
    Then the reader body contains the article text

  Scenario: A failed attempt under 24 hours old is not retried
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the article of "Post" was last checked 23 hours ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text
    When I request the reader of the item "Post" via htmx
    Then the feed server received 0 requests for "/posts/one"
    And the reader has no body

  Scenario: The page's og:image becomes the thumbnail of an item without one
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body and no thumbnail published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text and the og:image "https://img.example/og.jpg"
    When I request the reader of the item "Post" via htmx
    Then the stored thumbnail of the item "Post" is "https://img.example/og.jpg"

  Scenario: A relative og:image resolves against the page
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body and no thumbnail published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text and the og:image "/img/og.jpg"
    When I request the reader of the item "Post" via htmx
    Then the stored thumbnail of the item "Post" is "<feed server>/img/og.jpg"

  Scenario: The og:image is taken even when the article text cannot be extracted
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body and no thumbnail published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an empty body and the og:image "https://img.example/og.jpg"
    When I request the reader of the item "Post" via htmx
    Then the stored thumbnail of the item "Post" is "https://img.example/og.jpg"
    And the reader has no body

  Scenario: The og:image never replaces a feed image
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body and the thumbnail "https://img.example/feed.jpg" published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text and the og:image "https://img.example/og.jpg"
    When I request the reader of the item "Post" via htmx
    Then the stored thumbnail of the item "Post" is "https://img.example/feed.jpg"

  Scenario Outline: An og:image that is not http or https is ignored
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body and no thumbnail published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text and the og:image "<url>"
    When I request the reader of the item "Post" via htmx
    Then the item "Post" has no stored thumbnail

    Examples:
      | url                        |
      | javascript:alert(1)        |
      | data:image/png;base64,AAAA |

  Scenario: The og:image ranks ahead of the source favicon
    Given the source "Blog" has the favicon "https://cdn.example/icon.png"
    And the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body and no thumbnail published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text and the og:image "https://img.example/og.jpg"
    When I request the reader of the item "Post" via htmx
    And I request the home page "/"
    Then the timeline shows "https://img.example/og.jpg" as the thumbnail of "Post"
    And the timeline shows no site icon for "Post"

  Scenario: Without an og:image the favicon fallback still applies
    Given the source "Blog" has the favicon "https://cdn.example/icon.png"
    And the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and no stored body and no thumbnail published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text
    When I request the reader of the item "Post" via htmx
    And I request the home page "/"
    Then the timeline shows the favicon "https://cdn.example/icon.png" as the site icon of "Post"

  Scenario: An item with a full body gets no og:image, as its page is not fetched
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and a stored body of 800 characters of text and no thumbnail published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 900 characters of text and the og:image "https://img.example/og.jpg"
    When I request the reader of the item "Post" via htmx
    Then the item "Post" has no stored thumbnail

  Scenario: Opening marks the item read even when the fetch fails
    Given the source "Blog" has an unread item "Post" with the link "<feed server>/posts/one" and no stored body published 1 days ago
    And the feed server answers 500 for "/posts/one"
    When I request the reader of the item "Post" via htmx
    Then the item "Post" is read

  Scenario: A fetched article becomes searchable
    Given the source "Blog" has an item "Post" with the link "<feed server>/posts/one" and the stored body "<p>Just a teaser.</p>" published 1 days ago
    And the feed server serves "/posts/one" as an HTML page with an article of 800 characters of text
    When I search for "lorem"
    Then the timeline lists no items
    When I request the reader of the item "Post" via htmx
    And I search for "lorem"
    Then the timeline lists "Post"

  Scenario: Items stored before the upgrade are unchecked and keep their data
    Given the database is migrated only up to before article fetching
    And it holds a source "Blog" with a stored item titled "Old post"
    When the application is running
    Then the item "Old post" has no recorded article check
    And the source "Blog" has a stored item titled "Old post"

# Non-goals:
#   - Fetching at ingest, JavaScript-rendered pages, paywalls, site-specific
#     extractors, proxying images.
#
# Risks:
#   - New step vocabulary: HTML pages with an article / nav / footer / og:image,
#     a 2 MB page, a PDF, per-path status and silence, request counting per
#     path, a stored body of N characters, items with no thumbnail, setting
#     article_checked_at, a concurrent reader + home request, response timing.
#   - Extraction quality on real pages (the "most paragraph text" heuristic) is
#     a human-playtest question; the scenarios pin only the clear cases.
#   - The first open of a teaser post waits up to the 5 second timeout; the
#     reader answer-time scenario allows 8 seconds, and the concurrent-request
#     scenario needs the fetch not to hold a shared lock.
#   - A very short but genuine post (under 500 characters) is fetched once and
#     then kept unless the page text is longer.
#   - "Under 500 characters" and "24 hours" are auto-decided numbers.
#
# Open questions:
#   - None (fetch on open, the 500 character teaser threshold, the 24 hour
#     retry and the og:image-only-on-fetch rule are recorded as auto-decided
#     in the intent doc).
