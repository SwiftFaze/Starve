Feature: Sources keeps the player deck playing
  The timeline and Sources are two views of one shell. Moving between them
  through the top-bar page tabs, in either direction, swaps the page content
  via htmx and pushes the page's URL, so the player dock (and the deck in it)
  is never replaced and whatever is playing keeps playing. Adding, removing,
  grouping and renaming on Sources are htmx swaps of the same page content, so
  they do not interrupt playback either. Loading "/sources" directly still
  renders a full, working page that carries its own dock.

  Covers: the Timeline and Sources tabs, on both pages, requesting their page
  via htmx, pushing their URL to the browser history and still working as plain
  links without JavaScript; the dock sitting outside everything a page tab or a
  Sources form swaps, on both pages; a direct request for "/sources" returning a
  full page (document, top bar, theme switch, dock, htmx and deck scripts); an
  htmx request for either page still answering with the complete page the
  client selects from, as group links already do; every Sources form (add a
  source, set group, remove, create, rename and delete group) submitting via
  htmx into the page content and still working as a plain form without
  JavaScript; the outcome message of a Sources form landing inside the swapped
  page content; the Import link staying a plain link.
  Supersedes: the "visiting Sources" and "the Sources page" exclusions of
  docked-player.feature (everything else there is unchanged), and the
  page-switch half of theme-switch.feature's shutter, which replaced the whole
  body on a tab click.
  Out of scope: playback surviving a browser reload, opening an item
  ("/items/{id}/open") or the Import page, which stay full page loads (its
  upload form is a multipart post); the reader pane; what the browser does
  (the music really playing on, the shutter's sweep, Back and Forward restoring
  the page behind the dock): browser behaviour HTTP-level tests cannot see,
  covered by the human playtest.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: The page tabs on the timeline swap the page via htmx and keep their URL
    When I request the home page "/feed"
    Then every page tab requests its page via htmx without swapping the dock
    And every page tab pushes its URL to the browser history
    And every page tab still works without JavaScript

  Scenario: The page tabs on Sources swap the page via htmx and keep their URL
    When I request "/sources"
    Then every page tab requests its page via htmx without swapping the dock
    And every page tab pushes its URL to the browser history
    And every page tab still works without JavaScript

  Scenario Outline: Both pages carry the dock outside everything a tab swaps
    When I request "<page>"
    Then the response status is 200
    And the page has a player dock
    And no page tab or form on the page swaps a region containing the dock

    Examples:
      | page     |
      | /feed        |
      | /sources |

  Scenario: Loading Sources directly renders a full page
    When I request "/sources"
    Then the response status is 200
    And the response is a complete HTML document
    And the page contains "Subscriptions"
    And the page has a theme switch
    And the page loads the deck script
    And the page loads the htmx script

  Scenario Outline: An htmx request for a page answers with the complete page to select from
    Given the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the page "<page>" via htmx
    Then the response status is 200
    And the response is a complete HTML document
    And the page contains "<heading>"
    And the page has a player dock

    Examples:
      | page     | heading       |
      | /feed        | New releases  |
      | /sources | Subscriptions |

  Scenario: Returning to the timeline from Sources shows the timeline in full
    Given the group "Music" exists
    And the source "Tube" has an unread item "Clip" with the link "https://www.youtube.com/watch?v=dQw4w9WgXcQ" published 1 days ago
    When I request the page "/sources" via htmx
    And I request the page "/feed" via htmx
    Then the timeline lists "Clip"
    And every group link replaces only the release sheet via htmx
    And every page tab requests its page via htmx without swapping the dock

  Scenario: Every Sources form submits via htmx into the page content
    Given the group "Music" exists
    And the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    When I request "/sources"
    Then the add-a-source form submits via htmx into the page content
    And every source row's set-group and remove forms submit via htmx into the page content
    And the create-group form submits via htmx into the page content
    And every group row's rename and delete forms submit via htmx into the page content
    And every Sources form still works without JavaScript

  Scenario: A rejected add shows its message inside the swapped page content
    When I add the feed URL "not a url" via htmx
    Then the response status is 200
    And the sources page shows the message "must be an http or https URL"
    And the message is inside the page content a Sources form swaps

  Scenario: A rejected group name shows its message inside the swapped page content
    Given the group "Music" exists
    When I create the group "music" via htmx
    Then the response status is 200
    And the sources page shows the message "a group with that name already exists"
    And the message is inside the page content a Sources form swaps

  Scenario: Removing a source via htmx lands on the updated roster
    Given the feed server serves "/news.xml" as an RSS 2.0 feed titled "Daily News" with 1 entries
    And I have added the feed URL "/news.xml"
    When I remove the source "Daily News" via htmx
    Then the response status is 200
    And the sources page does not list a source titled "Daily News"

  Scenario: The Import link stays a plain page load
    When I request "/sources"
    Then the link to "/import" does not request its page via htmx

# Non-goals:
#   - Keeping playback through a browser reload or the Import page's upload.
#   - A persistent shell for the reader pane (separate issue).
#
# Risks:
#   - That the iframe really is never touched by a tab or form swap, and that
#     Back and Forward return to the timeline without rebuilding the dock
#     (htmx restores history by swapping a snapshot of the page, which could
#     re-create the dock), are browser behaviours; covered by the human
#     playtest: play a YouTube video and a SoundCloud track, go to Sources,
#     add and remove a source and a group, return by tab and by Back, and load
#     "/sources" directly.
#   - The shutter (theme.js) currently handles tab clicks by replacing the
#     whole body; it must hand page switches to htmx or it will still stop
#     playback. The shutter itself is playtest-only.
#
# Open questions:
#   - None.
