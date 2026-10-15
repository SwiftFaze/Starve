package dev.starve.acceptance

import dev.starve.application.ItemRepository
import dev.starve.application.SourceRepository
import dev.starve.domain.FeedItem
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Steps for full-article-fetch.feature: teaser-only posts, article pages, og:image, and the fetch limits. */
class FullArticleSteps {
    private val app = World.app
    private val legacy = LegacyDatabase(app)
    private var pendingReader: CompletableFuture<HttpResponse<String>>? = null
    private var homeMillis = 0L
    private var readerPendingAtHome = false

    // ---- seeding (every post in this feature belongs to source "Blog") ------------------

    @Given(
        "the source \"Blog\" has an item {string} with the link {string} " +
            "and no stored body and no thumbnail published {int} days ago",
    )
    fun postWithoutBodyOrThumbnail(
        title: String,
        link: String,
        days: Int,
    ) = insertBlog(FeedItem(title, title, expand(link), daysAgo(days), null, null, null))

    @Given(
        "the source \"Blog\" has an item {string} with the link {string} " +
            "and no stored body and the thumbnail {string} published {int} days ago",
    )
    fun postWithThumbnailOnly(
        title: String,
        link: String,
        thumbnail: String,
        days: Int,
    ) = insertBlog(FeedItem(title, title, expand(link), daysAgo(days), null, thumbnail, null))

    @Given(
        "the source \"Blog\" has an item {string} with the link {string} " +
            "and a stored body of {int} characters of text published {int} days ago",
    )
    fun postWithLongBody(
        title: String,
        link: String,
        length: Int,
        days: Int,
    ) = insertBlog(FeedItem(title, title, expand(link), daysAgo(days), null, null, paragraph(length)))

    @Given(
        "the source \"Blog\" has an item {string} with the link {string} " +
            "and a stored body of {int} characters of text and no thumbnail published {int} days ago",
    )
    fun postWithLongBodyNoThumbnail(
        title: String,
        link: String,
        length: Int,
        days: Int,
    ) = insertBlog(FeedItem(title, title, expand(link), daysAgo(days), null, null, paragraph(length)))

    @Given("the article of {string} was last checked {int} hours ago")
    fun articleCheckedHoursAgo(
        title: String,
        hours: Int,
    ) {
        app
            .jdbc()
            .sql("UPDATE items SET article_checked_at = ? WHERE title = ?")
            .params(Instant.now().minus(Duration.ofHours(hours.toLong())).toEpochMilli(), title)
            .update()
    }

    @Given("the database is migrated only up to before article fetching")
    fun migratedBeforeArticles() = legacy.migrateOnlyTo("7")

    // ---- feed server pages ---------------------------------------------------------------

    @Given("the feed server serves {string} as an HTML page with an article of {int} characters of text")
    fun articlePage(
        path: String,
        length: Int,
    ) = serveHtml(path, page(article(length)))

    @Given(
        "the feed server serves {string} as an HTML page whose article starts with the heading {string} " +
            "and {int} characters of text",
    )
    fun articlePageWithTitle(
        path: String,
        heading: String,
        length: Int,
    ) = serveHtml(path, page("<article><h1>$heading</h1><p>${text(length)}</p></article>"))

    @Given(
        "the feed server serves {string} as an HTML page with an article of {int} characters of text " +
            "and the og:image {string}",
    )
    fun articlePageWithImage(
        path: String,
        length: Int,
        image: String,
    ) = serveHtml(path, page(article(length), head = meta(image)))

    @Given(
        "the feed server serves {string} as an HTML page whose article contains {int} characters of text, " +
            "a script element, an onclick attribute, a javascript: link and a relative link {string} " +
            "and image {string}",
    )
    fun unsafeArticlePage(
        path: String,
        length: Int,
        link: String,
        image: String,
    ) = serveHtml(
        path,
        page(
            "<article><p>${text(length)}</p><script>alert(1)</script>" +
                "<p><a href=\"javascript:alert(2)\" onclick=\"alert(3)\">Bad link</a> " +
                "<a href=\"$link\">About</a><img src=\"$image\" alt=\"pic\"></p></article>",
        ),
    )

    @Given(
        "the feed server serves {string} as an HTML page with a nav reading {string}, " +
            "an article of {int} characters of text and a footer reading {string}",
    )
    fun pageWithChrome(
        path: String,
        nav: String,
        length: Int,
        footer: String,
    ) = serveHtml(path, page("<nav>$nav</nav><article><p>${text(length)}</p></article><footer>$footer</footer>"))

    @Given(
        "the feed server serves {string} as an HTML page with {int} characters of paragraph text " +
            "in a plain div among short link lists",
    )
    fun pageWithPlainDiv(
        path: String,
        length: Int,
    ) = serveHtml(
        path,
        page(
            "<ul><li><a href=\"/home\">Home</a></li></ul><div><p>${text(length)}</p></div>" +
                "<ul><li><a href=\"/archive\">Archive</a></li></ul>",
        ),
    )

    @Given("the feed server serves {string} as an HTML page with an empty body")
    fun emptyPage(path: String) = serveHtml(path, page(""))

    @Given("the feed server serves {string} as an HTML page with an empty body and the og:image {string}")
    fun emptyPageWithImage(
        path: String,
        image: String,
    ) = serveHtml(path, page("", head = meta(image)))

    @Given("the feed server answers {int} for {string}")
    fun answersStatus(
        code: Int,
        path: String,
    ) = World.feedServer().route(path, Route.Status(code))

    @Given("the feed server does not respond in time for {string}")
    fun stallsFor(path: String) = World.feedServer().route(path, Route.Stall)

    @Given("the feed server serves a 2 MB HTML page for {string}")
    fun largePage(path: String) = serveHtml(path, page("<p>" + "a".repeat(TWO_MB) + "</p>"))

    @Given("the feed server serves a PDF for {string}")
    fun pdf(path: String) = World.feedServer().route(path, Route.Raw("article.pdf", "application/pdf"))

    // ---- actions -------------------------------------------------------------------------

    @When("I request the reader of the item {string} via htmx and, meanwhile, the home page {string}")
    fun readerAndHomeTogether(
        title: String,
        path: String,
    ) {
        val id = itemId(title)
        val reader = CompletableFuture.supplyAsync { app.get("/items/$id/reader", mapOf("HX-Request" to "true")) }
        awaitFeedRequest()
        val started = System.nanoTime()
        World.response = app.get(path)
        homeMillis = (System.nanoTime() - started) / NANOS_PER_MILLI
        World.pages = listOf(World.response.body())
        readerPendingAtHome = !reader.isDone
        pendingReader = reader
    }

    // ---- assertions ----------------------------------------------------------------------

    @Then("no article page is requested")
    fun noArticleRequested() {
        val requests = World.feedServer().requests
        assertTrue(requests.isEmpty(), "requests: $requests")
    }

    @Then("the feed server received {int} requests for {string}")
    fun receivedRequests(
        count: Int,
        path: String,
    ) = assertEquals(count, World.feedServer().requests.count { it.path == path })

    @Then("the reader answered within 8 seconds")
    fun answeredWithin() {
        assertTrue(World.readerMillis <= EIGHT_SECONDS_MILLIS, "reader took ${World.readerMillis} ms")
    }

    @Then("the home page answers without waiting for the reader")
    fun homeAnsweredFirst() {
        assertTrue(readerPendingAtHome, "the reader had already answered")
        assertTrue(homeMillis < TWO_SECONDS_MILLIS, "home page took $homeMillis ms")
        pendingReader?.get(TEN_SECONDS, TimeUnit.SECONDS)
    }

    @Then("the reader body contains the article text")
    fun readerHasArticleText() = assertTrue(World.response.body().contains(ARTICLE_PHRASE), World.response.body())

    @Then("the reader body contains the paragraph text")
    fun readerHasParagraphText() = readerHasArticleText()

    @Then("the reader body does not contain {string}")
    fun readerLacks(text: String) = assertFalse(World.response.body().contains(text), World.response.body())

    @Then("the reader body is the stored body")
    fun readerShowsStoredBody() {
        val stored = requireNotNull(World.bodyBeforeReader) { "no stored body" }
        assertTrue(World.response.body().contains(stored), World.response.body())
    }

    @Then("the reader body has no script element")
    fun readerHasNoScript() = assertTrue(readerBody().select("script").isEmpty(), readerBody().html())

    @Then("the reader body has no onclick attribute")
    fun readerHasNoOnclick() = assertTrue(readerBody().select("[onclick]").isEmpty(), readerBody().html())

    @Then("the reader body has no javascript: URL")
    fun readerHasNoScriptUrl() {
        val urlAttributes = readerBody().allElements.flatMap { it.attributes().asList() }.map { it.value.trim() }
        assertTrue(urlAttributes.none { it.lowercase().startsWith("javascript:") }, readerBody().html())
    }

    @Then("the reader body links to {string} opening in a new tab with rel {string}")
    fun readerLinksOpenInNewTab(
        url: String,
        rel: String,
    ) {
        val links = readerBody().select("a").filter { it.attr("href") == expand(url) }
        assertTrue(links.isNotEmpty(), readerBody().html())
        links.forEach {
            assertEquals("_blank", it.attr("target"))
            assertEquals(rel, it.attr("rel"))
        }
    }

    @Then("the reader body shows the image {string}")
    fun readerShowsImage(url: String) {
        assertTrue(readerBody().select("img").any { it.attr("src") == expand(url) }, readerBody().html())
    }

    @Then("the stored body of the item {string} contains the article text")
    fun storedBodyHasArticle(title: String) {
        val body = storedBody(title)
        assertTrue(body?.contains(ARTICLE_PHRASE) == true, body ?: "no body")
    }

    @Then("the stored body of the item {string} is unchanged")
    fun storedBodyUnchanged(title: String) = assertEquals(World.bodyBeforeReader, storedBody(title))

    @Then("the item {string} has no recorded article check")
    fun noArticleCheck(title: String) = assertNull(articleChecked(title))

    @Then("the stored thumbnail of the item {string} is {string}")
    fun storedThumbnailIs(
        title: String,
        url: String,
    ) = assertEquals(expand(url), storedThumbnail(title))

    @Then("the item {string} has no stored thumbnail")
    fun noStoredThumbnail(title: String) = assertNull(storedThumbnail(title))

    @Then("the timeline shows {string} as the thumbnail of {string}")
    fun timelineShowsThumbnail(
        url: String,
        title: String,
    ) {
        val entry = timelineEntry(title)
        assertTrue(entry.contains("class=\"thumb\"") && entry.contains("src=\"$url\""), entry)
    }

    @Then("the timeline shows no site icon for {string}")
    fun timelineShowsNoIcon(title: String) {
        val entry = timelineEntry(title)
        assertFalse(entry.contains("class=\"site-icon\""), entry)
    }

    @Then("the timeline shows the favicon {string} as the site icon of {string}")
    fun timelineShowsIcon(
        url: String,
        title: String,
    ) {
        val entry = timelineEntry(title)
        assertTrue(entry.contains("class=\"site-icon\"") && entry.contains("src=\"$url\""), entry)
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun insertBlog(item: FeedItem) {
        val owner = app.ownedSource(BLOG)
        app.bean(ItemRepository::class.java).insertNew(owner.id, listOf(item), Instant.now())
    }

    private fun daysAgo(days: Int): Instant = Instant.now().minus(Duration.ofDays(days.toLong()))

    private fun serveHtml(
        path: String,
        markup: String,
    ) = World.feedServer().route(path, Route.Html(markup))

    private fun page(
        markup: String,
        head: String = "",
    ) = "<html><head><title>Post</title>$head</head><body>$markup</body></html>"

    private fun article(length: Int) = "<article><p>${text(length)}</p></article>"

    private fun meta(url: String) = "<meta property=\"og:image\" content=\"$url\">"

    private fun paragraph(length: Int) = "<p>${text(length)}</p>"

    /** Plain text of exactly [length] characters, starting with [ARTICLE_PHRASE]. */
    private fun text(length: Int): String =
        (ARTICLE_PHRASE + ", consectetur adipiscing elit, sed do eiusmod tempor. ")
            .repeat(length / SENTENCE_REPEAT_UNIT + 2)
            .take(length)
            .let { if (it.endsWith(" ")) it.dropLast(1) + "." else it }

    private fun expand(text: String) = World.expandFeedServer(text)

    private fun readerBody() = Jsoup.parse(World.response.body())

    /** The timeline entry of [title] on the all-items view, which still lists a post once it is read. */
    private fun timelineEntry(title: String): String {
        World.pages = listOf(app.get("/feed?show=all").body())
        return World.entry(title)
    }

    private fun awaitFeedRequest() {
        val deadline = System.nanoTime() + TWO_SECONDS_MILLIS * NANOS_PER_MILLI
        while (World.feedServer().requests.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MILLIS)
        }
        assertFalse(World.feedServer().requests.isEmpty(), "the reader never asked for the article page")
    }

    private fun itemId(title: String): Long = app.itemId(title)

    private fun storedBody(title: String): String? = column("body", title)

    private fun storedThumbnail(title: String): String? = column("thumbnail_url", title)

    private fun articleChecked(title: String): String? = column("article_checked_at", title)

    private fun column(
        name: String,
        title: String,
    ): String? =
        app
            .jdbc()
            .sql("SELECT CAST($name AS TEXT) FROM items WHERE title = ?")
            .param(title)
            .query(String::class.java)
            .list()
            .firstOrNull()

    private companion object {
        const val BLOG = "Blog"
        const val ARTICLE_PHRASE = "Lorem ipsum dolor sit amet"
        const val SENTENCE_REPEAT_UNIT = 70
        const val TWO_MB = 2_000_000
        const val EIGHT_SECONDS_MILLIS = 8_000L
        const val TWO_SECONDS_MILLIS = 2_000L
        const val TEN_SECONDS = 10L
        const val POLL_MILLIS = 10L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
