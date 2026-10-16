Feature: The post view on a phone: close, fill the screen, previous and next, open the playing item's post
  On a phone (below 60rem) an open post (the reader of an article, or the
  detail view of a video or track) covers the screen above the deck. This file
  gives that view the navigation it lacked. The post head carries Close.
  It also carries Previous and Next, which open the neighbouring row of the
  list the post was opened from: the row above (newer) and the row below
  (older), in that list's own order. The list's context (group, Favourites,
  Later, search, Show read) rides on the reader and detail URLs as the same
  query parameters /feed takes, and the row title links add it. A neighbour
  opens as a post view and never starts playing: an article in the reader, a
  video or track in the detail view. The first row has no Previous and the
  last has no Next. While an item plays, its player fragment (the deck)
  carries an "Open post" control that opens that item's detail view in the
  reader pane. It is a control in the deck, not a target over the video, since
  a click on the video belongs to YouTube. Pulling down at the top of a post
  closes it, the post meets the top edge of the screen, and the head returns
  on a deliberate upward scroll, not a micro adjustment; those are browser
  behaviour (playtest checklist below).

  Covers: Previous and Next in the reader and in the detail view, with the
  right targets for the plain timeline, a group, the Favourites view and Show
  read; items read before the list was rendered, which it hides, being
  skipped, while posts read since then (opening one marks it read) still
  count, so walking back works; the first and last rows
  lacking Previous and Next; a neighbour that is a video or track opening its
  detail view and not its player; an unknown group in the list context being
  a 404; "Open post" in the player fragment of YouTube, SoundCloud and
  embedding-disabled items; nothing over the video; the Close control
  staying in both fragments; the phone-only styling of all of these.
  Supersedes: media-detail-view.feature's out-of-scope "a queue or next-item
  navigation" for the post view only (still no queue, no autoplay), and its
  "no longer linked from the timeline": the detail view is now reachable from
  the deck's Open post and from a neighbouring post. Adds to
  youtube-embed-controls.feature's out-of-scope "tapping the playing video to
  open its post on mobile (#78)" without contradicting its Non-goals (no
  target over the video). reader-pane.feature's and media-detail-view.feature's
  "has a Close control" scenarios are unchanged; the bug was CSS hiding it.
  Out of scope: the mobile header and top bar (#80), the shutter flash (#81),
  desktop changes (the new controls are hidden by CSS above 60rem), swiping
  left or right between posts, a queue or autoplay, any tap target over the
  video, and the gestures, the gap, scroll sensitivity and on-screen
  visibility, which HTTP-level tests cannot see (no JS test runner, by
  decision) and the human playtest checklist below covers.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  # ---- previous and next in the post head -----------------------------------------------

  Scenario: A post offers the row above as Previous and the row below as Next
    Given the source "Blog" has an item "Newest" with the link "https://blog.example/a" and the stored body "<p>A</p>" published 1 days ago
    And the source "Blog" has an item "Middle" with the link "https://blog.example/b" and the stored body "<p>B</p>" published 2 days ago
    And the source "Blog" has an item "Oldest" with the link "https://blog.example/c" and the stored body "<p>C</p>" published 3 days ago
    When I request the reader of the item "Middle" via htmx from the plain timeline
    Then the response status is 200
    And the reader offers Previous opening the post of "Newest" via htmx
    And the reader offers Next opening the post of "Oldest" via htmx

  Scenario: In a group the neighbours are the group's own rows
    Given the source "Blog" has an item "Newest" with the link "https://blog.example/a" and the stored body "<p>A</p>" published 1 days ago
    And the source "Other" has an item "Elsewhere" with the link "https://other.example/x" and the stored body "<p>X</p>" published 2 days ago
    And the source "Blog" has an item "Oldest" with the link "https://blog.example/c" and the stored body "<p>C</p>" published 3 days ago
    And the group "Reading" exists
    And the source "Blog" is in the group "Reading"
    When I request the reader of the item "Newest" via htmx from the group "Reading"
    Then the response status is 200
    And the reader offers no Previous
    And the reader offers Next opening the post of "Oldest" via htmx

  Scenario: In the Favourites view the neighbours are the favourites
    Given the source "Blog" has an item "Newest" with the link "https://blog.example/a" and the stored body "<p>A</p>" published 1 days ago
    And the source "Blog" has an item "Middle" with the link "https://blog.example/b" and the stored body "<p>B</p>" published 2 days ago
    And the source "Blog" has an item "Oldest" with the link "https://blog.example/c" and the stored body "<p>C</p>" published 3 days ago
    And the item "Newest" is a favourite
    And the item "Oldest" is a favourite
    When I request the reader of the item "Oldest" via htmx from the Favourites view
    Then the response status is 200
    And the reader offers Previous opening the post of "Newest" via htmx
    And the reader offers no Next

  Scenario: Posts the list hides because they are read are skipped
    Given the source "Blog" has an item "Newest" with the link "https://blog.example/a" and the stored body "<p>A</p>" published 1 days ago
    And the source "Blog" has an item "Seen" with the link "https://blog.example/b" and the stored body "<p>B</p>" published 2 days ago
    And the source "Blog" has an item "Oldest" with the link "https://blog.example/c" and the stored body "<p>C</p>" published 3 days ago
    And I mark the item "Seen" as read
    When I request the reader of the item "Newest" via htmx from the plain timeline
    Then the reader offers Next opening the post of "Oldest" via htmx
    When I request the reader of the item "Newest" via htmx from the timeline showing read items
    Then the reader offers Next opening the post of "Seen" via htmx

  Scenario: The detail view of a video offers Previous and Next too
    Given the source "Tube" has an unread item "Newer" with the link "https://www.youtube.com/watch?v=9bZkp7q19f0" published 1 days ago
    And the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 2 days ago
    And the source "Blog" has an item "Older" with the link "https://blog.example/c" and the stored body "<p>C</p>" published 3 days ago
    When I request the detail view of the item "Clip" via htmx from the plain timeline
    Then the response status is 200
    And the detail view offers Previous opening the post of "Newer" via htmx
    And the detail view offers Next opening the post of "Older" via htmx

  Scenario: A neighbour that is a video opens its detail view and does not start playing
    Given the source "Blog" has an item "Post" with the link "https://blog.example/a" and the stored body "<p>A</p>" published 1 days ago
    And the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 2 days ago
    When I request the reader of the item "Post" via htmx from the plain timeline
    Then the reader offers Next opening the post of "Clip" via htmx
    And the Next control opens the detail view of "Clip" and not its player

  # ---- edges ----------------------------------------------------------------------------

  Scenario: The first post has no Previous and the last has no Next
    Given the source "Blog" has an item "Newest" with the link "https://blog.example/a" and the stored body "<p>A</p>" published 1 days ago
    And the source "Blog" has an item "Oldest" with the link "https://blog.example/c" and the stored body "<p>C</p>" published 3 days ago
    When I request the reader of the item "Newest" via htmx from the plain timeline
    Then the reader offers no Previous
    And the reader offers Next opening the post of "Oldest" via htmx
    When I request the reader of the item "Oldest" via htmx from the plain timeline
    Then the reader offers Previous opening the post of "Newest" via htmx
    And the reader offers no Next

  Scenario: The only post in the list has neither
    Given the source "Blog" has an item "Alone" with the link "https://blog.example/a" and the stored body "<p>A</p>" published 1 days ago
    When I request the reader of the item "Alone" via htmx from the plain timeline
    Then the reader offers no Previous
    And the reader offers no Next

  Scenario: The reader and the detail view keep their Close control
    Given the source "Blog" has an item "Post" with the link "https://blog.example/a" and the stored body "<p>A</p>" published 1 days ago
    And the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 2 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader has a Close control
    When I request the detail view of the item "Clip" via htmx
    Then the detail view has a Close control

  Scenario: Without a list context the plain timeline is assumed
    Given the source "Blog" has an item "Newest" with the link "https://blog.example/a" and the stored body "<p>A</p>" published 1 days ago
    And the source "Blog" has an item "Oldest" with the link "https://blog.example/c" and the stored body "<p>C</p>" published 3 days ago
    When I request the reader of the item "Newest" via htmx
    Then the reader offers Next opening the post of "Oldest" via htmx

  # ---- open the playing item's post -----------------------------------------------------

  Scenario Outline: The deck offers Open post for the playing item
    Given the source "Feed" has an unread item "Song" with the link "<link>" published 1 days ago
    When I request the player of the item "Song" via htmx
    Then the response status is 200
    And the deck offers Open post opening the detail view of "Song" into the reader pane via htmx

    Examples:
      | link                                        |
      | https://www.youtube.com/watch?v=dQw4w9WgXcQ |
      | https://soundcloud.com/artist/track         |

  Scenario: Open post is a deck control and nothing covers the video
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the Open post control is outside the video's container
    And nothing covers the YouTube video

  Scenario: A video that can't be played here still offers Open post
    Given the YouTube oEmbed endpoint responds to the video "dQw4w9WgXcQ" with HTTP 401
    And the source "Tube" has an unread item "Locked" with the link "https://youtu.be/dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Locked" via htmx
    Then the page contains "This video can't be played here"
    And the deck offers Open post opening the detail view of "Locked" into the reader pane via htmx

  # ---- failures -------------------------------------------------------------------------

  Scenario: An unknown group in the list context is a 404
    Given the source "Blog" has an item "Post" with the link "https://blog.example/a" and the stored body "<p>A</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx from the group "Nowhere"
    Then the response status is 404

  Scenario: A malformed group in the list context is a 404
    Given the source "Blog" has an item "Post" with the link "https://blog.example/a" and the stored body "<p>A</p>" published 1 days ago
    When I request the detail view of the item "Post" via htmx from the group "abc"
    Then the response status is 404

  Scenario: An unknown item has no reader to navigate from
    When I request the reader of the unknown item 9999
    Then the response status is 404

# Human playtest checklist (Step 4.5), on an iPhone (or a device-sized browser
# below 60rem). Browser behaviour these scenarios cannot see:
#   - Close: open a post, then a video's detail view (via Open post): Close is
#     visible in the head of both and returns to the list where it was left.
#   - The gap: open a post; it meets the top edge of the screen, and nothing
#     of the page behind (bar, obi, list) shows above it, in portrait and
#     landscape, with the Safari toolbar showing and collapsed, with and
#     without a video playing.
#   - Pull down: at the very top of a post, drag down past about 80px and let
#     go: it closes, as Close does. A shorter drag springs back. Part-way down
#     the post, dragging down just scrolls.
#   - Previous and Next: in the head, with a thumb-sized target (2.75rem). From
#     the middle of a group, Next goes to the row below, Previous to the row
#     above; the post opens scrolled to its top. The first and last rows hide
#     the missing one. Opening a video's detail view this way does not start
#     playback.
#   - Open post: play a YouTube video; the deck shows Open post; tapping it
#     opens that video's post view while the video keeps playing. Tapping the
#     video itself still plays/pauses through YouTube.
#   - Head reveal: scroll a long post down (the head tucks away past 120px),
#     then wiggle upward a few pixels: the head stays away. Scroll up about
#     64px, or all the way to the top: it comes back.
#   - Desktop (above 60rem): no Previous, Next, Open post or Close in the head
#     of a tiled post, and the head never tucks, exactly as before.
#
# Non-goals:
#   - Swiping left or right between posts (collides with iOS's edge-swipe back
#     and with sideways-scrolling code and tables in posts).
#   - A queue or autoplay of the next item.
#   - Any tap target over the video (youtube-embed-controls.feature).
#   - The mobile header and top bar (#80), the shutter flash (#81), desktop.
#
# Risks:
#   - The cause of the gap at the top (item 2) is not visible from the CSS:
#     the phone reader is fixed at top 0 above the bar (starve.css:3614). The
#     coder reproduces it on a device-sized viewport before fixing it.
#   - Close is hidden by a global `.reader-close { display: none }`
#     (starve.css:2595); the rule meant to scope that to tiled desktop was
#     lost in a mangled selector at starve.css:2583. Repairing it must not
#     show Close in a tiled desktop post (the title bar's x does that).
#   - The Later and search views order their rows their own way; Previous and
#     Next must use the same query as the list. Only the plain timeline,
#     groups, Favourites and Show read are asserted here.
#   - The row title links (home.html:96) gain query parameters; the
#     scenarios that assert their hx-get (reader-pane.feature,
#     media-detail-view.feature) must keep passing for the plain timeline.
#   - #81 (not merged) swaps `.label#shell` and moves `#dock` outside it. All
#     the new controls target #reader, which does not move, but the coder
#     re-checks after #81 lands. Its spec was not on its branch when this was
#     written.
#   - The new step phrases (from the plain timeline / group / Favourites view,
#     Previous / Next offers, Open post) are new infrastructure.
#
# Open questions:
#   - None.
