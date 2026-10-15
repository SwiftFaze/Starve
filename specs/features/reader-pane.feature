Feature: Reader pane for blog posts
  Clicking a blog post's title shows the post in a reader in the pane beside
  the list, instead of sending the browser to the external site. The list stays
  visible, the post is marked read, and anything already playing keeps playing.

  Covers: which items open in the reader (an item whose link is not a YouTube
  video or SoundCloud track, including an item with no link); the title link
  of such an item requesting GET /items/{id}/reader via htmx into the reader
  pane while its plain href stays /items/{id}/open (the no-JS path, unchanged);
  the reader showing the item's title, source, published date and stored body
  (the body inserted as the stored, already sanitised HTML; title, source and
  date escaped as text); an "Open original" link to the item's link opening in
  a new tab with rel="noopener noreferrer"; opening in the reader marking the
  item read; the no-body state (title, source, date and "Open original"
  only, plus an explanatory line); a link to the source's own RSS/Atom feed,
  opening in a new tab; a Close control; the reader not asking a
  video in the pane to step down when a post opens (it keeps its placement and
  is not moved elsewhere); 404 for an unknown item id and for a playable item.
  Supersedes: in part chronological-feed.feature, where a title click always
  goes to /items/{id}/open and redirects. That now holds only without
  JavaScript and for playable items; the endpoint itself is unchanged.
  Supersedes: "reading article text in the pane" being out of scope in
  player-deck.feature; the pane now serves the reader too.
  Out of scope: storing and sanitising bodies (post-bodies.feature); what the
  browser does with the placements (the list staying put, the reader scrolling
  on its own, the list and video splitters and their remembered sizes, the pane
  video staying above the post, closing): browser behaviour covered by the
  human playtest; the detail view for YouTube/SoundCloud items (#30), whose titles
  request their detail view instead (media-detail-view.feature); full-text fetch for teaser feeds; images in posts
  beyond loading them; search over bodies; keyboard shortcuts.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A blog post's title opens the reader via htmx and keeps a plain link for no-JS
    Given the source "Blog" has an unread item "Post" with the link "https://blog.example/post" published 1 days ago
    When I request the home page "/feed"
    Then the title of "Post" requests its reader into the reader pane via htmx
    And the title of "Post" still links to its open endpoint without JavaScript

  Scenario: The home page has an empty reader pane beside the list
    Given the source "Blog" has an unread item "Post" published 1 days ago
    When I request the home page "/feed"
    Then the page has a reader pane
    And the reader pane is empty

  Scenario: The reader shows the post's title, source, date and body
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hello <b>world</b></p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the response status is 200
    And the reader shows the title "Post"
    And the reader shows the source "Blog"
    And the reader shows a published date
    And the reader body contains "<p>Hello <b>world</b></p>"

  Scenario: The stored body is inserted as HTML, but title and source are escaped
    Given the source "Blog <i>One</i>" has an item "A <u>title</u>" with the link "https://blog.example/post" and the stored body "<p>Real <em>markup</em></p>" published 1 days ago
    When I request the reader of the item "A <u>title</u>" via htmx
    Then the reader body contains "<p>Real <em>markup</em></p>"
    And the response contains the text "A <u>title</u>" escaped
    And the response contains the text "Blog <i>One</i>" escaped

  Scenario: Opening the reader marks the post read
    Given the source "Blog" has an unread item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the item "Post" is read
    When I request the home page "/feed"
    Then the timeline does not list "Post"

  Scenario: The reader offers Open original, which opens externally
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader has an "Open original" link to "https://blog.example/post"
    And the "Open original" link opens in a new tab with rel "noopener noreferrer"

  Scenario: The reader links to the source's own feed
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader links to the feed of the source "Blog", opening in a new tab

  Scenario: The reader has a Close control
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader has a Close control

  Scenario: A post without a body shows its title and Open original
    Given the source "Blog" has an item "Teaser" with the link "https://blog.example/teaser" and no stored body published 1 days ago
    When I request the reader of the item "Teaser" via htmx
    Then the response status is 200
    And the reader shows the title "Teaser"
    And the reader has no body
    And the reader says the post has no text to show here
    And the reader has an "Open original" link to "https://blog.example/teaser"
    And the item "Teaser" is read

  Scenario: A post ingested before the upgrade shows the no-body state until its feed is refetched
    Given the database is migrated only up to before post bodies
    And it holds a source "Blog" with a stored item titled "Old post"
    And the application is running
    When I request the reader of the item "Old post" via htmx
    Then the reader has no body
    And the reader says the post has no text to show here

  Scenario: A post with no link and no body has nothing to open
    Given the source "Blog" has an item "Linkless" with no link and no stored body published 1 days ago
    When I request the reader of the item "Linkless" via htmx
    Then the reader shows the title "Linkless"
    And the reader has no "Open original" link

  Scenario: Opening a post does not move a video out of the pane
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader does not ask a video in the pane to step down

  Scenario: Switching to another post does not move a video out of the pane
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    And the source "Blog" has an item "Second" with the link "https://blog.example/second" and the stored body "<p>Two</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    And I request the reader of the item "Second" via htmx
    Then the reader does not ask a video in the pane to step down

  Scenario: A YouTube video's title requests its detail view, not the reader
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the home page "/feed"
    Then the title of "Clip" does not request a reader
    And the title of "Clip" requests its detail view into the reader pane via htmx
    And the title of "Clip" still links to its open endpoint without JavaScript
    When I request the reader of the item "Clip" via htmx
    Then the response status is 404

  Scenario: A SoundCloud track's title requests its detail view, not the reader
    Given the source "Cloud" has an unread item "Track" with the link "https://soundcloud.com/artist/track" published 1 days ago
    When I request the home page "/feed"
    Then the title of "Track" does not request a reader
    And the title of "Track" requests its detail view into the reader pane via htmx
    When I request the reader of the item "Track" via htmx
    Then the response status is 404

  Scenario: Without JavaScript the title still opens the original and marks read
    Given the source "Blog" has an unread item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I open the item "Post"
    Then the response status is 302
    And the response redirects to the link of "Post"
    And the item "Post" is read

  Scenario: An unknown item has no reader
    When I request the reader of the unknown item 9999
    Then the response status is 404

# Non-goals:
#   - A queue or "next post" navigation inside the reader.
#
# Risks:
#   - "Anything already playing keeps playing" depends on the reader not
#     replacing the dock and on deck.js keeping a pane video in the pane;
#     that is browser behaviour the human playtest must cover (YouTube in the default arrangement
#     then open a post; YouTube in Deck then open a post; a SoundCloud track
#     then open a post; close the reader).
#   - The reader request marks the item read, but the list row only greys on
#     the next render unless the response also updates it; the coder decides,
#     the playtest checks it feels right.
#
# Open questions:
#   - None.
