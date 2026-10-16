Feature: Deck player fixes: stars in the deck and the detail view, play/pause and SoundCloud on iOS
  The player deck carries a favourite star for the playing item, next to its
  title, and so does the detail view of a YouTube or SoundCloud item. Both are
  the same star as on the timeline rows (the star fragment and POST
  /items/{id}/favourite and /unfavourite). Every star carries an id naming its
  item: star-<id> on a row, deck-star-<id>, reader-star-<id> and
  detail-star-<id>. An htmx toggle from any of them answers the clicked star
  plus the item's other stars out of band, so all of them follow one another,
  and a refresh for an item that is not on screen changes nothing. The deck's
  play/pause control follows the real player state on desktop, and the deck's
  play starts a SoundCloud track on iOS Safari.

  Covers: the star in the player fragment for YouTube, SoundCloud and
  embedding-disabled items, and in the detail view, empty or filled from the
  item's favourite flag; item-specific star ids, the reader's included
  (formerly the fixed "reader-star"); each origin (row, deck, reader, detail
  view) answering its own star plus the other three out of band; the no-JS
  redirect and 404 for an unknown item from the deck and the detail view.
  Supersedes: favourites.feature's exclusion of "the star in the player dock
  or in the detail view for YouTube/SoundCloud items (#30)". Both now have a
  star, and that file's description must drop the exclusion. Its other
  scenarios (row and reader stars, the reader refreshing the row's star) are
  unchanged.
  Out of scope: the deck actually pausing, resuming and updating its icon, and
  SoundCloud starting on an iPhone: browser behaviour HTTP-level tests cannot
  see, covered by the human playtest checklist below (no JS test runner, by
  decision). YouTube embed controls (#79), the mobile post view (#78), the
  mobile header (#80) and the shutter flash (#81).

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  # ---- the star in the deck and the detail view -----------------------------------------

  Scenario Outline: The deck shows the playing item's star
    Given the source "Feed" has an unread item "Song" with the link "<link>" published 1 days ago
    And the item "Song" <state>
    When I request the player of the item "Song" via htmx
    Then the response status is 200
    And the deck has <star> star
    And the deck's star <toggle> "Song" via htmx

    Examples:
      | link                                        | state              | star     | toggle       |
      | https://www.youtube.com/watch?v=dQw4w9WgXcQ | is not a favourite | an empty | favourites   |
      | https://www.youtube.com/watch?v=dQw4w9WgXcQ | is a favourite     | a filled | unfavourites |
      | https://soundcloud.com/artist/track         | is not a favourite | an empty | favourites   |
      | https://soundcloud.com/artist/track         | is a favourite     | a filled | unfavourites |

  Scenario: A video that can't be played here still has its star
    Given the YouTube oEmbed endpoint responds to the video "dQw4w9WgXcQ" with HTTP 401
    And the source "Tube" has an unread item "Locked" with the link "https://youtu.be/dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Locked" via htmx
    Then the page contains "This video can't be played here"
    And the deck has an empty star

  Scenario Outline: The detail view shows the item's star
    Given the source "Feed" has an unread item "Song" with the link "<link>" published 1 days ago
    And the item "Song" <state>
    When I request the detail view of the item "Song" via htmx
    Then the response status is 200
    And the detail view has <star> star
    And the detail view's star <toggle> "Song" via htmx

    Examples:
      | link                                        | state              | star     | toggle       |
      | https://www.youtube.com/watch?v=dQw4w9WgXcQ | is not a favourite | an empty | favourites   |
      | https://soundcloud.com/artist/track         | is a favourite     | a filled | unfavourites |

  Scenario Outline: Every star's id names its item
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    And the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hi</p>" published 1 days ago
    When I request the <place> of the item "<item>" via htmx
    Then the <star> has an id naming the item "<item>"

    Examples:
      | place       | item | star               |
      | player      | Clip | deck's star        |
      | detail view | Clip | detail view's star |
      | reader      | Post | reader's star      |

  # ---- keeping the stars in step --------------------------------------------------------

  Scenario Outline: A toggle from any star refreshes the item's other stars out of band
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I favourite the item "Clip" via htmx from <origin>
    Then the response status is 200
    And the response shows <answer> filled star that unfavourites "Clip" via htmx
    And the response also updates the <others> stars for "Clip" out of band
    And the item "Clip" is a favourite

    Examples:
      | origin           | answer            | others                            |
      | its timeline row | a                 | deck, reader, detail view         |
      | the deck         | the deck's        | timeline row, reader, detail view |
      | the reader       | the reader's      | timeline row, deck, detail view   |
      | the detail view  | the detail view's | timeline row, deck, reader        |

  Scenario: Unfavouriting from the deck refreshes the other stars empty
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    And the item "Clip" is a favourite
    When I unfavourite the item "Clip" via htmx from the deck
    Then the response shows the deck's empty star that favourites "Clip" via htmx
    And every out-of-band star in the response is empty
    And the item "Clip" is not a favourite

  Scenario: Every out-of-band star in a toggle answer names the toggled item
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    And the source "Tube" has an unread item "Other" with the link "https://www.youtube.com/watch?v=9bZkp7q19f0" published 2 days ago
    When I favourite the item "Clip" via htmx from the deck
    Then every out-of-band star in the response has an id naming the item "Clip"
    And no star in the response names the item "Other"

  # ---- failures and no JavaScript -------------------------------------------------------

  Scenario Outline: Toggling an unknown item from the deck or the detail view returns 404
    When I <action> the unknown item <id> via htmx from the <origin>
    Then the response status is 404

    Examples:
      | action      | id   | origin      |
      | favourite   | 9999 | deck        |
      | unfavourite | 9999 | deck        |
      | favourite   | abc  | deck        |
      | favourite   | 9999 | detail view |
      | unfavourite | abc  | detail view |

  Scenario Outline: Toggling from the deck or the detail view without JavaScript redirects to the timeline
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I favourite the item "Clip" from the <origin>
    Then the response redirects to "/feed"
    And the item "Clip" is a favourite

    Examples:
      | origin      |
      | deck        |
      | detail view |

# Human playtest checklist (Step 4.5): browser behaviour these scenarios cannot see.
#   - Desktop, YouTube: Play an item; press pause: playback stops, the icon
#     switches to Play. Press again: it resumes, the icon switches to Pause.
#   - Desktop, SoundCloud: the same.
#   - Desktop: the same after a reload restores the deck (paused where it was).
#   - Desktop: with the playing item's detail view open and its row listed,
#     toggle each of the three stars; the other two follow every time.
#   - Desktop: with a different post open than the one playing, toggle the
#     deck's star; the open post's star does not change.
#   - iPhone, SoundCloud: tap the deck's play button: the track plays; pause,
#     seek and elapsed/total work afterwards.
#
# Non-goals:
#   - Any other change to the deck's layout or transport.
#   - A favourites count, or favouriting anything other than the item shown.
#   - A JS test runner (decided: playtest only).
#
# Risks:
#   - The play/pause cause is unconfirmed (suspected: a stale state.playing
#     from YouTube's infoDelivery). The coder reproduces it before fixing it.
#   - iOS may refuse a programmatic SoundCloud play even inside the tap's
#     handler. The fallback is SoundCloud's own player (the "native" class),
#     recorded as a Clarification if it is taken.
#   - htmx logs htmx:oobErrorNoTarget for each out-of-band star with no
#     element on the page (row not listed, deck closed, post not open).
#     Harmless, but noisy in the console.
#   - Renaming "reader-star" touches existing unit tests and a step regex
#     (FavouriteControllerTest, ReaderControllerTest, StarViewTest,
#     FavouriteSteps). A behaviour-preserving rename, not a regression.
#
# Open questions:
#   - None.
