Feature: Player deck along the bottom of the timeline
  Playing an item loads its player into a deck fixed along the bottom of the
  timeline: what is playing, a play/pause control, a waveform that shows
  progress and seeks, elapsed and total time, a volume control and Stop. A
  YouTube video plays in a pane beside the list by default and can move to a
  mini window or into the deck itself; the list beside the pane can be resized
  by dragging its edge. The deck drives the embeds over their postMessage APIs,
  so YouTube's own controls are hidden.

  Covers: the deck controls in the player fragment (pause, seek, volume,
  elapsed, total, Stop); the waveform drawn from bars seeded by the item id, so
  an item always shows the same waveform; the Pane / Mini / Deck placement
  control offered for YouTube only and starting on Pane; the YouTube embed URL
  asking for the JS API and no YouTube controls; the list splitter on the
  timeline; the deck script being served.
  Supersedes: "the dock in the timeline sidebar" placement of
  docked-player.feature (the dock is now the bottom deck); everything else
  there (Play loads into the dock, the named player, Stop, sheet-only swaps)
  is unchanged.
  Out of scope: what the browser does with the controls (seeking, volume,
  moving the video, resizing, folding the list to the STV strip, the title
  ticker): browser behaviour HTTP-level tests cannot see, covered by the human
  playtest. (Reading article text in the pane was out of scope here; it is
  now covered by reader-pane.feature.)

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
    And the response has a Stop control

  Scenario: An item always gets the same waveform
    Given the source "Cloud" has an unread item "Track" with the link "https://soundcloud.com/artist/track" published 1 days ago
    When I request the player of the item "Track" via htmx
    Then the response draws a waveform of 120 bars
    And requesting the player of "Track" again draws the same waveform

  Scenario: A YouTube video offers Pane, Mini and Deck, starting on Pane
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response offers the video placements "Pane", "Mini" and "Deck"
    And the placement "Pane" is selected

  Scenario: A SoundCloud track offers no video placement
    Given the source "Cloud" has an unread item "Track" with the link "https://soundcloud.com/artist/track" published 1 days ago
    When I request the player of the item "Track" via htmx
    Then the response offers no video placement

  Scenario: The timeline has a list splitter and serves the deck script
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the home page "/"
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
