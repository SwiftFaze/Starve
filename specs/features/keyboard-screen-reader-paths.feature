Feature: Keyboard and screen-reader paths through the timeline
  A keyboard or screen-reader user can skip to the list, step item by item,
  stay inside an open post on a phone, and hear short confirmations instead of
  silence or a whole post read out (#99).

  Covers: a "Skip to list" link, the timeline's first focusable element, that
  jumps to #sheet and shows only on keyboard focus; each row (article.item)
  named by its title through aria-labelledby; the reader pane without
  aria-live, opening a post the visitor asked for moving focus to its title
  instead; on a phone, the visually hidden Previous and Next links taken out of
  the tab order (tabindex -1) and the bar, obi and list made inert while a post
  covers the screen, given back on close; one visually hidden role="status"
  line announcing "Marked read · N unread", "All marked read" and "N results"
  (or "No results"); the no-JS page unchanged apart from the skip link, the row
  names and the empty status line.
  Supersedes: nothing.
  Out of scope: keyboard shortcuts (#101); the heading outline (#100); the
  browser behaviour itself (focus, inert, announcements), checked by eye and
  ear; here the page and the script are checked for it.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And the source "Blog" has an unread item "Post" published 1 days ago

  Scenario: The timeline's first focusable element skips to the list
    When I request "/feed"
    Then the first focusable element is the link "Skip to list" to "#sheet"

  Scenario: Each row is named by its title
    When I request "/feed"
    Then the row "Post" is labelled by its title link

  Scenario: The page has one status line and the reader is not a live region
    When I request "/feed"
    Then the page has one polite status line for announcements
    And the reader pane is not a live region

  Scenario: The script moves focus to an opened post and keeps the phone reader closed off
    When I request "/deck.js"
    Then the script focuses an opened post's title only when the visitor opened it
    And the script makes the bar, the obi and the list inert behind a phone post and gives them back on close
    And the script takes a phone post's Previous and Next out of the tab order
    And the script announces "Marked read", "All marked read" and the number of results
