Feature: Theme switch and page shutter
  The caution chevrons at the foot of the obi switch between the dark and the
  light theme; the choice is remembered in the browser, and without one the
  OS decides. Theme changes and switches between Timeline and Sources run
  behind a red shutter panel.

  Covers: every page (Timeline, Sources, Import) carrying the theme switch
  and loading the theme script in its head, so a saved theme applies before
  the first paint; the theme script being served.
  Out of scope: what the browser does (flipping the theme, remembering it,
  the shutter's sweep, swapping a page in place, the same-page scroll to top):
  browser behaviour HTTP-level tests cannot see, covered by the human
  playtest. Without JavaScript the switch does nothing and the page follows
  the OS.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario Outline: Every page carries the theme switch
    When I request "<page>"
    Then the response status is 200
    And the page has a theme switch
    And the page loads the theme script before its stylesheet

    Examples:
      | page     |
      | /feed        |
      | /sources |
      | /import  |

  Scenario: The theme script is served
    When I request "/theme.js"
    Then the response status is 200

# Non-goals:
#   - A third "follow the OS" button; clearing the saved choice returns to it.
#
# Risks:
#   - The page switch swaps content in place; playing stops across Import and
#     reload, but Timeline <-> Sources keeps it (sources-keeps-playing.feature).
#
# Open questions:
#   - None.
