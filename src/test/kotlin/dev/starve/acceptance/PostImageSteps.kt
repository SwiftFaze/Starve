package dev.starve.acceptance

import dev.starve.application.ItemRepository
import dev.starve.application.SourceRepository
import dev.starve.domain.FeedItem
import io.cucumber.java.ParameterType
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant

/** A double-quoted step argument, escapes allowed; its one capture group is the quoted text. */
private const val QUOTED = "\"((?:[^\"\\\\]|\\\\.)*)\""

/** A post as the reader-image steps write it: its link, stored thumbnail and stored body. */
data class StoredPost(
    val link: String,
    val thumbnail: String,
    val body: String,
)

/** Steps for post-images.feature: feed images, site icons, and where each one shows. */
class PostImageSteps {
    private val app = World.app
    private val legacy = LegacyDatabase(app)

    // ---- parameter types -----------------------------------------------------------------

    /** An RSS entry's media, link and body, named in the feature as one phrase; parsed by [entrySpec]. */
    @ParameterType("(?:the media:|the link |the image enclosure ).*")
    fun feedEntry(phrase: String): String = phrase

    @ParameterType("with the link $QUOTED, the stored thumbnail $QUOTED and the stored body $QUOTED")
    fun storedPost(
        link: String,
        thumbnail: String,
        body: String,
    ): StoredPost = StoredPost(unescaped(link), unescaped(thumbnail), unescaped(body))

    // ---- feed fixtures -------------------------------------------------------------------

    @Given("the feed server serves {string} as an RSS 2.0 feed titled {string} whose channel links to {string}")
    fun channelLinks(
        path: String,
        title: String,
        site: String,
    ) = serveRss(path, FeedSpec(title = title, siteLink = substituted(site)))

    @Given(
        "the feed server serves {string} as an RSS 2.0 feed titled {string} " +
            "whose channel links to {string} with {int} entries",
    )
    fun channelLinksWithEntries(
        path: String,
        title: String,
        site: String,
        entries: Int,
    ) = serveRss(path, FeedSpec(title = title, siteLink = substituted(site), entries = entries))

    @Given(
        "the feed server serves {string} as an RSS 2.0 feed titled {string} " +
            "whose channel links to {string} and whose linkless entry has the media:thumbnail {string}",
    )
    fun linklessEntryWithThumbnail(
        path: String,
        title: String,
        site: String,
        thumbnail: String,
    ) = serveRss(
        path,
        FeedSpec(title = title, siteLink = substituted(site), linkless = true, mediaThumbnail = thumbnail),
    )

    @Given("the feed server serves {string} as an RSS 2.0 feed titled {string} whose entry has {feedEntry}")
    fun entryHas(
        path: String,
        title: String,
        phrase: String,
    ) = serveRss(path, entrySpec(title, substituted(phrase)))

    @Given("the feed server now serves {string} with the media:thumbnail {string}")
    fun nowServesThumbnail(
        path: String,
        url: String,
    ) = serveRss(path, currentSpec(path).copy(mediaThumbnail = url))

    // ---- site fixtures -------------------------------------------------------------------

    @Given("the feed server serves {string} as an HTML page whose head links the icon {string}")
    fun pageLinksIcon(
        path: String,
        href: String,
    ) = servePage(path, "icon", href)

    @Given("the feed server serves {string} as an HTML page whose head links the {string} {string}")
    fun pageLinksRel(
        path: String,
        rel: String,
        href: String,
    ) = servePage(path, rel, href)

    @Given("the feed server serves {string} as an HTML page without any icon link")
    fun pageWithoutIcon(path: String) =
        World.feedServer().route(path, Route.Html("<html><head><title>Site</title></head><body>Hello</body></html>"))

    @Given("the feed server serves {string} as an icon")
    fun serveIcon(path: String) = World.feedServer().route(path, Route.Icon)

    @Given("the feed server does not respond to {string} in time")
    fun doesNotRespond(path: String) = World.feedServer().route(path, Route.Stall)

    @Then("the feed server has received {int} requests for {string}")
    fun receivedRequests(
        count: Int,
        path: String,
    ) = assertEquals(count, World.feedServer().requests.count { it.path == path })

    // ---- seeding -------------------------------------------------------------------------

    @Given("the source {string} has the favicon {string}")
    fun hasFavicon(
        source: String,
        url: String,
    ) = updateSource(source, "favicon_url = ?, favicon_checked_at = ?", url, Instant.now().toEpochMilli())

    @Given("the favicon of the source {string} was last checked {int} days ago")
    fun checkedDaysAgo(
        source: String,
        days: Int,
    ) = updateSource(source, "favicon_checked_at = ?", daysAgo(days).toEpochMilli())

    @Given("the source {string} has no stored favicon and was never checked")
    fun neverChecked(source: String) = updateSource(source, "favicon_url = NULL, favicon_checked_at = NULL")

    @Given(
        "the source {string} has an unread item {string} with the stored thumbnail {string} published {int} days ago",
    )
    fun unreadWithThumbnail(
        source: String,
        title: String,
        thumbnail: String,
        days: Int,
    ) = seed(source, FeedItem(title, title, link(title), daysAgo(days), null, thumbnail))

    @Given("the source {string} has an unread item {string} with no stored thumbnail published {int} days ago")
    fun unreadWithoutThumbnail(
        source: String,
        title: String,
        days: Int,
    ) = seed(source, FeedItem(title, title, link(title), daysAgo(days), null, null))

    @Given("the source {string} has an item {string} {storedPost} published {int} days ago")
    fun postWithThumbnail(
        source: String,
        title: String,
        post: StoredPost,
        days: Int,
    ) = seed(source, FeedItem(title, title, post.link, daysAgo(days), null, post.thumbnail, post.body))

    @Given("the database is migrated only up to before site favicons")
    fun migratedBeforeFavicons() = legacy.migrateOnlyTo("6")

    // ---- assertions on storage -----------------------------------------------------------

    @Then("the stored thumbnail of the item of {string} is {string}")
    fun thumbnailIs(
        feed: String,
        url: String,
    ) = assertEquals(listOf(substituted(url)), thumbnails(feed))

    @Then("the item of {string} has no stored thumbnail")
    fun hasNoThumbnail(feed: String) = assertEquals(listOf<String?>(null), thumbnails(feed))

    @Then("the stored favicon of the source {string} is {string}")
    fun faviconIs(
        source: String,
        url: String,
    ) = assertEquals(substituted(url), favicon(source))

    @Then("the source {string} has no stored favicon")
    fun hasNoFavicon(source: String) = assertNull(favicon(source))

    // ---- assertions on the timeline ------------------------------------------------------

    @Then("the timeline entry {string} shows the thumbnail {string}")
    fun entryShowsThumbnail(
        title: String,
        url: String,
    ) = assertTrue(imgs(World.entry(title)).any { it.contains("class=\"thumb\"") && it.contains("src=\"$url\"") })

    @Then("the timeline entry {string} shows the site icon {string}")
    fun entryShowsSiteIcon(
        title: String,
        url: String,
    ) = assertTrue(imgs(World.entry(title)).any { it.contains("class=\"site-icon\"") && it.contains("src=\"$url\"") })

    @Then("the timeline entry {string} shows no site icon")
    fun entryShowsNoSiteIcon(title: String) = assertFalse(World.entry(title).contains("class=\"site-icon\""))

    // ---- assertions on the reader --------------------------------------------------------

    @Then("the reader shows the header image {string}")
    fun readerShowsHeaderImage(url: String) =
        assertTrue(imgs(body()).any { it.contains("class=\"reader-image\"") && it.contains("src=\"$url\"") }, body())

    @Then("the reader shows no header image")
    fun readerShowsNoHeaderImage() = assertFalse(body().contains("class=\"reader-image\""), body())

    @Then("the reader shows the image {string} once")
    fun readerShowsImageOnce(url: String) = assertEquals(1, body().split("src=\"$url\"").size - 1, body())

    @Then("the reader shows the site icon {string} by the source name")
    fun readerShowsSiteIcon(url: String) {
        val icon = body().indexOf("class=\"site-icon\"")
        assertTrue(icon >= 0 && imgs(body()).any { it.contains("src=\"$url\"") }, body())
        assertTrue(icon < body().indexOf("class=\"source\""), body())
    }

    @Then("the reader shows no site icon")
    fun readerShowsNoSiteIcon() = assertFalse(body().contains("class=\"site-icon\""), body())

    // ---- helpers -------------------------------------------------------------------------

    private fun body(): String = World.response.body()

    private fun imgs(html: String): List<String> = IMG.findAll(html).map { it.value }.toList()

    private fun serveRss(
        path: String,
        spec: FeedSpec,
    ) = World.feedServer().route(path, Route.Feed(spec))

    private fun currentSpec(path: String): FeedSpec = (World.feedServer().routeOf(path) as Route.Feed).spec

    private fun servePage(
        path: String,
        rel: String,
        href: String,
    ) = World.feedServer().route(
        path,
        Route.Html("<html><head><link rel=\"$rel\" href=\"${substituted(href)}\"></head><body></body></html>"),
    )

    private fun substituted(text: String) = text.replace("<feed server>", World.feedServer().baseUrl)

    /** The RSS entry a phrase such as `the media:thumbnail "a", the link "b"` describes. */
    private fun entrySpec(
        title: String,
        phrase: String,
    ): FeedSpec {
        val content = TYPED_CONTENT.find(phrase)?.let { MediaContent(it.arg(1), type = it.arg(2)) }
        val medium = MEDIUM_CONTENT.find(phrase)?.let { MediaContent(it.arg(1), medium = it.arg(2)) }
        return FeedSpec(
            title = title,
            entryLink = LINK.find(phrase)?.arg(1),
            mediaThumbnail = THUMBNAIL.find(phrase)?.arg(1),
            mediaContent = content ?: medium,
            enclosure = ENCLOSURE.find(phrase)?.arg(1),
            content = ENCODED.find(phrase)?.arg(1),
            description = DESCRIPTION.find(phrase)?.arg(1),
        )
    }

    private fun link(title: String) = "http://example.test/${title.replace(' ', '-')}"

    private fun daysAgo(days: Int): Instant = Instant.now().minus(Duration.ofDays(days.toLong()))

    private fun owner(source: String) =
        app.bean(SourceRepository::class.java).let { sources ->
            sources.findAll().firstOrNull { it.title == source } ?: sources.insert("http://$source.test/feed", source)
        }

    private fun seed(
        source: String,
        item: FeedItem,
    ) = app.bean(ItemRepository::class.java).insertNew(owner(source).id, listOf(item), Instant.now())

    private fun updateSource(
        source: String,
        assignments: String,
        vararg params: Any?,
    ) {
        owner(source)
        app
            .jdbc()
            .sql("UPDATE sources SET $assignments WHERE title = ?")
            .params(params.toList() + source)
            .update()
    }

    private fun thumbnails(feed: String): List<String?> =
        app
            .jdbc()
            .sql("SELECT i.thumbnail_url FROM items i JOIN sources s ON s.id = i.source_id WHERE s.title = ?")
            .param(feed)
            .query { rs, _ -> rs.getString(1) }
            .list()

    private fun favicon(source: String): String? =
        app
            .jdbc()
            .sql("SELECT favicon_url FROM sources WHERE title = ?")
            .param(source)
            .query { rs, _ -> rs.getString(1) }
            .list()
            .single()

    private companion object {
        val IMG = Regex("<img\\b[^>]*>")
        val THUMBNAIL = Regex("the media:thumbnail $QUOTED")
        val TYPED_CONTENT = Regex("the media:content $QUOTED of type $QUOTED")
        val MEDIUM_CONTENT = Regex("the media:content $QUOTED with medium $QUOTED")
        val ENCLOSURE = Regex("the image enclosure $QUOTED")
        val ENCODED = Regex("the content:encoded $QUOTED")
        val LINK = Regex("the link $QUOTED")
        val DESCRIPTION = Regex("the description $QUOTED")

        fun MatchResult.arg(index: Int): String = unescaped(groupValues[index])

        fun unescaped(text: String): String = text.replace("\\\"", "\"")
    }
}
