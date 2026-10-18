Feature: Two shutter paces, fast for page switches
  The shutter that covers page switches and theme changes runs at two paces:
  the full sweep for a theme change, a fast one for a page switch (#102,
  option A).

  Covers: theme.js's two paces, a theme change taking 1.25s in, a 0.4s hold
  and 1.25s out, and a page switch taking 250ms in, no hold and 250ms out (about
  0.5s instead of about 3s); the page switch still sweeping in before its
  request goes out and out only after the new page has settled, so the swap
  stays under full cover (#81's fix).
  Supersedes: the "one speed" rule of shutter-transition-flash.feature's
  description for page switches; that feature's swap-under-cover behaviour is
  unchanged.
  Out of scope: the shutter's drawing; reduced motion, which already skips it;
  the felt timing, checked by eye.

  Background:
    Given a fresh SQLite database file
    And the application is running

  Scenario: The theme script runs page switches fast and theme changes at the full sweep
    When I request "/theme.js"
    Then the shutter pace "THEME" sweeps for 1250 ms and holds for 400 ms
    And the shutter pace "PAGE" sweeps for 250 ms and holds for 0 ms
    And the theme switch sweeps in and out at the "THEME" pace
    And a page switch sweeps in and out at the "PAGE" pace
