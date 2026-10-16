Feature: The YouTube embed's own controls work again
  A YouTube video in the deck's video panel is YouTube's own player with its
  own controls: subtitles, settings, fullscreen, seek bar and the rest. The
  embed asks for those controls (controls=1) and nothing sits on top of the
  iframe, so every click and double click reaches YouTube. The deck's own
  transport (play/pause, waveform seek, volume, Eject) stays beside it and
  keeps following the video, including when it is paused from YouTube's own
  button. Clicking the video no longer toggles play/pause through the deck,
  and double-clicking it no longer goes fullscreen through the deck: YouTube
  handles both itself.

  Covers: the YouTube embed URL asking for the JS API and YouTube's controls
  (controls=1, still enablejsapi=1, autoplay=1, rel=0, iv_load_policy=3,
  playsinline=1); the iframe allowing fullscreen; no shield element over the
  YouTube iframe; the deck controls still present; SoundCloud and
  embedding-disabled items carrying no shield.
  Supersedes: player-deck.feature's description sentence "The deck drives the
  embeds over their postMessage APIs, so YouTube's own controls are hidden"
  and its "Covers" item "the YouTube embed URL asking for the JS API and no
  YouTube controls" (the deck still drives the embeds over postMessage; the
  controls are no longer hidden). Its Risks note that YouTube shows its title
  bar briefly no longer applies, since the controls are now shown. Also
  supersedes the controls=0 in the embed URL of embedded-playback.feature:
  the scenarios "The player fragment embeds the privacy-enhanced YouTube
  player" and "A video whose oEmbed check is unavailable is embedded anyway"
  must expect controls=1. The coder edits those files; this one does not.
  (player-deck.feature has no scenario naming the URL: the description and
  Covers item are what change there.)
  Out of scope: what YouTube does with the controls and the deck's reaction
  to YouTube's state messages (browser behaviour HTTP-level tests cannot
  see, covered by the human playtest checklist below); the deck's handling
  of YouTube's onStateChange messages (#77); tapping the playing video to
  open its post on mobile (#78); the mobile header (#80) and the shutter
  flash (#81).

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  # ---- happy path -----------------------------------------------------------------------

  Scenario Outline: The YouTube embed asks for YouTube's own controls
    Given the source "Tube" has an unread item "Clip" with the link "<link>" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response status is 200
    And the response contains an iframe whose source is "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&enablejsapi=1&controls=1&rel=0&iv_load_policy=3&playsinline=1"
    And the iframe allows fullscreen
    And the player is wrapped in a responsive 16:9 container

    Examples:
      | link                                        |
      | https://www.youtube.com/watch?v=dQw4w9WgXcQ |
      | https://youtu.be/dQw4w9WgXcQ                |
      | https://www.youtube.com/shorts/dQw4w9WgXcQ  |

  Scenario: Nothing covers the YouTube video
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response status is 200
    And nothing covers the YouTube video

  # ---- edge cases -----------------------------------------------------------------------

  Scenario: The deck's own transport stays beside YouTube's controls
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response has a play/pause control, a seek control and a volume control
    And the response shows elapsed and total time
    And the response has an Eject control

  Scenario: A video whose oEmbed check is unavailable still gets YouTube's controls
    Given the YouTube oEmbed endpoint responds to the video "dQw4w9WgXcQ" with HTTP 500
    And the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response status is 200
    And the response contains an iframe whose source is "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&enablejsapi=1&controls=1&rel=0&iv_load_policy=3&playsinline=1"
    And nothing covers the YouTube video

  Scenario: A SoundCloud track is untouched
    Given the source "Cloud" has an unread item "Track" with the link "https://soundcloud.com/artist/track" published 1 days ago
    When I request the player of the item "Track" via htmx
    Then the response status is 200
    And nothing covers the YouTube video
    And the response has a play/pause control, a seek control and a volume control

  # ---- failure cases --------------------------------------------------------------------

  Scenario: A video that can't be played here has no embed to control
    Given the YouTube oEmbed endpoint responds to the video "dQw4w9WgXcQ" with HTTP 401
    And the source "Tube" has an unread item "Locked" with the link "https://youtu.be/dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Locked" via htmx
    Then the page contains "This video can't be played here"
    And the response contains no iframe
    And nothing covers the YouTube video

  Scenario: A player for an unknown item returns 404
    When I request the player of the unknown item 9999
    Then the response status is 404

# Human playtest checklist (Step 4.5): browser behaviour these scenarios cannot see.
#   - Desktop: Play a video; the YouTube control bar appears on hover.
#   - Subtitles: turn captions on from the CC button; they show. Open settings
#     and change the quality and speed; they take effect.
#   - Fullscreen: YouTube's fullscreen button goes fullscreen and Esc leaves
#     it; double-clicking the video does the same.
#   - Click the video: it pauses or plays (YouTube's own behaviour) and the
#     deck's play/pause icon follows.
#   - Pause with YouTube's own button: the deck's icon switches to Play. Press
#     the deck's button: the video resumes. Seek with YouTube's bar: the deck's
#     waveform and elapsed time follow. Seek on the deck's waveform: the video
#     jumps.
#   - Deck volume slider still changes the video's volume; Eject stops the
#     video and closes the deck.
#   - Phone layout: play a video; YouTube's controls are tappable, and the
#     fullscreen button works (iOS Safari: playsinline keeps it in the page
#     until fullscreen is pressed).
#   - SoundCloud: plays and pauses from the deck as before.
#
# Non-goals:
#   - Deck buttons for subtitles or fullscreen (YouTube's own are used).
#   - Tapping the video to open its post on mobile (#78): it can no longer be
#     built on a full-size shield over the video.
#
# Risks:
#   - #77 is fixing deck.js's handling of YouTube's onStateChange messages.
#     Pausing from YouTube's own button only shows in the deck once that
#     lands; check the playtest items on pause and seek after merging both.
#   - YouTube shows its title bar briefly at start and after a seek, and
#     now also its hover overlay over the video; accepted.
#   - The shield removal drops deck.js's click and double-click handlers on
#     it; fullscreen then depends on YouTube's button and allowfullscreen.
#
# Open questions:
#   - None.
