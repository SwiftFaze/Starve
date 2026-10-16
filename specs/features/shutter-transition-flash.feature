Feature: Page switches behind the shutter swap in place, with no reload or flash
  Every link that moves between the app's pages runs behind the red shutter
  as an htmx swap, the way the Timeline and Sources tabs and the theme switch
  already do: the link keeps its "href" (so it works without JavaScript and
  with a modified click), also requests its page via htmx and pushes its URL,
  and every page it can lead to loads the scripts the swap needs. Today only
  those tabs (on the Timeline and Sources) and the theme switch are swaps;
  every other link in the bar, and the links between the public pages, are
  intercepted by theme.js, sweep the shutter in, and then do a full page load
  that flashes. After this feature they are swaps like the tabs.

  Covers: the signed-in top bar on every page that has it (Timeline, Sources,
  Import, Settings, Account, the delete-user confirmation, the temporary
  password page): the Timeline and Sources tabs on the pages that lack the
  htmx attributes today (Import, Settings, Account, the delete-user
  confirmation, the temporary password page), the Account and Settings links
  and the wordmark on every one of them, and the Cancel and "Back to
  Settings" links of the delete-user confirmation and temporary password
  pages; the public top bar and the links between the public pages (landing,
  Docs, Sign in, Sign up, the Setup page's guide link, the wordmark); each of
  these pages answering an htmx request with a complete document the client
  selects from, as the Timeline and Sources already do, and loading the htmx
  and theme scripts; an htmx request without a session still being told to
  load "/login" as a full page; Sign out staying a plain POST form.
  Supersedes: the sentence of theme-switch.feature that says theme changes
  and Timeline/Sources switches are the only things behind the shutter (page
  switches now include every in-app link), and the full-page-switch behaviour
  of theme.js (the bar's plain links, Sign out and the access forms sweeping
  the shutter in and then loading the next page covered), which this feature
  retires; nothing else in theme-switch.feature changes. In
  sources-keeps-playing.feature, Account and Settings join the Timeline and
  Sources as pages that carry the dock, so the bar's switches between those
  four keep playback running (playtest request).
  Out of scope: the mobile header layout (#80) and the mobile post view (#78);
  the Import link on Sources, the Delete link on Settings and the group,
  Favourites, Later and Load more links (the first two stay plain links with
  no shutter, the rest are already plain htmx swaps without one); form
  submissions (Sign in, Sign up, Setup, Sign out, change password, confirm
  delete), which become ordinary form posts with no shutter; what the browser
  does (the sweep, no reload, no flash, the title and the URL changing, Back
  and Forward): browser behaviour HTTP-level tests cannot see, covered by the
  human playtest.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario Outline: The signed-in bar's links request their page via htmx
    When I request "<page>"
    Then the "<label>" link to "<href>" requests its page via htmx and pushes its URL

    Examples:
      | page      | label    | href      |
      | /feed     | Starve   | /feed     |
      | /feed     | Account  | /account  |
      | /feed     | Settings | /settings |
      | /sources  | Starve   | /feed     |
      | /sources  | Account  | /account  |
      | /sources  | Settings | /settings |
      | /import   | Timeline | /feed     |
      | /import   | Sources  | /sources  |
      | /import   | Starve   | /feed     |
      | /import   | Account  | /account  |
      | /import   | Settings | /settings |
      | /settings | Timeline | /feed     |
      | /settings | Sources  | /sources  |
      | /settings | Starve   | /feed     |
      | /settings | Account  | /account  |
      | /account  | Timeline | /feed     |
      | /account  | Sources  | /sources  |
      | /account  | Starve   | /feed     |
      | /account  | Settings | /settings |

  Scenario: The delete-user confirmation's links request their page via htmx
    Given the user "friend" exists
    When I GET "/settings/users/{id}/delete" with the id of the user "friend"
    Then the "Timeline" link to "/feed" requests its page via htmx and pushes its URL
    And the "Sources" link to "/sources" requests its page via htmx and pushes its URL
    And the "Starve" link to "/feed" requests its page via htmx and pushes its URL
    And the "Cancel" link to "/settings" requests its page via htmx and pushes its URL

  Scenario: The temporary password page's links request their page via htmx
    Given the user "friend" exists
    When I POST "/settings/users/{id}/reset" with the id of the user "friend"
    Then the "Timeline" link to "/feed" requests its page via htmx and pushes its URL
    And the "Starve" link to "/feed" requests its page via htmx and pushes its URL
    And the "Back to Settings" link to "/settings" requests its page via htmx and pushes its URL

  Scenario Outline: The public pages' links request their page via htmx
    Given signups are on
    And I am not signed in
    When I request "<page>"
    Then the "<label>" link to "<href>" requests its page via htmx and pushes its URL

    Examples:
      | page    | label   | href    |
      | /       | Docs    | /docs   |
      | /       | Sign in | /login  |
      | /       | Sign up | /signup |
      | /       | Starve  | /       |
      | /docs   | Sign in | /login  |
      | /docs   | Starve  | /       |
      | /login  | Docs    | /docs   |
      | /login  | Sign up | /signup |
      | /login  | Starve  | /       |
      | /signup | Docs    | /docs   |
      | /signup | Sign in | /login  |
      | /signup | Starve  | /       |

  Scenario: The setup page's guide link requests its page via htmx
    Given the application is running with no users yet
    When I request "/setup"
    Then the "install and setup guide" link to "/docs" requests its page via htmx and pushes its URL

  Scenario Outline: A signed-in page answers an htmx request with a complete page and loads the scripts a swap needs
    When I request the page "<page>" via htmx
    Then the response status is 200
    And the response is a complete HTML document
    And the page loads the htmx script
    And the page loads the deck script
    And the page loads the theme script before its stylesheet

    Examples:
      | page      |
      | /feed     |
      | /sources  |
      | /import   |
      | /settings |
      | /account  |

  Scenario Outline: A public page answers an htmx request with a complete page and loads the scripts a swap needs
    Given signups are on
    And I am not signed in
    When I request the page "<page>" via htmx
    Then the response status is 200
    And the response is a complete HTML document
    And the page loads the htmx script
    And the page loads the docs script
    And the page loads the theme script before its stylesheet

    Examples:
      | page    |
      | /       |
      | /docs   |
      | /login  |
      | /signup |

  Scenario: The links still work without JavaScript
    When I request "/account"
    Then the "Timeline" link to "/feed" keeps its plain href
    And the "Settings" link to "/settings" keeps its plain href
    And the "Starve" link to "/feed" keeps its plain href

  Scenario: The Import link on Sources stays a plain link
    When I request "/sources"
    Then the link to "/import" does not request its page via htmx

  Scenario: Sign out stays a plain POST form, not an htmx swap
    When I request "/account"
    Then the top bar has a "Sign out" button that posts "/logout" with the CSRF token
    And the "Sign out" form works without JavaScript
    And the "Sign out" form does not submit via htmx

  Scenario: A swap request after the session has ended is told to load the login page in full
    Given I am not signed in
    When I request the page "/account" via htmx
    Then the response tells htmx to load "/login" as a full page
    And the response does not contain the login form

  Scenario: The Timeline and Sources tabs still keep the dock out of what they swap
    When I request "/feed"
    Then no page tab or form on the page swaps a region containing the dock

# Human playtest checklist (Step 4.5): browser behaviour these scenarios cannot see.
#   - Desktop, signed in, on the Timeline: click Account. The shutter sweeps in
#     and out with no page reload and no flash (no white or blank frame, no
#     text jumping as fonts load, the address bar changes to /account, the tab
#     title becomes "Account - Starve"). Repeat for Settings, the wordmark,
#     and Timeline / Sources from Account, Settings and Import.
#   - The same from Sources, and from Settings to Account and back.
#   - Click the wordmark while on the Timeline: no reload; the page glides back
#     to the top, or, already at the top, nothing visible happens.
#   - Click the link of the page you are on (Account on Account): no shutter,
#     no reload.
#   - Settings: open Delete on a user (a plain link, no shutter), then Cancel:
#     shutter, no flash. Reset a password, then Back to Settings: the same.
#   - Back and Forward through a few of these pages: each restores without a
#     reload or flash.
#   - Playing a video or track, then Timeline -> Sources -> Account ->
#     Settings -> Timeline by the bar's links: it plays on throughout, and
#     each page's sleeve and title are right. Timeline -> Import, Delete user
#     or a form post on Account/Settings: playback stops (a full page load).
#   - Signed out: Docs, Sign in, Sign up and the wordmark on the landing page;
#     the cross-links between Docs, Sign in and Sign up; the guide link on the
#     Setup page: each sweeps with no reload and no flash. The Docs table of
#     contents and its scroll behaviour still work after arriving by a swap.
#   - Theme switch on any of them still sweeps and flips with no flash; after a
#     swap to another page the chosen theme and the favicon are right.
#   - Sign in, Sign up, Setup, Sign out and the change-password and confirm-delete
#     buttons: a plain form post with no shutter and no half-covered page.
#     (A normal browser page load happens here, by design.)
#   - Modified clicks (Ctrl/Cmd-click) on any of these links open a new tab
#     normally; with the browser's reduced-motion setting the pages change at
#     once with no shutter and no reload.
#   - Turn the server off and click a bar link: the shutter clears and the
#     browser falls back to a normal page load, ending on the browser's own
#     error page, not a stuck shutter.
#   - 375px phone width: the swaps work; layout itself is #80.
#
# Non-goals:
#   - Making Sign in, Sign up, Setup and Sign out flash-free or swapped: they
#     change who the visitor is, and the page they land on loads different
#     scripts, so they stay full page loads and lose the shutter.
#   - Keeping playback running beyond the Timeline, Sources, Account and
#     Settings bar switches (sources-keeps-playing.feature); form posts and
#     plain-link pages still reload.
#   - Putting the Import and Delete links behind the shutter.
#   - A JS test runner (playtest only, as for the rest of the shutter).
#
# Risks:
#   - Settings, Account, Import, Docs and the public pages have a different
#     head, body class and sleeve from the Timeline and Sources; a swap that
#     replaces only #sheet would leave the old sleeve and body class on
#     screen. The swap must cover them, the page title must follow, and the
#     scripts (htmx, deck.js, docs.js) must be present or run on arrival.
#   - Pages with no dock must not break deck.js or the Back/Forward restorer,
#     which assume #sheet, #series and #panel exist.
#   - Removing the "arriving" cover (sessionStorage "starve.shutter" and
#     data-shutter) touches theme.js and starve.css; a half-removed version
#     would leave pages hidden.
#   - htmx logs oobErrorNoTarget when an out-of-band target is missing on the
#     destination page. Harmless, but noisy in the console.
#
# Open questions:
#   - None.
