Feature: Phone touch targets and the end of a long list
  On a phone every control is big enough for a thumb, back to the top never
  covers a row's actions, and the end of a long list offers what comes next
  (#98).

  Covers: on screens up to 60rem, the icon toggles 2.75rem wide as well as
  tall; the theme switch's chevrons keeping their drawing with a 2.75rem
  invisible hit area; the wordmark's link filling its 2.75rem line; form
  buttons in the sheet (Import, Reset password), the settings switch and the
  docs Copy buttons at least 2.75rem; back to the top hiding while a row's
  actions or a post's head are under it; at the end of the list (no Load
  more), when unread rows are left on the page and the view is the timeline
  (not search, Favourites or Later), a Mark all read form at the foot, posting
  like the head's, shown on phones only.
  Supersedes: DESIGN.md's phone paragraph listing only row actions, toggles,
  fields and the deck at 2.75rem.
  Out of scope: desktop sizes, unchanged; the deck, already 44px; the "End of
  list" marker itself (#95), which this sits beside once it lands; measured
  sizes in a browser, checked by hand.

  Background:
    Given a fresh SQLite database file
    And the application is running

  Scenario: The end of the list offers Mark all read while unread rows are left
    Given the source "Blog" has an unread item "Post" published 1 days ago
    When I request "/feed"
    Then the end of the list has a Mark all read form posting "/read-all"

  Scenario: A caught-up list has no Mark all read at its end
    When I request "/feed"
    Then the end of the list has no Mark all read form

  Scenario: The stylesheet gives every phone control a 44px target
    When I request "/starve.css"
    Then on phones the rule "button.toggle,\n  a.toggle" sets "width: 2.75rem"
    And on phones the rule ".theme-switch::after" sets "width: 2.75rem"
    And on phones the rule ".wordmark a" sets "min-height: 2.75rem"
    And on phones the rule ".sheet form button,\n  button.switch" sets "min-height: 2.75rem"
    And on phones the rule ".docs-body pre .copy" sets "min-height: 2.75rem"

  Scenario: Back to the top steps aside over a row's actions
    When I request "/deck.js"
    Then back to the top hides while a row's actions or a post's head are under it
