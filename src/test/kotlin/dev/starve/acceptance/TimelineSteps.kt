package dev.starve.acceptance

import dev.starve.application.ItemRepository
import dev.starve.application.SourceRepository
import dev.starve.domain.FeedItem
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant

class TimelineSteps {
    private val app = World.app
    private var seededTitles: List<String> = emptyList()

    // ---- seeding -------------------------------------------------------------------------

    @Given("the source {string} has an unread item {string} published {int} days ago")
    fun unreadItem(
        source: String,
        title: String,
        days: Int,
    ) = seed(source, title, daysAgo(days))

    @Given("the source {string} has an unread item {string} published {int} days ago with a thumbnail")
    fun unreadItemWithThumbnail(
        source: String,
        title: String,
        days: Int,
    ) {
        seed(source, title, daysAgo(days))
        app
            .jdbc()
            .sql("UPDATE items SET thumbnail_url = ? WHERE title = ?")
            .params("http://img.test/$title.jpg", title)
            .update()
    }

    @Given("the source {string} has a read item {string} published {int} days ago")
    fun readItem(
        source: String,
        title: String,
        days: Int,
    ) {
        seed(source, title, daysAgo(days))
        app.bean(ItemRepository::class.java).markRead(itemId(title), Instant.now())
    }

    @Given(
        "the source {string} has unread items {string}, {string} and {string} " +
            "stored in that order with the same published date",
    )
    fun tiedItems(
        source: String,
        first: String,
        second: String,
        third: String,
    ) {
        val published = daysAgo(1)
        listOf(first, second, third).forEach { seed(source, it, published) }
    }

    @Given("the source {string} has {int} unread items published at one-minute intervals")
    fun manyItems(
        source: String,
        count: Int,
    ) {
        val now = Instant.now()
        seededTitles = (1..count).map { "N-%03d".format(it) }
        seededTitles.forEachIndexed { index, title -> seed(source, title, now.minus(Duration.ofMinutes(index + 1L))) }
    }

    @Given("the source {string} receives a new unread item {string} after that page was rendered")
    fun lateItem(
        source: String,
        title: String,
    ) = seed(source, title, Instant.now(), firstSeen = renderedAt().plusSeconds(1))

    // ---- actions -------------------------------------------------------------------------

    @When("I open the item {string}")
    fun openItem(title: String) = openId(itemId(title))

    @When("I open the unknown item {int}")
    fun openUnknown(id: Int) = openId(id.toLong())

    @When("I mark the item {string} read")
    fun markItemRead(title: String) = markId(itemId(title))

    @When("I mark read the unknown item {int}")
    fun markUnknownRead(id: Int) = markId(id.toLong())

    @When("I mark the item {string} read via htmx")
    fun markItemReadViaHtmx(title: String) {
        World.response = app.postForm("/items/${itemId(title)}/read", mapOf("HX-Request" to "true"))
    }

    @When("I mark all read from the page I received")
    fun markAllRead() {
        val before = "before" to renderedAt().toEpochMilli().toString()
        val group = GROUP.find(World.response.body())?.groupValues?.get(1)
        val fields = listOfNotNull(before, group?.let { "group" to it })
        World.response = app.postForm("/read-all", *fields.toTypedArray())
    }

    @When("I load more")
    fun loadMore() {
        val href = loadMoreTag(World.response.body())?.let { HREF.find(it)?.groupValues?.get(1) }
        World.response = app.get(requireNotNull(href) { "no Load more control" }.replace("&amp;", "&"), HTMX)
        World.pages = World.pages + World.response.body()
    }

    // ---- assertions ----------------------------------------------------------------------

    @Then("the response redirects to the link of {string}")
    fun redirectsToLink(title: String) {
        val link = scalar("SELECT link FROM items WHERE id = ?", itemId(title))
        assertEquals(
            link,
            World.response
                .headers()
                .firstValue("Location")
                .orElse(null),
        )
    }

    @Then("the item {string} is read")
    fun itemIsRead(title: String) = assertTrue(isRead(title), "'$title' is unread")

    @Then("the item {string} is unread")
    fun itemIsUnread(title: String) = assertFalse(isRead(title), "'$title' is read")

    @Then("the timeline lists the items in this order")
    fun listsInOrder(table: DataTable) = assertEquals(table.asList(), titles())

    @Then("the timeline lists {string}")
    fun listsItem(title: String) = assertTrue(title in titles(), "timeline lacks '$title': ${titles()}")

    @Then("the timeline does not list {string}")
    fun doesNotListItem(title: String) = assertFalse(title in titles(), "timeline has '$title'")

    @Then("the timeline lists no items")
    fun listsNothing() = assertEquals(emptyList<String>(), titles())

    @Then("the timeline lists {int} items")
    fun listsCount(count: Int) = assertEquals(count, titles().size)

    @Then("the timeline lists every item exactly once, newest first")
    fun listsEveryItemOnce() = assertEquals(seededTitles, titles())

    @Then("the timeline offers {string}")
    fun offers(text: String) = assertTrue(loadMoreTag(World.response.body()) != null && text == "Load more")

    @Then("the timeline does not offer {string}")
    fun doesNotOffer(text: String) = assertTrue(loadMoreTag(World.response.body()) == null && text == "Load more")

    @Then("the page does not contain {string}")
    fun pageLacks(text: String) = assertFalse(World.response.body().contains(text), "page has '$text'")

    @Then("the page contains a viewport meta tag")
    fun hasViewport() = assertTrue(World.response.body().contains("<meta name=\"viewport\""))

    @Then("the timeline entry {string} shows the source {string}")
    fun entryShowsSource(
        title: String,
        source: String,
    ) = assertTrue(entry(title).contains(">$source<"), "no source '$source' in ${entry(title)}")

    @Then("the timeline entry {string} shows a published date")
    fun entryShowsDate(title: String) = assertTrue(DATE.containsMatchIn(entry(title)))

    @Then("the timeline entry {string} shows a thumbnail")
    fun entryShowsThumbnail(title: String) = assertTrue(entry(title).contains("<img "))

    @Then("the timeline entry {string} shows no thumbnail")
    fun entryShowsNoThumbnail(title: String) = assertFalse(entry(title).contains("class=\"thumb\""))

    @Then("the timeline entry {string} is marked as read")
    fun entryMarkedRead(title: String) = assertTrue(isReadEntry(title))

    @Then("the timeline entry {string} is not marked as read")
    fun entryNotMarkedRead(title: String) = assertFalse(isReadEntry(title))

    // ---- helpers -------------------------------------------------------------------------

    private fun daysAgo(days: Int): Instant = Instant.now().minus(Duration.ofDays(days.toLong()))

    private fun seed(
        source: String,
        title: String,
        published: Instant,
        firstSeen: Instant = Instant.now(),
    ) {
        val sources = app.bean(SourceRepository::class.java)
        val owner =
            sources.findAll().firstOrNull { it.title == source }
                ?: sources.insert("http://$source.test/feed", source)
        val item = FeedItem(title, title, "http://example.test/${title.replace(' ', '-')}", published, null, null)
        app.bean(ItemRepository::class.java).insertNew(owner.id, listOf(item), firstSeen)
    }

    private fun scalar(
        sql: String,
        param: Any,
    ): String =
        app
            .jdbc()
            .sql(sql)
            .param(param)
            .query(String::class.java)
            .single()

    private fun itemId(title: String): Long =
        app
            .jdbc()
            .sql("SELECT id FROM items WHERE title = ?")
            .param(title)
            .query(Long::class.java)
            .single()

    private fun isRead(title: String): Boolean =
        app
            .jdbc()
            .sql("SELECT COUNT(*) FROM items WHERE title = ? AND read_at IS NOT NULL")
            .param(title)
            .query(Long::class.java)
            .single() == 1L

    private fun openId(id: Long) {
        World.response = app.get("/items/$id/open")
    }

    private fun markId(id: Long) {
        World.response = app.postForm("/items/$id/read")
    }

    private fun renderedAt(): Instant {
        val millis = requireNotNull(RENDERED.find(World.response.body())) { "page has no render timestamp" }
        return Instant.ofEpochMilli(millis.groupValues[1].toLong())
    }

    private fun titles(): List<String> = TITLE.findAll(World.pages.joinToString("")).map { it.groupValues[1] }.toList()

    private fun entry(title: String): String = World.entry(title)

    private fun isReadEntry(title: String): Boolean = READ_CLASS.containsMatchIn(entry(title).substringBefore('>'))

    private fun loadMoreTag(body: String): String? = LOAD_MORE.find(body)?.value

    private companion object {
        val HTMX = mapOf("HX-Request" to "true")
        val TITLE = Regex("<article[^>]*data-title=\"([^\"]*)\"")
        val RENDERED = Regex("name=\"before\"[^>]*value=\"(\\d+)\"")
        val GROUP = Regex("name=\"group\"[^>]*value=\"(\\d+)\"")
        val LOAD_MORE = Regex("<a[^>]*class=\"load-more\"[^>]*>")
        val DATE = Regex("<time [^>]*>\\d{4}-\\d{2}-\\d{2}")
        val HREF = Regex("href=\"([^\"]*)\"")
        val READ_CLASS = Regex("class=\"item read\"")
    }
}
