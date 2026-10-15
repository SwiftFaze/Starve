Feature: Player deck along the bottom of the timeline
  Playing an item loads its player into a deck fixed along the bottom of the
  timeline: what is playing, a play/pause control, a waveform that shows
  progress and seeks, elapsed and total time, a volume control and Eject
  (stops playback and closes the deck). A YouTube video plays in a panel of
  the timeline's workspace, which is tiled like tmux: "2 col" (the list, then
  a column with the video over any open post), "3 col" (list, video and post
  side by side), "Bottom" (the video in a band along the bottom of the
  workspace, above the deck) or "PiP" (in Chrome and Edge, an always-on-top
  picture-in-picture window outside the browser that hosts the video in
  Starve's own "/pip.html", resuming where it was; elsewhere a floating,
  draggable, resizable window in the page). Panels are resized by dragging the splitters between them, can
  swap places by dragging one panel's title bar onto another, and the panel
  last clicked is outlined. The deck drives the embeds over their postMessage
  APIs, so YouTube's own controls are hidden.

  Covers: the deck controls in the player fragment (pause, seek, volume,
  elapsed, total, Eject); the waveform drawn from bars seeded by the item id,
  so an item always shows the same waveform; the 2 col / 3 col / Bottom / PiP
  layout control offered for YouTube only and starting on 2 col; the YouTube
  embed URL asking for the JS API and no YouTube controls; the list splitter
  on the timeline; the deck script being served; "/pip.html" being served and
  allowed in a frame of the same origin (X-Frame-Options SAMEORIGIN), since
  the picture-in-picture window frames it.
  Supersedes: the Pane / Mini / Deck placements this file used to describe
  (Pane is now 2 col, Mini is now PiP, the deck slot is now Bottom) and the
  "Stop" label (now Eject); "the dock in the timeline sidebar" placement of
  docked-player.feature (the dock is now the bottom deck); everything else
  there (Play loads into the dock, the named player, Eject, sheet-only swaps)
  is unchanged.
  Out of scope: what the browser does with the controls (seeking, volume,
  laying out and swapping panels, resizing, folding the list to the STV
  strip, the title ticker, the phone layout): browser behaviour HTTP-level
  tests cannot see, covered by the human playtest. (Reading article text in
  the pane is covered by reader-pane.feature.)

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: The player carries the deck controls
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response status is 200
    And the response has a play/pause control, a seek control and a volume control
    And the response shows elapsed and total time
    And the response has an Eject control

  Scenario: An item always gets the same waveform
    Given the source "Cloud" has an unread item "Track" with the link "https://soundcloud.com/artist/track" published 1 days ago
    When I request the player of the item "Track" via htmx
    Then the response draws a waveform of 120 bars
    And requesting the player of "Track" again draws the same waveform

  Scenario: A YouTube video offers the 2 col, 3 col, Bottom and PiP layouts, starting on 2 col
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response offers the video layouts "2 col", "3 col", "Bottom" and "PiP"
    And the layout "2 col" is selected

  Scenario: A SoundCloud track offers no video layout
    Given the source "Cloud" has an unread item "Track" with the link "https://soundcloud.com/artist/track" published 1 days ago
    When I request the player of the item "Track" via htmx
    Then the response offers no video layout

  Scenario: The timeline has a list splitter and serves the deck script
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the home page "/feed"
    Then the timeline has a list splitter
    And the page loads the deck script
    When I request "/deck.js"
    Then the response status is 200

  Scenario: The picture-in-picture host page can be framed by Starve itself
    When I request "/pip.html"
    Then the response status is 200
    And the response header "X-Frame-Options" is "SAMEORIGIN"

# Non-goals:
#   - A queue, autoplay of the next item, or playback history.
#   - Keeping playback across full page loads.
#
# Risks:
#   - SoundCloud's widget handshake can fail in some browsers; the deck then
#     shows SoundCloud's own player above it after 8 seconds (playtest).
#   - YouTube still shows its title bar briefly at start and after a seek;
#     its embed cannot turn that off (accepted in playtest).
#
# Open questions:
#   - None.
