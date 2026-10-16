Feature: The phone header in one row, the obi as group picker and the search in the list's title row
  On a phone (below 60rem) the top bar is one row: the wordmark, Timeline and
  Sources, and a menu button that folds the account actions (Account,
  Settings, Sign out) behind it. The obi is one slim band whose name opens the
  group list (All, the groups, Favourites, Later) under it. The sheet head is
  one line. On every screen the timeline's search waits as an icon at the
  right end of the list's title row and unfolds across it; its results replace
  only the sheet body below it, so the field keeps focus while typing, and a
  group change re-renders the form with the group it searches. The markup is
  shared by phone and desktop and works as plain links and forms without
  scripts; which parts fold away is styling and script.

  Covers: the menu button controlling the account actions in the top bar of
  the timeline, Sources and Account; the account actions staying plain links
  and a plain sign-out form; the obi's group picker controlling the group list
  on the timeline, and no picker on Sources; the search form at the head of
  the timeline's sheet, outside the sheet body its results swap, and none in
  the top bar.
  Supersedes: full-text-search.feature's "search box in the top bar" (the box
  is now at the head of the list); mobile-post-view.feature's Previous and
  Next as phone buttons (they stay in the post head for screen readers, and on
  a phone a sideways swipe across the post follows them; the links and their
  targets are unchanged).
  Out of scope: everything a browser does with it: the menu and group list
  unfolding and folding, the search icon unfolding, the post swipe and
  pull-down gestures and their captions and veil, the deck's line on a phone,
  the drop preview over a video, the video sliding by transform, the artwork
  lightbox. These are covered by the human playtest checklist below.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  # ---- the top bar's menu ---------------------------------------------------------------

  Scenario Outline: The top bar folds its account actions behind a menu button
    When I request "<page>"
    Then the response status is 200
    And the top bar has a menu button controlling its account actions
    And the account actions are a link to "/account" and a plain sign-out form

    Examples:
      | page     |
      | /feed    |
      | /sources |
      | /account |

  # ---- the obi's group picker -----------------------------------------------------------

  Scenario: The timeline's obi opens the group list
    Given the group "Music" exists
    When I request the home page "/feed"
    Then the obi has a group picker controlling the group list
    And the group list links to the group "Music"

  Scenario: Sources has no group picker
    When I request "/sources"
    Then the obi has no group picker

  # ---- search in the list's title row ---------------------------------------------------

  Scenario: The search sits at the head of the list, outside the part its results replace
    When I request the home page "/feed"
    Then the timeline's sheet starts with the search form
    And the search box swaps only the sheet body with its results
    And the top bar has no search box

  Scenario: A group's timeline searches that group
    Given the group "Music" exists
    When I request the timeline of the group "Music"
    Then the search form carries the group "Music"

  Scenario: A search in force keeps the field filled in at the head of the list
    Given the source "Alpha" has an unread item "Match here" published 1 days ago
    When I request the home page "/feed?q=match"
    Then the search box contains "match"
    And the timeline's sheet starts with the search form

# Human playtest checklist (Step 4.5), on an iPhone SE (375×667) and on desktop:
#   - Phone bar: one row (STARVE, Timeline, Sources, menu). The menu unfolds
#     Account, Settings and Sign out under the bar and folds back on a tap
#     outside or Escape; opening the group list closes it.
#   - Obi: one band; ALL ▾ opens the group list under it (rows follow one
#     another in); a group switches the view and closes the list.
#   - Sheet head: Show read and Mark all read on one line with the search
#     icon at the right; the icon unfolds the field across the row, which
#     keeps its query and folds back once cleared and left. Desktop: the same
#     icon at the end of "New releases"; hidden in the folded STV strip.
#   - Post head: STV · READ · star · Later · Close (top right); the feed link
#     and Open original at the foot.
#   - Swipe a post left or right: it follows the finger, the edge caption turns
#     red at the mark, letting go flies it off and the next slides in; short of
#     the mark it eases back; "Last post" / "First post" rubber-bands.
#   - Pull down at the top: the list shows through a dark, blurred veil that
#     clears as the post nears the bottom; "Release to close" turns red at
#     80px; letting go past it drops the post away and closes it.
#   - Deck on a phone: art, title, star, Eject; a tap on the art or title opens
#     the post. Desktop: one row; a click on the art or title opens the post;
#     hovering underlines the title and fills nothing.
#   - Desktop: dragging a panel over the video shows the cyan preview over the
#     whole video panel; swapping panes slides the video without jitter.
#   - A SoundCloud or YouTube post's artwork opens large in the lightbox.
#   - Eject keeps the list where it is.
#
# Non-goals:
#   - A floating bottom toolbar (the deck owns the bottom edge).
#   - Changing desktop's bar beyond the search moving to the list.
#
# Risks:
#   - The phone layout leans on :has() (iOS Safari 15.4+).
#   - The post gestures take touchmove non-passively once a direction locks;
#     a sideways swipe inside a code block or a wide table is left to it.
#
# Open questions:
#   - None.
