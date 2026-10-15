Feature: Player deck along the bottom of the timeline
  Playing an item loads its player into a deck fixed along the bottom of the
  timeline: what is playing, a play/pause control, a waveform that shows
  progress and seeks, elapsed and total time, a volume control and Eject
  (stops playback and closes the deck). A YouTube video plays in a panel of
  the timeline's workspace, which is tiled like tmux: the list, the video and
  an open post each have a title bar, and dragging a title bar onto another
  panel splits that panel on the side it is dropped (left, right, top or
  bottom), or swaps the two when dropped in its middle; dropped on an edge of
  the workspace, the panel takes that whole edge. The rules between panels are
  dragged to resize them, the arrangement and sizes are remembered, and the
  panel last clicked is outlined. The deck drives the embeds over their
  postMessage APIs, so YouTube's own controls are hidden.

  Covers: the deck controls in the player fragment (pause, seek, volume,
  elapsed, total, Eject); the waveform drawn from bars seeded by the item id,
  so an item always shows the same waveform; no layout buttons in the deck
  (the arrangement is made by dragging title bars); the YouTube embed URL
  asking for the JS API and no YouTube controls; the list splitter on the
  timeline; the deck script being served.
  Supersedes: the 2 col / 3 col / Bottom / PiP layout buttons and the
  picture-in-picture window this file described during #38 (removed: every
  arrangement is now made by dragging title bars), and before them the
  Pane / Mini / Deck placements; the "Stop" label (now Eject); "the dock in
  the timeline sidebar" placement of docked-player.feature (the dock is now
  the bottom deck); everything else there (Play loads into the dock, the named
  player, Eject, sheet-only swaps) is unchanged.
  Out of scope: what the browser does with the controls (seeking, volume,
  splitting, swapping and resizing panels, folding the list to the STV strip,
  the title ticker, the phone layout): browser behaviour HTTP-level tests
  cannot see, covered by the human playtest. (Reading article text in the
  pane is covered by reader-pane.feature.)

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

  Scenario: A YouTube video offers no layout buttons
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response offers no video layout

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
