Feature: Row and reader polish
  Smaller timeline and reader fixes from the UX critique (#107): icon toggles
  that name themselves, an art placeholder, a readable reader measure and
  lighter row actions on desktop.

  Covers: the star and clock toggles (in rows, the reader and the detail view)
  carrying a visible mono label shown on hover and on keyboard focus, which
  follows the pressed state ("Favourite" / "Unfavourite", "Save for later" /
  "Saved"); a row's art cell showing the sunk surface inside a hairline until
  its lazy image loads, with no layout shift; reader and detail text (paragraphs,
  list items, quotes, headings) kept to 68ch however wide the panel, images,
  tables and code still free; on a desktop pointer (hover-capable, fine, wider
  than 60rem), a row's unpressed star and clock hidden until the row is hovered
  or holds focus, keeping their place and their tab stop, a pressed one always
  shown, and phones and touch always showing both (auto-decided: the issue's
  recommendation).
  Supersedes: nothing; favourites.feature and read-later.feature keep the
  toggles' behaviour.
  Out of scope: keyboard shortcuts (#101); phone target sizes (#98); how the
  labels and the fade look, checked by eye.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And the source "Blog" has an unread item "Post" published 1 days ago

  Scenario: A row's icon toggles carry labels that follow their state
    When I request "/feed"
    Then the star of the row "Post" is labelled "Favourite"
    And the Later toggle of the row "Post" is labelled "Save for later"
    When I favourite the item "Post" via htmx
    Then the returned star is labelled "Unfavourite"
    When I save the item "Post" for later via htmx
    Then the returned Later toggle is labelled "Saved"

  Scenario: The stylesheet keeps the reader measure, the art placeholder and the lighter row actions
    When I request "/starve.css"
    Then the reader's running text is capped at 68ch
    And a row's thumbnail is drawn over the sunk surface
    And only a desktop pointer hides a row's unpressed star and clock until hover or focus
