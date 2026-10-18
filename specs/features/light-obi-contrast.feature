Feature: Obi text and the smallest type pass contrast and size floors
  The stylesheet keeps the obi's text readable in both themes and never sets
  text below the system's smallest label size (#97).

  Covers: the obi ink (--on-obi) on the obi red (--red) reaching at least 4.5:1
  in the dark theme, in the light theme that follows the OS and in the light
  theme the switch forces (ink in both themes since #97; the light theme used
  to write white, about 4.1:1); the failing-count block keeping the obi's
  colours inverted (--obi-tag-text on --on-obi) at 4.5:1 as well; no font size
  in the stylesheet below 0.6875rem (11px), so the "Demo" and "Read" tags, the
  badges and the phone obi meta are all at least 11px; the landing demo
  dimming a struck row by colour, never by opacity, so its faint source and
  date stay at the faint tier's contrast.
  Supersedes: theme-switch.feature's white light-theme obi text, which it
  never asserted.
  Out of scope: other uses of red (the playing row's number cell, the deck's
  rule), which carry ink and already pass; how it looks, checked by eye.

  Background:
    Given a fresh SQLite database file
    And the application is running

  Scenario: Obi text passes AA in every theme
    When I request "/starve.css"
    Then in every theme "--on-obi" on "--red" has a contrast of at least 4.5
    And in every theme "--obi-tag-text" on "--on-obi" has a contrast of at least 4.5

  Scenario: No text is set below 11px
    When I request "/starve.css"
    Then no font size in the stylesheet is below 0.6875rem

  Scenario: The landing demo dims a struck row by colour, not opacity
    When I request "/starve.css"
    Then the keyframes "demo-read" change no opacity
