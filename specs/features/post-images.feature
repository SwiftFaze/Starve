Feature: Post images from the feed, falling back to the site favicon
  Each item gets an image to show in the timeline and the reader: the first of
  media:thumbnail, a media:content image, an image enclosure and the first
  usable <img> of the stored body; failing all of those, the favicon of the
  item's source site, looked up once per source and shown as a site icon.

  Covers: choosing the item thumbnail in that order (itunes:image keeps its
  place after the enclosure); media:content only when it is an image; relative
  image URLs resolved against the item's link (the site link when the item has
  none); thumbnail URLs that are not http or https being ignored; skipping an
  <img> with a width or height of 0 or 1 (tracking pixels) in favour of the
  next one; a re-poll filling a missing thumbnail but never overwriting one;
  the per-source favicon (nullable favicon_url and favicon_checked_at columns
  on sources): looked up from the feed's site link, else the feed URL's
  origin, from the page's <link rel="icon"> (also "shortcut icon"; relative
  href resolved against the page), else /favicon.ico when it answers 2xx with
  an image; looked up on the first poll and again only when the last check is
  over 7 days old (found or not), at most once per source however many items
  it has, on a not-modified poll too; a slow, failing or absent favicon never
  marking the source as errored, losing its items or holding up another
  source; the favicon not being copied onto items: the timeline and the reader
  fall back to it at render time (an item's own thumbnail wins), so earlier
  items get it too; the timeline showing the favicon in the thumbnail slot as
  a smaller site icon, and the blank state when there is none; the reader
  showing a feed thumbnail as a header image unless the body already contains
  that image, and the favicon only as a small icon by the source name;
  the migration leaving existing sources without a favicon.
  Supersedes: in part feed-ingestion.feature, which limits the thumbnail to
  media:thumbnail, an image enclosure and itunes:image; the order and
  fallbacks here extend that, and its scenarios still hold. In part
  reader-pane.feature, whose reader shows no image.
  Out of scope: the article page's og:image fallback (#29); downloading,
  proxying or resizing images; the dock art of YouTube and SoundCloud items;
  apple-touch-icon and web-manifest icons; checking that a <link rel="icon">
  target loads; how the icon looks (sizes, centring): the human playtest.

  Background:
    Given a fresh SQLite database file
    And the application is running
    And a feed server is running

  Scenario: media:thumbnail is stored as the item's thumbnail
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the media:thumbnail "https://img.example/thumb.jpg"
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "https://img.example/thumb.jpg"

  Scenario: A media:content image is used when there is no media:thumbnail
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the media:content "https://img.example/content.jpg" of type "image/jpeg"
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "https://img.example/content.jpg"

  Scenario: A media:content with medium image counts even without a type
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the media:content "https://img.example/medium.jpg" with medium "image"
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "https://img.example/medium.jpg"

  Scenario: A media:content that is not an image is ignored
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the media:content "https://video.example/clip.mp4" of type "video/mp4"
    When I add the feed URL "/blog.xml"
    Then the item of "Blog" has no stored thumbnail

  Scenario: An image enclosure is used when there is no media image
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the image enclosure "https://img.example/enclosure.jpg"
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "https://img.example/enclosure.jpg"

  Scenario: The first image in the body is used when the entry has no media image
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <p>Intro</p><img src="https://img.example/first.png"><img src="https://img.example/second.png">
      """
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "https://img.example/first.png"

  Scenario: A tracking pixel is skipped in favour of the next image
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <img src="https://track.example/p.gif" width="1" height="1"><img src="https://img.example/real.png" width="640" height="480">
      """
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "https://img.example/real.png"

  Scenario: A body whose only image is a tracking pixel yields no feed image
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded
      """
      <p>Hi</p><img src="https://track.example/p.gif" width="1" height="1">
      """
    When I add the feed URL "/blog.xml"
    Then the item of "Blog" has no stored thumbnail

  Scenario: Earlier sources win over later ones
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the media:thumbnail "https://img.example/a-thumb.jpg", the media:content "https://img.example/b-content.jpg" of type "image/jpeg", the image enclosure "https://img.example/c-enclosure.jpg" and the content:encoded "<img src=\"https://img.example/d-body.png\">"
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "https://img.example/a-thumb.jpg"

  Scenario: The enclosure wins over the body image when there is no media image
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the image enclosure "https://img.example/c-enclosure.jpg" and the content:encoded "<img src=\"https://img.example/d-body.png\">"
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "https://img.example/c-enclosure.jpg"

  Scenario: The media:content image wins over the enclosure
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the media:content "https://img.example/b-content.jpg" of type "image/jpeg" and the image enclosure "https://img.example/c-enclosure.jpg"
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "https://img.example/b-content.jpg"

  Scenario: Relative image URLs resolve against the item's link
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the link "<feed server>/posts/one" and the media:thumbnail "/images/thumb.jpg"
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "<feed server>/images/thumb.jpg"

  Scenario: A relative image in the body resolves against the item's link
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the link "<feed server>/posts/one" and the content:encoded "<img src=\"pic.png\">"
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "<feed server>/posts/pic.png"

  Scenario: A relative image URL on an item with no link resolves against the site link
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose channel links to "<feed server>/site/" and whose linkless entry has the media:thumbnail "/images/thumb.jpg"
    When I add the feed URL "/blog.xml"
    Then the stored thumbnail of the item of "Blog" is "<feed server>/images/thumb.jpg"

  Scenario Outline: A thumbnail URL that is not http or https is ignored
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the media:thumbnail "<url>"
    When I add the feed URL "/blog.xml"
    Then the item of "Blog" has no stored thumbnail

    Examples:
      | url                        |
      | javascript:alert(1)        |
      | data:image/png;base64,AAAA |

  Scenario: A refetch fills in a missing thumbnail but never overwrites one
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose entry has the content:encoded "<p>No image</p>"
    And I have added the feed URL "/blog.xml"
    When the feed server now serves "/blog.xml" with the media:thumbnail "https://img.example/first.jpg"
    And the poller runs
    Then the stored thumbnail of the item of "Blog" is "https://img.example/first.jpg"
    When the feed server now serves "/blog.xml" with the media:thumbnail "https://img.example/second.jpg"
    And the poller runs
    Then the stored thumbnail of the item of "Blog" is "https://img.example/first.jpg"

  Scenario: The favicon is found from the site page's icon link
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose channel links to "<feed server>/site/"
    And the feed server serves "/site/" as an HTML page whose head links the icon "https://cdn.example/icon.png"
    When I add the feed URL "/blog.xml"
    Then the stored favicon of the source "Blog" is "https://cdn.example/icon.png"
    And the source "Blog" shows no error

  Scenario: A "shortcut icon" link counts and a relative href resolves against the page
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose channel links to "<feed server>/site/"
    And the feed server serves "/site/" as an HTML page whose head links the "shortcut icon" "assets/fav.png"
    When I add the feed URL "/blog.xml"
    Then the stored favicon of the source "Blog" is "<feed server>/site/assets/fav.png"

  Scenario: Without an icon link the site's /favicon.ico is used
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose channel links to "<feed server>/site/"
    And the feed server serves "/site/" as an HTML page without any icon link
    And the feed server serves "/favicon.ico" as an icon
    When I add the feed URL "/blog.xml"
    Then the stored favicon of the source "Blog" is "<feed server>/favicon.ico"

  Scenario: Without a site link the feed URL's origin is used
    Given the feed server serves "/feeds/blog.xml" as an RSS 2.0 feed titled "Blog" with 1 entries
    And the feed server serves "/" as an HTML page whose head links the icon "/root-icon.png"
    When I add the feed URL "/feeds/blog.xml"
    Then the stored favicon of the source "Blog" is "<feed server>/root-icon.png"

  Scenario: A site with no discoverable favicon has none stored
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose channel links to "<feed server>/site/"
    And the feed server serves "/site/" as an HTML page without any icon link
    And the feed server now responds to "/favicon.ico" with HTTP 404
    When I add the feed URL "/blog.xml"
    Then the source "Blog" has no stored favicon
    And the source "Blog" shows no error

  Scenario: A /favicon.ico that is not an image is not taken as the favicon
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose channel links to "<feed server>/site/"
    And the feed server serves "/site/" as an HTML page without any icon link
    And the feed server serves "/favicon.ico" as an HTML page
    When I add the feed URL "/blog.xml"
    Then the source "Blog" has no stored favicon

  Scenario: The favicon is fetched once per source, not once per item
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose channel links to "<feed server>/site/" with 5 entries
    And the feed server serves "/site/" as an HTML page whose head links the icon "https://cdn.example/icon.png"
    When I add the feed URL "/blog.xml"
    And the poller runs
    Then the feed server has received 1 requests for "/site/"

  Scenario: A missing favicon is not looked up again on every poll
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose channel links to "<feed server>/site/"
    And the feed server serves "/site/" as an HTML page without any icon link
    And the feed server now responds to "/favicon.ico" with HTTP 404
    When I add the feed URL "/blog.xml"
    And the poller runs
    Then the feed server has received 1 requests for "/site/"
    And the feed server has received 1 requests for "/favicon.ico"

  Scenario: The favicon is looked up again once the last check is over 7 days old
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose channel links to "<feed server>/site/"
    And the feed server serves "/site/" as an HTML page whose head links the icon "https://cdn.example/old.png"
    And I have added the feed URL "/blog.xml"
    And the feed server serves "/site/" as an HTML page whose head links the icon "https://cdn.example/new.png"
    And the favicon of the source "Blog" was last checked 8 days ago
    When the poller runs
    Then the stored favicon of the source "Blog" is "https://cdn.example/new.png"

  Scenario: The favicon is not looked up again within 7 days
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose channel links to "<feed server>/site/"
    And the feed server serves "/site/" as an HTML page whose head links the icon "https://cdn.example/old.png"
    And I have added the feed URL "/blog.xml"
    And the favicon of the source "Blog" was last checked 6 days ago
    When the poller runs
    Then the feed server has received 1 requests for "/site/"

  Scenario: A not-modified poll still looks up a favicon that is due
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" with 1 entries and an ETag
    And the feed server serves "/favicon.ico" as an icon
    And I have added the feed URL "/blog.xml"
    And the source "Blog" has no stored favicon and was never checked
    When the poller runs
    Then the stored favicon of the source "Blog" is "<feed server>/favicon.ico"

  Scenario: A site that does not answer in time does not cost the source its items or an error
    Given the feed server serves "/blog.xml" as an RSS 2.0 feed titled "Blog" whose channel links to "<feed server>/site/" with 3 entries
    And the feed server does not respond to "/site/" in time
    And the feed server does not respond to "/favicon.ico" in time
    When I add the feed URL "/blog.xml"
    Then the source "Blog" has 3 stored items
    And the source "Blog" shows no error
    And the source "Blog" shows a last fetched time
    And the source "Blog" has no stored favicon

  Scenario: A failing favicon lookup does not stop another source from being polled
    Given the feed server serves "/slow.xml" as an RSS 2.0 feed titled "Slow Site" whose channel links to "<feed server>/slow/" with 1 entries
    And the feed server serves "/fine.xml" as an RSS 2.0 feed titled "Fine Site" with 1 entries
    And I have added the feed URL "/slow.xml"
    And I have added the feed URL "/fine.xml"
    And the feed server now responds to "/slow/" with HTTP 500
    And the feed "/slow.xml" now has 2 entries
    And the feed "/fine.xml" now has 2 entries
    And the favicon of the source "Slow Site" was last checked 8 days ago
    When the poller runs
    Then the source "Slow Site" has 2 stored items
    And the source "Slow Site" shows no error
    And the source "Fine Site" has 2 stored items
    And the source "Fine Site" shows no error

  Scenario: The timeline shows an item's feed image as its thumbnail
    Given the source "Blog" has an unread item "Post" with the stored thumbnail "https://img.example/thumb.jpg" published 1 days ago
    When I request the home page "/feed"
    Then the timeline entry "Post" shows the thumbnail "https://img.example/thumb.jpg"
    And the timeline entry "Post" shows no site icon

  Scenario: The timeline shows the site favicon for an item without an image
    Given the source "Blog" has the favicon "https://cdn.example/icon.png"
    And the source "Blog" has an unread item "Post" with no stored thumbnail published 1 days ago
    When I request the home page "/feed"
    Then the timeline entry "Post" shows the site icon "https://cdn.example/icon.png"
    And the timeline entry "Post" shows no thumbnail

  Scenario: A feed image wins over the favicon
    Given the source "Blog" has the favicon "https://cdn.example/icon.png"
    And the source "Blog" has an unread item "Post" with the stored thumbnail "https://img.example/thumb.jpg" published 1 days ago
    When I request the home page "/feed"
    Then the timeline entry "Post" shows the thumbnail "https://img.example/thumb.jpg"
    And the timeline entry "Post" shows no site icon

  Scenario: Items stored before the favicon was found show it too
    Given the source "Blog" has an unread item "Old post" with no stored thumbnail published 30 days ago
    And the source "Blog" has the favicon "https://cdn.example/icon.png"
    When I request the home page "/feed"
    Then the timeline entry "Old post" shows the site icon "https://cdn.example/icon.png"

  Scenario: An item with no image and a source with no favicon shows the blank state
    Given the source "Blog" has an unread item "Post" with no stored thumbnail published 1 days ago
    When I request the home page "/feed"
    Then the timeline entry "Post" shows no thumbnail
    And the timeline entry "Post" shows no site icon

  Scenario: The reader shows a feed image above the body
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post", the stored thumbnail "https://img.example/hero.jpg" and the stored body "<p>Hello</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader shows the header image "https://img.example/hero.jpg"
    And the reader shows no site icon

  Scenario: The reader does not repeat an image the body already contains
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post", the stored thumbnail "https://img.example/hero.jpg" and the stored body "<p>Hello</p><img src=\"https://img.example/hero.jpg\">" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader shows no header image
    And the reader shows the image "https://img.example/hero.jpg" once

  Scenario: The reader shows the favicon as a small site icon, not a header image
    Given the source "Blog" has the favicon "https://cdn.example/icon.png"
    And the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hello</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader shows the site icon "https://cdn.example/icon.png" by the source name
    And the reader shows no header image

  Scenario: The reader shows no image when there is neither a feed image nor a favicon
    Given the source "Blog" has an item "Post" with the link "https://blog.example/post" and the stored body "<p>Hello</p>" published 1 days ago
    When I request the reader of the item "Post" via htmx
    Then the reader shows no header image
    And the reader shows no site icon

  Scenario: A source stored before the upgrade has no favicon and keeps its items
    Given the database is migrated only up to before site favicons
    And it holds a source "Blog" with a stored item titled "Old post"
    When the application is running
    Then the source "Blog" has no stored favicon
    And the source "Blog" has a stored item titled "Old post"

# Non-goals:
#   - Caching the icon bytes or serving them from Starve; icons and images load
#     straight from their own hosts, as feed thumbnails do today.
#
# Risks:
#   - Several steps are new vocabulary the coder must add: entries with
#     media:content / enclosure / link / channel link, icon pages, an icon
#     response, request counting, "does not respond in time" per path, setting
#     favicon_checked_at, and the rendered site-icon / header-image markers.
#   - The sanitiser may drop width/height, so the tracking-pixel rule must read
#     them before the attributes are removed.
#   - A lookup that blocks the poll thread costs up to two bounded timeouts per
#     source; the coder decides whether to run it after the items are stored or
#     off-thread, and the scenarios only pin the observable outcome.
#   - How a site icon and a header image look beside the existing 4rem thumbnail
#     slot is a human-playtest question.
#
# Open questions:
#   - None.
