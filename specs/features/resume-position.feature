Feature: Media resumes where it was left, on any device
  Every play started from zero. Now the deck keeps the position of whatever
  plays on the server, per user and item, so pressing Play again, on this
  device or another, starts where it stopped.

  Covers: POST "/items/{id}/position" with "at" and, when known, "duration"
  in whole seconds, which deck.js sends every 10 seconds of play, on pause,
  at the end, on Eject and when another item takes the deck; the position
  kept in a nullable items.play_position (migration V23) for every playable
  kind (audio, YouTube, SoundCloud); a position under 5 seconds, or within
  the last 10 seconds of a known length, forgotten (the play counts as not
  started, or as finished); the timeline's Play control reading "Resume
  41:12" for an item with a position; the player fragment carrying it as
  data-resume and showing it as the elapsed time, and deck.js seeking there
  once the player is ready; 404 for an unknown or another user's item, 400
  for a negative time.
  Supersedes: nothing; a reload still restores the player paused where it
  was (docked-player.feature), which wins over the stored position.
  Out of scope: a "continue listening" list; syncing two devices playing at
  once; whether the seek lands right in each player: the human playtest.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: A stopped episode offers to resume where it stopped
    Given the feed server serves "/pod.xml" as a podcast titled "Pod" whose episode has the audio enclosure "https://cdn.example/ep1.mp3" lasting "2:00:00"
    And I add the feed URL "/pod.xml"
    When the deck reports that "Pod 1" stopped at 2472 seconds of 7200
    Then the response status is 204
    When I request the home page "/feed?show=all"
    Then the Play control of "Pod 1" reads "Resume 41:12"
    When I request the player of the item "Pod 1" via htmx
    Then the player resumes at 2472 seconds, showing "41:12"

  Scenario Outline: A video or track resumes too
    Given the source "Media" has an unread item "Thing" with the link "<link>" published 1 days ago
    When the deck reports that "Thing" stopped at 95 seconds
    And I request the home page "/feed?show=all"
    Then the Play control of "Thing" reads "Resume 1:35"

    Examples:
      | link                                        |
      | https://www.youtube.com/watch?v=dQw4w9WgXcQ |
      | https://soundcloud.com/artist/track         |

  Scenario Outline: A play barely started or played to the end starts from the beginning next time
    Given the source "Media" has an unread item "Thing" with the link "https://soundcloud.com/artist/track" published 1 days ago
    And the deck reported that "Thing" stopped at 600 seconds of 7200
    When the deck reports that "Thing" stopped at <at> seconds of 7200
    And I request the home page "/feed?show=all"
    Then the Play control of "Thing" reads "Play"
    When I request the player of the item "Thing" via htmx
    Then the player starts from the beginning

    Examples:
      | at   |
      | 4    |
      | 7190 |
      | 7200 |

  Scenario: The newest position replaces the last
    Given the source "Media" has an unread item "Thing" with the link "https://soundcloud.com/artist/track" published 1 days ago
    When the deck reports that "Thing" stopped at 600 seconds
    And the deck reports that "Thing" stopped at 61 seconds
    And I request the home page "/feed?show=all"
    Then the Play control of "Thing" reads "Resume 1:01"

  Scenario: The position follows the user to another device
    Given the source "Media" has an unread item "Thing" with the link "https://soundcloud.com/artist/track" published 1 days ago
    And the deck reported that "Thing" stopped at 3723 seconds
    When I sign in again in another browser
    And I request the home page "/feed?show=all" in that browser
    Then the Play control of "Thing" reads "Resume 1:02:03"

  Scenario: Another user's item, or a negative time, is refused
    Given the user "alice" exists
    And the source "Media" has an unread item "Thing" with the link "https://soundcloud.com/artist/track" published 1 days ago
    When the deck reports that "Thing" stopped at -1 seconds
    Then the response status is 400
    When I sign in as "alice"
    And the deck reports that "Thing" stopped at 95 seconds
    Then the response status is 404

# Non-goals:
#   - A list of things in progress; live sync between devices.
#
# Risks:
#   - Saves are client-driven: a tab killed mid-play keeps the last save, up
#     to 10 seconds behind.
#   - The seek is each player's own API; landing exactly is the playtest's.
#
# Open questions:
#   - None (5 s start threshold, 10 s end window, 10 s save interval are
#     recorded as auto-decided in the intent doc).
