Feature: Confirm destructive actions, and keep a rejected source URL
  Removing a source (and with it every item it brought), deleting a group and
  resetting a user's password each ask first (#96). The first control is a link
  to the same page with that row in its confirming state, which holds the real
  form and a Cancel link. It works as plain HTML, and with htmx it swaps only
  the sheet, so whatever plays keeps playing. When a pasted source URL is
  rejected, the reason shows as an error under the field, the field is marked
  invalid and keeps what was typed.

  Covers: GET /sources?remove={id} and /sources?delete-group={id}; GET
  /settings?reset={id}; the confirming row (its question, its form and Cancel);
  the add-source error under the field, its alert role, aria-invalid and the
  kept value; group-name errors drawn as errors.
  Supersedes: the "Reset password" form in each row of user-management.feature,
  which is now a link to the row's confirming state.
  Out of scope: deleting a user, which already has its own confirmation page
  (user-management.feature); undo.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: Remove asks first
    Given the source "Alpha" has an unread item "One" published 1 days ago
    When I request "/sources"
    Then the row of the source "Alpha" has a "Remove" link to "/sources?remove={id}"
    And the row of the source "Alpha" has no form that posts "/sources/{id}/delete"

  Scenario: The confirming row names the source and holds the removal
    Given the source "Alpha" has an unread item "One" published 1 days ago
    When I ask to remove the source "Alpha"
    Then the row of the source "Alpha" asks "Remove Alpha and its items?"
    And the row of the source "Alpha" has a form that posts "/sources/{id}/delete"
    And the row of the source "Alpha" has a "Cancel" link to "/sources"

  Scenario: Delete group asks first
    Given the group "Music" exists
    When I request "/sources"
    Then the row of the group "Music" has a "Delete" link to "/sources?delete-group={id}"
    And the row of the group "Music" has no form that posts "/groups/{id}/delete"

  Scenario: The confirming group row says its sources stay
    Given the group "Music" exists
    When I ask to delete the group "Music"
    Then the row of the group "Music" asks "Delete Music? Its sources stay, ungrouped."
    And the row of the group "Music" has a form that posts "/groups/{id}/delete"
    And the row of the group "Music" has a "Cancel" link to "/sources"

  Scenario: Reset password asks first
    Given the user "friend" exists
    When I request "/settings"
    Then the user row of "friend" has a "Reset password" link to "/settings?reset={id}"
    When I GET "/settings?reset={id}" with the id of the user "friend"
    Then the user row of "friend" has a "Reset password" form that posts "/settings/users/{id}/reset" with the CSRF token
    And the user row of "friend" has a "Cancel" link to "/settings"

  Scenario: A rejected URL shows as an error at the field and keeps what was typed
    When I add the raw feed URL "ftp://example.com/feed"
    Then the add-source field shows the error "The feed URL must be an http or https URL"
    And the add-source field keeps "ftp://example.com/feed"

  Scenario: A group-name error is drawn as an error
    When I create the group "   "
    Then the sources page shows the error "group name is required"
