Feature: Docked player that keeps playing while I browse the timeline
  Playing an item docks its player in the timeline's sidebar instead of under
  the item, so music keeps playing while I switch groups, show or hide read
  items, load more or mark everything read.

  Covers: the dock in the timeline sidebar; the Play control loading the
  player into the dock, replacing whatever it held (one player at a time,
  without emptying the dock first); the player fragment naming what is playing (title,
  source, catalogue number "STV" plus the item id zero-padded to four digits,
  thumbnail when the item has one); a Stop control in the dock; group links,
  the show/hide read toggle and "Mark all read" requesting the timeline via
  htmx and replacing only the release sheet, the group navigation and the
  series heading, never the dock; group links and the read toggle pushing
  their URL to the browser history.
  Superseded in part by player-deck.feature: the dock is now a deck along the
  bottom of the timeline, not the sidebar; everything else here still holds.
  Supersedes: the "expands an inline player inside the item" behavior, the
  "keeps playing across navigation" exclusion and the one-player-at-a-time
  htmx hook of embedded-playback.feature. Everything else there (detection,
  embeds, marking read, 404s, oEmbed fallback) is unchanged.
  Out of scope: a queue or playlist; continuing playback across full page
  loads (opening an item, visiting Sources, browser reload); the Sources page;
  no-JS docking (without JavaScript the links and forms work as before and
  /items/{id}/open stays the no-JS path).

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: The timeline has a dock and the Play control loads into it
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the home page "/"
    Then the timeline has a player dock
    And the Play control of "Clip" loads its player into the dock
    And the Play control of "Clip" replaces whatever the dock holds

  Scenario: The docked player names what is playing
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response status is 200
    And the response names the item "Clip" from "Tube" with its catalogue number
    And the response has a Stop control

  Scenario: The docked player shows the item's thumbnail when it has one
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" and a thumbnail published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the response contains the thumbnail of "Clip"

  Scenario: The embedding-disabled fallback is also named
    Given the YouTube oEmbed endpoint responds to the video "dQw4w9WgXcQ" with HTTP 401
    And the source "Tube" has an unread item "Locked" with the link "https://youtu.be/dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Locked" via htmx
    Then the response names the item "Locked" from "Tube" with its catalogue number
    And the page contains "This video can't be played here"

  Scenario: Group links replace only the release sheet and keep their URL
    Given the group "Music" exists
    And the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the home page "/"
    Then every group link replaces only the release sheet via htmx
    And every group link pushes its URL to the browser history
    And every group link still works without JavaScript

  Scenario: The read toggle replaces only the release sheet
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the home page "/"
    Then the read toggle replaces only the release sheet via htmx
    And the read toggle still works without JavaScript

  Scenario: Mark all read replaces only the release sheet
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the home page "/"
    Then the Mark all read form replaces only the release sheet via htmx
    And the Mark all read form still works without JavaScript

# Non-goals:
#   - A queue, autoplay of the next item, or playback history.
#   - Keeping playback across full page loads (Sources, opening an item,
#     reload); that needs a persistent shell, not this dock.
#
# Risks:
#   - That the music really keeps playing (the dock's iframe is never moved or
#     re-rendered by a swap) is browser behavior HTTP-level tests cannot see;
#     it is covered by the human playtest, including the phone layout where
#     the dock is a sticky strip under the top bar.
#   - The player fragment needs the item's title, source and thumbnail, so the
#     item lookup behind GET /items/{id}/player returns more than the link.
#
# Open questions:
#   - None.
