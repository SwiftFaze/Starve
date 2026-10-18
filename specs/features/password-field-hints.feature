Feature: Access form fields that check early and keep what was typed
  The forms that set a new password (sign-up, first-run setup, Account) let the
  browser check the minimum length before submitting, and a form the server
  rejects comes back with every non-secret field still filled in and the error
  tied to the field it is about (#105).

  Covers: minlength="10" on the new-password field of "/signup", "/setup" and
  "/account" (the server check stays the authority, unchanged); a rejected
  sign-up keeping its username, and a rejected setup keeping its setup code
  and username; a password field never coming back with a value; the field the
  error is about carrying aria-invalid="true" and being described by the error
  message as well as its own hint (username format or taken: the username;
  too short or too long: the new password; a wrong setup code: the code; a
  wrong current password: the current password); an error about no one field
  (too many attempts) marking none.
  Supersedes: nothing; self-signup.feature, accounts-login.feature and
  account-password.feature keep their rules and messages.
  Out of scope: the password rules themselves; the login form, which gets no
  minlength; the browser's own validation message, checked by eye.

  Background:
    Given a fresh SQLite database file

  Scenario: New-password fields carry the minimum length
    Given the application is running
    And signups are on
    And the user "friend" exists
    And I am not signed in
    When I request "/signup"
    Then the field "password" has the minimum length 10
    When I sign in as "friend"
    And I request "/account"
    Then the field "new-password" has the minimum length 10
    And the field "current-password" has no minimum length

  Scenario: The setup form carries the minimum length
    Given the application is running with no users yet
    When I request "/setup"
    Then the field "password" has the minimum length 10

  Scenario: A rejected sign-up keeps the username and marks it
    Given the application is running
    And signups are on
    And I am not signed in
    When I sign up as "OWNER" with password "correct-horse-2"
    Then the signup form is shown again with the error "That username is already taken"
    And the field "username" keeps the value "OWNER"
    And the field "username" is marked invalid and described by the error
    And the field "password" has no value
    And the field "password" is not marked invalid

  Scenario: A too-short password marks the password field
    Given the application is running
    And signups are on
    And I am not signed in
    When I sign up as "newfriend" with password "short"
    Then the field "username" keeps the value "newfriend"
    And the field "password" is marked invalid and described by the error
    And the field "username" is not marked invalid

  Scenario: A rejected setup keeps the code and the username and marks the code
    Given the application is running with no users yet
    When I submit setup with the code "AAAA-AAAA" as "rob" with password "correct-horse-1"
    Then the setup form is shown again with the error "Invalid setup code"
    And the field "code" keeps the value "AAAA-AAAA"
    And the field "username" keeps the value "rob"
    And the field "code" is marked invalid and described by the error
    And the field "password" has no value

  Scenario: A wrong current password marks the current password field
    Given the application is running
    And the user "friend" exists
    When I sign in as "friend"
    And I change my password from "not-my-password" to "battery-staple-2"
    Then the Account page shows the error "Current password is incorrect"
    And the field "current-password" is marked invalid and described by the error
    And the field "new-password" is not marked invalid
    And the field "current-password" has no value
    And the field "new-password" has no value

  Scenario: Too many attempts marks no field
    Given the application is running with no users yet
    When I submit setup with the code "AAAA-AAAA" 5 times
    And I submit setup with the code "AAAA-AAAA" as "rob" with password "correct-horse-1"
    Then the setup form is shown again with the error "Too many failed attempts. Try again later."
    And no field is marked invalid
