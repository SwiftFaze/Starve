Feature: Images come through Starve, and links out carry no tracking
  Every image Starve showed was loaded straight from its source, so each
  render told those sites (and their CDNs and trackers) when the user looked
  and from where. Now every image is served by Starve itself, which fetches
  it once and keeps it, and links out lose their tracking parameters.

  Covers: the timeline's thumbnails and site icons, the reader's site icon,
  header image and body images, the detail view's art and description
  images and the deck's art all pointing at "/images?src=<url>&sig=<sig>",
  where the signature is an HMAC of the viewer's username and the URL under
  a key Starve generates once (so the route only ever fetches what Starve
  put on that viewer's page, and one user cannot use another's); the route
  answering 404 for a missing, wrong or other viewer's signature, for an
  image that cannot be fetched, for a response that is not an image
  (jpeg, png, gif, webp, avif, bmp, svg, ico), for one over 5 MB, and for a
  host that resolves to a private, loopback, link-local or otherwise local
  address, checked again at every redirect (at most 3); fetched images
  stored in an "images" folder beside the database and served from there
  with a year-long private Cache-Control and a sandboxing
  Content-Security-Policy; the daily prune removing cached images no stored
  item or source refers to any more, then the least recently used beyond
  starve.images.cache-max-size (default 500MB); deck.js falling back on a
  proxied image that fails as on a missing one (thumbnail, then site icon,
  then blank); utm_* and known click ids (fbclid, gclid, mc_cid, mc_eid and
  similar) stripped from "/items/{id}/open", from "Open original" and from
  links in post bodies; every response carrying "Referrer-Policy:
  no-referrer".
  Supersedes: in part post-images.feature, full-article-fetch.feature and
  media-detail-view.feature, whose images were shown from their own
  addresses; their scenarios still name an image by the address the feed
  gave, read back through the proxy link.
  Out of scope: YouTube, SoundCloud and podcast audio, which still contact
  their platforms; article fetches (already server-side); resizing or
  re-encoding images.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: The timeline shows no image from another site
    Given the source "Blog" has the favicon "<feed server>/icon.png"
    And the source "Blog" has an unread item "Pictured" with the stored thumbnail "<feed server>/thumb.png" published 1 days ago
    And the source "Blog" has an unread item "Plain" with no stored thumbnail published 2 days ago
    When I request the home page "/feed"
    Then every image on the page comes from Starve's image route

  Scenario: A proxied image is fetched from its site once, then served from Starve's cache
    Given the feed server serves "/thumb.png" as an icon
    And the source "Blog" has an unread item "Pictured" with the stored thumbnail "<feed server>/thumb.png" published 1 days ago
    When I request the home page "/feed"
    And I request the image of the timeline entry "Pictured"
    Then the response status is 200
    And the response is an image of type "image/png", cached privately for a year
    When I request the image of the timeline entry "Pictured"
    Then the response status is 200
    And the feed server received 1 requests for "/thumb.png"

  Scenario: The reader's images come through Starve too
    Given the source "Blog" has an item "Post" with the stored body "<p>Text</p><img src=\"https://cdn.example/a.png\">" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then every image on the page comes from Starve's image route

  Scenario Outline: An image link that was not signed for this viewer is refused
    Given the feed server serves "/thumb.png" as an icon
    And the user "alice" exists
    And the source "Blog" has an unread item "Pictured" with the stored thumbnail "<feed server>/thumb.png" published 1 days ago
    When I request the home page "/feed"
    And I request the image of the timeline entry "Pictured" <how>
    Then the response status is 404
    And the feed server received 0 requests for "/thumb.png"

    Examples:
      | how                                     |
      | with its signature changed              |
      | for another address with its signature  |
      | without a signature                     |
      | signed in as "alice"                    |

  Scenario: An image on a private address is never fetched
    Given the source "Blog" has an unread item "Inside" whose stored thumbnail is "/thumb.png" on the feed server's numeric address
    And the feed server serves "/thumb.png" as an icon
    When I request the home page "/feed"
    And I request the image of the timeline entry "Inside"
    Then the response status is 404
    And the feed server received 0 requests for "/thumb.png"

  Scenario: A response that is not an image is refused
    Given the feed server serves "/thumb.png" as an HTML page without any icon link
    And the source "Blog" has an unread item "Pictured" with the stored thumbnail "<feed server>/thumb.png" published 1 days ago
    When I request the home page "/feed"
    And I request the image of the timeline entry "Pictured"
    Then the response status is 404

  Scenario: Opening an item drops the tracking parameters from its link
    Given the source "Blog" has an unread item "Story" with the link "https://example.com/post?utm_source=rss&id=7&fbclid=abc" published 1 days ago
    When I open the item "Story"
    Then the response redirects to "https://example.com/post?id=7"

  Scenario: Links in the reader lose their tracking parameters
    Given the source "Blog" has an item "Post" with the link "https://example.com/post?utm_medium=feed&id=7" and the stored body "<p><a href=\"https://other.example/x?gclid=1&amp;q=a\">x</a></p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader has an "Open original" link to "https://example.com/post?id=7"
    And the reader body links to "https://other.example/x?q=a" opening in a new tab with rel "noopener noreferrer"

  Scenario Outline: No response tells another site where it came from
    When I request "<page>"
    Then the response has the header "Referrer-Policy" set to "no-referrer"

    Examples:
      | page     |
      | /feed    |
      | /sources |
      | /login   |

  Scenario: The cache keeps only the images that stored items and sources still show
    Given the feed server serves "/kept.png" as an icon
    And the feed server serves "/gone.png" as an icon
    And the source "Blog" has an unread item "Kept" with the stored thumbnail "<feed server>/kept.png" published 1 days ago
    And the source "Blog" has an unread item "Gone" with the stored thumbnail "<feed server>/gone.png" published 1 days ago
    When I request the home page "/feed"
    And I request the image of the timeline entry "Kept"
    And I request the image of the timeline entry "Gone"
    And the item "Gone" is deleted from the database
    And the image cache is pruned
    Then the image cache holds 1 image

# Non-goals:
#   - Proxying embeds, audio or article fetches; resizing images.
#
# Risks:
#   - DNS can answer differently between the check and the fetch; the check
#     runs on every hop and the window is small.
#   - A proxied image that fails is replaced in the browser by deck.js; with
#     scripts off it shows as broken (playtest).
#   - Usernames are what links are signed for, so links are only valid while
#     the same username is signed in.
#
# Open questions:
#   - None (the 500MB default cap, the 5 MB image limit and signed links in
#     place of a lookup are recorded as auto-decided in the intent doc).
