Feature: Embedded playback for YouTube and SoundCloud items
  Playable items on the timeline get a "Play" control that opens an embedded
  player, so I can watch or listen without leaving my feed. Where that player
  appears (the timeline's dock) and how it keeps playing while I browse is
  owned by docked-player.feature.

  Covers: detecting playable items by their link (YouTube watch / youtu.be /
  shorts links, SoundCloud track links); the "Play" control on the timeline
  (htmx GET /items/{id}/player); the YouTube privacy-enhanced iframe
  (youtube-nocookie.com) and the SoundCloud widget iframe; playing marks the
  item read; 404 for unknown and non-playable items; the embedding-disabled
  fallback via YouTube oEmbed (HTTP 401/403) with a link out; fail-open when
  oEmbed is unavailable. One player at a time is owned by
  docked-player.feature (Play replaces the dock's content).
  Supersedes: nothing. Opening via /items/{id}/open from
  chronological-feed.feature is unchanged and remains the no-JS path.
  Out of scope: the dock and playback across timeline navigation
  (docked-player.feature); downloading media; platforms other than YouTube
  and SoundCloud.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A YouTube item shows a Play control
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the home page "/feed"
    Then the timeline entry "Clip" has a Play control

  Scenario Outline: The player fragment embeds the privacy-enhanced YouTube player
    Given the source "Tube" has an unread item "Clip" with the link "<link>" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response status is 200
    And the response contains an iframe whose source is "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&enablejsapi=1&controls=1&rel=0&iv_load_policy=3&playsinline=1"
    And the iframe allows "autoplay; encrypted-media; picture-in-picture"
    And the iframe allows fullscreen
    And the player is wrapped in a responsive 16:9 container

    Examples:
      | link                                          |
      | https://www.youtube.com/watch?v=dQw4w9WgXcQ   |
      | https://m.youtube.com/watch?v=dQw4w9WgXcQ     |
      | https://music.youtube.com/watch?v=dQw4w9WgXcQ |
      | https://youtu.be/dQw4w9WgXcQ                  |
      | https://www.youtube.com/shorts/dQw4w9WgXcQ    |

  Scenario: A SoundCloud item shows a Play control and embeds the widget
    Given the source "Sound" has an unread item "Track" with the link "https://soundcloud.com/some-artist/some-track" published 1 days ago
    When I request the home page "/feed"
    Then the timeline entry "Track" has a Play control
    When I request the player of the item "Track" via htmx
    Then the response status is 200
    And the response contains an iframe whose source is "https://w.soundcloud.com/player/?url=https%3A%2F%2Fsoundcloud.com%2Fsome-artist%2Fsome-track&auto_play=true"

  Scenario: Playing an item marks it read
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the item "Clip" is read
    When I request the home page "/feed"
    Then the timeline does not list "Clip"

  Scenario: A non-playable item has no Play control and no player
    Given the source "Blog" has an unread item "Story" with the link "https://example.com/story" published 1 days ago
    When I request the home page "/feed"
    Then the timeline lists "Story"
    And the timeline entry "Story" has no Play control
    When I request the player of the item "Story" via htmx
    Then the response status is 404
    And the item "Story" is unread

  Scenario: The player of an unknown item returns 404
    When I request the player of the unknown item 99999
    Then the response status is 404

  Scenario: A video with embedding disabled falls back to a link out with a notice
    Given the YouTube oEmbed endpoint responds to the video "dQw4w9WgXcQ" with HTTP 401
    And the source "Tube" has an unread item "Locked" with the link "https://youtu.be/dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Locked" via htmx
    Then the response status is 200
    And the page contains "This video can't be played here"
    And the response contains a link to "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    And the response contains no iframe
    And the item "Locked" is read

  Scenario: A video whose oEmbed check is unavailable is embedded anyway
    Given the YouTube oEmbed endpoint responds to the video "dQw4w9WgXcQ" with HTTP 500
    And the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response status is 200
    And the response contains an iframe whose source is "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&enablejsapi=1&controls=1&rel=0&iv_load_policy=3&playsinline=1"

# Non-goals:
#   - Downloads, other platforms (Vimeo, Spotify, etc.). The dock is
#     docked-player.feature.
#   - A standalone player page for no-JS browsers; /items/{id}/open remains
#     the no-JS path.
#   - Caching oEmbed results.
#
# Risks:
#   - YouTube or SoundCloud may change embed URLs, oEmbed behavior or policy;
#     the fixture-based tests cannot detect that.
#   - Real browser behavior (autoplay policies, the one-player-at-a-time hook,
#     375px layout, iframes actually loading) is untested by automation and
#     is covered by the human playtest.
#   - New step phrasings (item seeding with a controlled link, player
#     requests, oEmbed fixture, iframe assertions) need step definitions; the
#     feed fixture server must also serve the oEmbed endpoint, and
#     starve.youtube.oembed-url must point at it in tests.
#   - Marking read happens on player request, so a failed iframe load still
#     leaves the item read.
#
# Open questions:
#   - None. Decisions are recorded under Clarifications in
#     specs/intent/embedded-playback.md.
