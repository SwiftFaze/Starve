Feature: Starve installs as a home-screen app
  On a phone Starve lived in a browser tab, and adding it to the home screen
  gave a generic screenshot icon on iOS. A web app manifest, PNG icons from
  the obi mark and theme colours let "Add to Home Screen" and "Install app"
  give a Starve icon that opens the timeline full screen.

  Covers: "/manifest.webmanifest" (served as application/manifest+json to
  anyone, signed in or not) naming the app "Starve", starting at "/feed" in
  standalone display with the ink ground as its theme and background colour,
  and listing 192 and 512 px icons and a 512 px maskable one; those PNG icons
  and a 180 px "/apple-touch-icon.png", public too; every page's head
  linking the manifest and the touch icon and carrying a theme-color for the
  dark theme (ink) and one for the light theme (paper), which theme.js sets
  to the theme in use when the obi switch overrides the OS; the deck's links
  out opening outside the app; the install guide explaining Add to Home
  Screen.
  Supersedes: nothing.
  Out of scope: a service worker, offline reading or caching; push
  notifications; native apps; how the installed app looks and behaves on a
  real phone: the human playtest.

  Background:
    Given a fresh SQLite database file
    And the application is running

  Scenario: The manifest describes the app to anyone
    Given I am not signed in
    When I request "/manifest.webmanifest"
    Then the response status is 200
    And the response content type is "application/manifest+json"
    And the manifest says
      | name             | Starve     |
      | short_name       | Starve     |
      | start_url        | /feed      |
      | scope            | /          |
      | display          | standalone |
      | theme_color      | #0c0c12    |
      | background_color | #0c0c12    |
    And the manifest lists the icons
      | src                    | sizes   | purpose  |
      | /icon-192.png          | 192x192 | any      |
      | /icon-512.png          | 512x512 | any      |
      | /icon-maskable-512.png | 512x512 | maskable |

  Scenario Outline: The icons are public PNG images of their size
    Given I am not signed in
    When I request "<path>"
    Then the response status is 200
    And the response content type is "image/png"
    And the response is a PNG image of <size> by <size> pixels

    Examples:
      | path                   | size |
      | /icon-192.png          | 192  |
      | /icon-512.png          | 512  |
      | /icon-maskable-512.png | 512  |
      | /apple-touch-icon.png  | 180  |

  Scenario Outline: Every page links the manifest, the touch icon and the theme colours
    When I request "<page>"
    Then the response status is 200
    And the page head links the manifest and the touch icon
    And the page head has the theme colour "#0c0c12" for the dark theme and "#ebe8e0" for the light one

    Examples:
      | page      |
      | /feed     |
      | /sources  |
      | /account  |
      | /settings |
      | /docs     |

  Scenario Outline: The pages before sign-in link them too
    Given I am not signed in
    When I request "<page>"
    Then the page head links the manifest and the touch icon

    Examples:
      | page    |
      | /       |
      | /login  |
      | /docs   |

  Scenario: The install guide explains adding Starve to the home screen
    Given I am not signed in
    When I request "/docs"
    Then the page contains "Add to Home Screen"
    And the page contains "Install app"

  Scenario: A video that cannot be embedded is watched outside the app
    Given a feed server is running
    And the YouTube oEmbed endpoint responds to the video "dQw4w9WgXcQ" with HTTP 401
    And the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the player of the item "Clip" via htmx
    Then the link "Watch on YouTube" opens in a new browsing context

# Non-goals:
#   - Offline use, caching, push.
#
# Risks:
#   - iOS shows the status bar black ("black" status-bar style) in both
#     themes; "black-translucent" would need safe-area padding everywhere.
#   - Whether links out leave the standalone window is the platform's call;
#     every link out carries target="_blank", which both iOS and Android
#     open outside the app.
#
# Open questions:
#   - None (theme colours, the status-bar style and the maskable safe zone
#     are recorded as auto-decided in the intent doc).
