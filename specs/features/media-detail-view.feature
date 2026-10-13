Feature: Detail view for SoundCloud and YouTube items in the pane
  Clicking the title of a SoundCloud track or YouTube video shows a detail view
  in the reader pane beside the list, instead of sending the browser to the
  external site: large artwork, title, artist or channel, published date,
  description and a Play control (Pause when that item is already playing).

  Covers: the title link of a playable item requesting GET /items/{id}/detail
  via htmx into the reader pane while its plain href stays /items/{id}/open
  (the no-JS path); the view showing artwork, title, artist/channel, published
  date and description; SoundCloud details coming from the feed plus
  SoundCloud's public oEmbed (thumbnail, author and description, no API key),
  YouTube details from the feed only; the description inserted as sanitised
  HTML, never raw; the oEmbed response cached per item so it is fetched once and
  not on every render; oEmbed failing (error status or unreadable body)
  falling back to the feed data; a Play control that loads the item into the
  deck exactly as the row's Play does; the view keyed to its item so the deck
  can show Pause while it plays; an "Open original" link (new tab,
  rel="noopener noreferrer"); opening the view marking the item read; the view
  never asking a video in the pane to step down; a Close control; 404 for an
  unknown item and for a non-playable one.
  Supersedes: in part chronological-feed.feature, embedded-playback.feature and
  reader-pane.feature, where the title of a YouTube/SoundCloud item always goes
  to /items/{id}/open. That now holds only without JavaScript; the endpoint
  itself is unchanged. It replaces the "keeps today's behaviour" scenarios for
  YouTube and SoundCloud titles in reader-pane.feature; the reader still 404s
  for playable items.
  Out of scope: comments, play counts and anything needing the SoundCloud API;
  YouTube oEmbed for the detail view; the row's Play control (unchanged,
  embedded-playback.feature); the deck and its placements (player-deck.feature);
  browser behaviour covered by the human playtest (Pause appearing and
  disappearing as playback changes elsewhere, the video staying above the view
  in the pane, splitters, the pane scrolling on its own, closing); a queue or
  next-item navigation.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A playable item's title opens the detail view via htmx and keeps a plain link for no-JS
    Given the source "Cloud" has an unread item "Track" with the link "https://soundcloud.com/artist/track" published 1 days ago
    And the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the home page "/"
    Then the title of "Track" requests its detail view into the reader pane via htmx
    And the title of "Track" still links to its open endpoint without JavaScript
    And the title of "Clip" requests its detail view into the reader pane via htmx
    And the title of "Clip" still links to its open endpoint without JavaScript

  Scenario: A SoundCloud track shows its oEmbed details
    Given the source "Cloud" has an item "Track" by "Feed Artist" with the link "https://soundcloud.com/artist/track" and the stored body "<p>Feed text</p>" published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with the author "Oembed Artist", the thumbnail "https://i1.sndcdn.com/artworks-large.jpg" and the description "<p>Oembed text</p>"
    When I request the detail view of the item "Track" via htmx
    Then the response status is 200
    And the detail view shows the title "Track"
    And the detail view shows the artist "Oembed Artist"
    And the detail view shows a published date
    And the detail view shows the artwork "https://i1.sndcdn.com/artworks-large.jpg"
    And the detail view description contains "<p>Oembed text</p>"
    And the detail view has a Play control

  Scenario: A SoundCloud track whose oEmbed has no description shows the feed's description
    Given the source "Cloud" has an item "Track" with the link "https://soundcloud.com/artist/track" and the stored body "<p>Feed text</p>" published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with the author "Oembed Artist" and no description
    When I request the detail view of the item "Track" via htmx
    Then the detail view description contains "<p>Feed text</p>"

  Scenario: A failing oEmbed falls back to the feed data
    Given the source "Cloud" has an item "Track" by "Feed Artist" with the link "https://soundcloud.com/artist/track", a thumbnail and the stored body "<p>Feed text</p>" published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with HTTP 500
    When I request the detail view of the item "Track" via htmx
    Then the response status is 200
    And the detail view shows the title "Track"
    And the detail view shows the artist "Feed Artist"
    And the detail view shows the item's thumbnail as its artwork
    And the detail view description contains "<p>Feed text</p>"
    And the detail view has a Play control

  Scenario: An oEmbed that cannot be read falls back to the feed data
    Given the source "Cloud" has an item "Track" by "Feed Artist" with the link "https://soundcloud.com/artist/track" and the stored body "<p>Feed text</p>" published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with an unreadable body
    When I request the detail view of the item "Track" via htmx
    Then the detail view shows the artist "Feed Artist"
    And the detail view description contains "<p>Feed text</p>"

  Scenario: A track with no feed data and no oEmbed still shows its title and Play
    Given the source "Cloud" has an item "Bare" with the link "https://soundcloud.com/artist/bare" and no stored body published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/bare" with HTTP 404
    When I request the detail view of the item "Bare" via htmx
    Then the response status is 200
    And the detail view shows the title "Bare"
    And the detail view has no description
    And the detail view has a Play control

  Scenario: A SoundCloud track's oEmbed response is cached per item
    Given the source "Cloud" has an item "Track" with the link "https://soundcloud.com/artist/track" and the stored body "<p>Feed text</p>" published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with the author "Oembed Artist", the thumbnail "https://i1.sndcdn.com/artworks-large.jpg" and the description "<p>Oembed text</p>"
    When I request the detail view of the item "Track" via htmx
    And I request the detail view of the item "Track" via htmx
    Then the SoundCloud oEmbed endpoint was asked for the track "https://soundcloud.com/artist/track" 1 time
    And the detail view shows the artist "Oembed Artist"

  Scenario: The cached oEmbed response survives a restart
    Given the source "Cloud" has an item "Track" with the link "https://soundcloud.com/artist/track" and the stored body "<p>Feed text</p>" published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with the author "Oembed Artist", the thumbnail "https://i1.sndcdn.com/artworks-large.jpg" and the description "<p>Oembed text</p>"
    When I request the detail view of the item "Track" via htmx
    And the application is restarted
    And I request the detail view of the item "Track" via htmx
    Then the SoundCloud oEmbed endpoint was asked for the track "https://soundcloud.com/artist/track" 1 time
    And the detail view shows the artist "Oembed Artist"

  Scenario: A failed oEmbed fetch is not cached, so the next open retries
    Given the source "Cloud" has an item "Track" with the link "https://soundcloud.com/artist/track" and the stored body "<p>Feed text</p>" published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with HTTP 500
    When I request the detail view of the item "Track" via htmx
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with the author "Oembed Artist" and the description "<p>Oembed text</p>"
    And I request the detail view of the item "Track" via htmx
    Then the detail view shows the artist "Oembed Artist"

  Scenario: A YouTube video shows its feed details
    Given the source "Tube" has an item "Clip" by "Some Channel" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ", a thumbnail and the stored body "<p>About the clip</p>" published 1 days ago
    When I request the detail view of the item "Clip" via htmx
    Then the response status is 200
    And the detail view shows the title "Clip"
    And the detail view shows the artist "Some Channel"
    And the detail view shows a published date
    And the detail view shows the item's thumbnail as its artwork
    And the detail view description contains "<p>About the clip</p>"
    And the detail view has a Play control
    And no SoundCloud oEmbed request was made

  Scenario: The Play control loads the item into the deck
    Given the source "Tube" has an item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the detail view of the item "Clip" via htmx
    Then the Play control of the detail view loads the player of "Clip" into the dock

  Scenario: The detail view is keyed to its item so the deck can show Pause
    Given the source "Tube" has an item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the detail view of the item "Clip" via htmx
    Then the detail view carries the catalogue number of "Clip"
    And the detail view has a Play control

  Scenario: Opening the detail view marks the item read
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the detail view of the item "Clip" via htmx
    Then the item "Clip" is read
    When I request the home page "/"
    Then the timeline does not list "Clip"

  Scenario: The detail view offers Open original, which opens externally
    Given the source "Cloud" has an item "Track" with the link "https://soundcloud.com/artist/track" published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with HTTP 404
    When I request the detail view of the item "Track" via htmx
    Then the detail view has an "Open original" link to "https://soundcloud.com/artist/track"
    And the "Open original" link opens in a new tab with rel "noopener noreferrer"

  Scenario: The detail view has a Close control
    Given the source "Tube" has an item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the detail view of the item "Clip" via htmx
    Then the detail view has a Close control

  Scenario: The description is sanitised
    Given the source "Cloud" has an item "Track" with the link "https://soundcloud.com/artist/track" and no stored body published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with the description "<p>Fine</p><script>alert(1)</script><a href=\"javascript:alert(2)\" onclick=\"alert(3)\">link</a>"
    When I request the detail view of the item "Track" via htmx
    Then the detail view description contains "<p>Fine</p>"
    And the response does not contain "<script"
    And the response does not contain "javascript:"
    And the response does not contain "onclick"

  Scenario: Title, artist and source are escaped as text
    Given the source "Cloud <i>One</i>" has an item "A <u>title</u>" by "Band <b>X</b>" with the link "https://soundcloud.com/artist/track" published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with HTTP 404
    When I request the detail view of the item "A <u>title</u>" via htmx
    Then the response contains the text "A <u>title</u>" escaped
    And the response contains the text "Band <b>X</b>" escaped
    And the response contains the text "Cloud <i>One</i>" escaped

  Scenario: Opening a detail view does not move a video out of the pane
    Given the source "Tube" has an item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the detail view of the item "Clip" via htmx
    Then the detail view does not ask a video in the pane to step down

  Scenario: Switching between detail views and posts does not move a video out of the pane
    Given the source "Tube" has an item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    And the source "Cloud" has an item "Track" with the link "https://soundcloud.com/artist/track" published 1 days ago
    And the SoundCloud oEmbed endpoint responds to the track "https://soundcloud.com/artist/track" with HTTP 404
    And the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I request the detail view of the item "Clip" via htmx
    Then the detail view does not ask a video in the pane to step down
    When I request the detail view of the item "Track" via htmx
    Then the detail view does not ask a video in the pane to step down
    When I request the reader of the item "Post" via htmx
    Then the reader does not ask a video in the pane to step down
    When I request the detail view of the item "Clip" via htmx
    Then the detail view does not ask a video in the pane to step down

  Scenario: A blog post has no detail view
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I request the detail view of the item "Post" via htmx
    Then the response status is 404

  Scenario: A post's title still opens the reader, not the detail view
    Given the source "Blog" has an unread item "Post" with the link "https://blog.example/post" published 1 days ago
    When I request the home page "/"
    Then the title of "Post" requests its reader into the reader pane via htmx
    And the title of "Post" does not request a detail view

  Scenario: An unknown item has no detail view
    When I request the detail view of the unknown item 9999
    Then the response status is 404

  Scenario: Without JavaScript the title still opens the original and marks read
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I open the item "Clip"
    Then the response status is 302
    And the response redirects to the link of "Clip"
    And the item "Clip" is read

# Non-goals:
#   - Comments, play counts or any SoundCloud API data.
#   - A YouTube oEmbed call for the detail view.
#
# Risks:
#   - Pause appearing while the item plays (and Play returning when it stops
#     or another item starts) is deck.js behaviour; the human playtest must
#     cover: play from the row then open the view; play from the view; pause
#     from the deck; start another item while a view is open.
#   - "Video stays in the pane" is browser behaviour: playtest YouTube in Pane
#     then open a detail view, a post, and another detail view.
#   - SoundCloud may change its oEmbed shape; the fallback to feed data bounds
#     the damage.
#   - New step phrasings need step definitions: the detail request, the oEmbed
#     fixture (responses and a request counter on the feed server), artist
#     seeding ("by") and the detail-view assertions. starve.soundcloud.oembed-url
#     must point at the fixture in tests. Needs a migration (cached oEmbed on
#     items) and sanitising of the oEmbed description via BodySanitizer.
#
# Open questions:
#   - None.
