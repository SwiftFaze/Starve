Feature: Keyboard shortcuts on the timeline
  Single-key shortcuts on the timeline and in an open post, and a "?" list of
  them, for longer desktop sessions (#101).

  Covers: deck.js's shortcuts, live only on the timeline page and never while
  typing in a field or contenteditable or with Ctrl, Alt or Meta held: j / k
  move a row cursor (the focused row title, with the usual cyan focus outline)
  down / up and scroll it into view; o (and Enter, natively) opens the current
  row; m marks it read and moves the cursor to the next row; Space plays or
  pauses the deck unless focus is on a button; n / p step to the next /
  previous post while the reader is open; / focuses search; ? shows a key list
  (role dialog, square, hairline, mono keys), Escape or ? again closing it and
  returning focus; keys inside the YouTube iframe never reaching the page.
  Auto-decided: the core set plus n / p, which the post nav already does; s
  and l (star, Later) left out of this first version, as the issue
  recommends.
  Supersedes: nothing; Escape keeps its other jobs.
  Out of scope: rebinding keys; multi-select; Sources and Settings; the
  behaviour in a browser, checked by hand; here the script is checked for it.

  Background:
    Given a fresh SQLite database file
    And the application is running

  Scenario: The script binds the shortcut keys and lists them
    When I request "/deck.js"
    Then the script binds the keys "j, k, o, m, Space, n, p, /, ?"
    And the key list names every bound key
    And the shortcuts only run on the timeline, not while typing or with a modifier held

  Scenario: The timeline is the page the shortcuts look for
    When I request "/feed"
    Then the sheet is marked as the timeline page
