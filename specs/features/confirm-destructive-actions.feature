Feature: Confirm destructive actions, and keep a rejected source URL
  Removing a source (and with it every item it brought), deleting a group and
  resetting a user's password each ask first (#96). The first control is a link
  to the same page drawn with a confirmation dialog open over it, which names
  what will happen and holds the real form and a Cancel link. It works as plain
  HTML (the dialog is open in the page); with scripts the dialog is modal, and
  Escape or a click on the veil is Cancel. With htmx the link and Cancel swap
  only the sheet, so whatever plays keeps playing. When a pasted source URL is
  rejected, the reason shows as an error under the field, the field is marked
  invalid and keeps what was typed.

  Covers: GET /sources?remove={id} and /sources?delete-group={id}; GET
  /settings?reset={id}; the confirmation dialog (its title, what it says, its
  form and Cancel); an id that names nothing asks nothing; the add-source error
  under the field, its alert role, aria-invalid and the kept value; group-name
  errors drawn as errors.
  Supersedes: the "Reset password" form in each row of user-management.feature,
  which is now a link to the page's reset confirmation.
  Out of scope: deleting a user, which already has its own confirmation page
  (user-management.feature); undo; the modal behaviour itself (deck.js), which
  the acceptance runner cannot drive.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: Remove asks first
    Given the source "Alpha" has an unread item "One" published 1 days ago
    When I request "/sources"
    Then the row of the source "Alpha" has a "Remove" link to "/sources?remove={id}"
    And the row of the source "Alpha" has no form that posts "/sources/{id}/delete"
    And the page has no confirmation

  Scenario: Asking to remove a source shows a confirmation naming it
    Given the source "Alpha" has an unread item "One" published 1 days ago
    When I ask to remove the source "Alpha"
    Then the confirmation is titled "Remove source"
    And the confirmation says "Alpha and every item it brought will be deleted. This can't be undone."
    And the confirmation posts "/sources/{id}/delete" for the source "Alpha"
    And the confirmation has a Cancel link to "/sources"

  Scenario: Delete group asks first
    Given the group "Music" exists
    When I request "/sources"
    Then the row of the group "Music" has a "Delete" link to "/sources?delete-group={id}"
    And the row of the group "Music" has no form that posts "/groups/{id}/delete"

  Scenario: The group confirmation says its sources stay
    Given the group "Music" exists
    And the source "Alpha" has an unread item "One" published 1 days ago
    And the source "Alpha" is in the group "Music"
    When I ask to delete the group "Music"
    Then the confirmation is titled "Delete group"
    And the confirmation says "Music will be deleted. Its 1 source stays, ungrouped."
    And the confirmation posts "/groups/{id}/delete" for the group "Music"
    And the confirmation has a Cancel link to "/sources"

  Scenario: Reset password asks first
    Given the user "friend" exists
    When I request "/settings"
    Then the user row of "friend" has a "Reset password" link to "/settings?reset={id}"
    When I GET "/settings?reset={id}" with the id of the user "friend"
    Then the confirmation is titled "Reset password"
    And the confirmation says "friend gets a temporary password, shown to you once, to sign in with. Their current password stops working."
    And the confirmation posts "/settings/users/{id}/reset" for the user "friend"
    And the confirmation has a Cancel link to "/settings"

  Scenario: An id that names nothing asks nothing
    When I request "/sources?remove=999"
    Then the page has no confirmation

  Scenario: A rejected URL shows as an error at the field and keeps what was typed
    When I add the raw feed URL "ftp://example.com/feed"
    Then the add-source field shows the error "The feed URL must be an http or https URL"
    And the add-source field keeps "ftp://example.com/feed"

  Scenario: A group-name error is drawn as an error
    When I create the group "   "
    Then the sources page shows the error "group name is required"
