package dev.starve.acceptance

import dev.starve.application.ItemRepository
import dev.starve.application.SourceRepository
import dev.starve.domain.FeedItem
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant

/** Steps for docked-player.feature: the dock, the named player and the sheet-only swaps. */
class DockSteps {
    private val app = World.app

    @Given(
        "the source {string} has an unread item {string} with the link {string} " +
            "and a thumbnail published {int} days ago",
    )
    fun unreadItemWithThumbnail(
        source: String,
        title: String,
        link: String,
        days: Int,
    ) {
        val owner = app.ownedSource(source)
        val published = Instant.now().minus(Duration.ofDays(days.toLong()))
        val item = FeedItem(title, title, link, published, null, thumbnailOf(title))
        app.bean(ItemRepository::class.java).insertNew(owner.id, listOf(item), Instant.now())
    }

    @Then("the timeline has a player dock")
    fun hasDock() = assertTrue(body().contains("id=\"dock\""), "no dock in ${body()}")

    @Then("the Play control of {string} loads its player into the dock")
    fun playLoadsIntoDock(title: String) {
        val button = playButton(title)
        assertTrue(button.contains("hx-target=\"#dock\""), button)
    }

    @Then("the Play control of {string} replaces whatever the dock holds")
    fun playReplacesDock(title: String) {
        assertTrue(playButton(title).contains("hx-swap=\"innerHTML\""), playButton(title))
        assertTrue(!playButton(title).contains("hx-on::before-request"), playButton(title))
    }

    @Then("the response names the item {string} from {string} with its catalogue number")
    fun namesItem(
        title: String,
        source: String,
    ) {
        val number = "STV %04d".format(itemId(title))
        listOf(">$title<", ">$source<", ">$number<").forEach {
            assertTrue(body().contains(it), "no '$it' in ${body()}")
        }
    }

    @Then("the response has an Eject control")
    fun hasStop() = assertTrue(STOP.containsMatchIn(body()), body())

    @Then("the response contains the thumbnail of {string}")
    fun hasThumbnail(title: String) {
        val page = Html.unproxied(body())
        assertTrue(page.contains("<img ") && page.contains(thumbnailOf(title)), page)
    }

    @Then("every group link replaces only the release sheet via htmx")
    fun groupLinksSwapSheet() = groupLinks().forEach { assertSwapsSheet(it, "hx-get") }

    @Then("every group link pushes its URL to the browser history")
    fun groupLinksPushUrl() = groupLinks().forEach { assertTrue(it.contains("hx-push-url=\"true\""), it) }

    @Then("every group link still works without JavaScript")
    fun groupLinksHaveHref() = groupLinks().forEach { assertTrue(HREF.containsMatchIn(it), it) }

    @Then("the read toggle replaces only the release sheet via htmx")
    fun toggleSwapsSheet() {
        assertSwapsSheet(toggle(), "hx-get")
        assertTrue(toggle().contains("hx-push-url=\"true\""), toggle())
    }

    @Then("the read toggle still works without JavaScript")
    fun toggleHasHref() = assertTrue(HREF.containsMatchIn(toggle()), toggle())

    @Then("the Mark all read form replaces only the release sheet via htmx")
    fun markAllSwapsSheet() = assertSwapsSheet(markAllForm(), "hx-post")

    @Then("the Mark all read form still works without JavaScript")
    fun markAllHasAction() =
        assertTrue(
            markAllForm().contains("method=\"post\"") && markAllForm().contains("action=\"/read-all\""),
            markAllForm(),
        )

    private fun assertSwapsSheet(
        tag: String,
        verb: String,
    ) {
        val expected =
            listOf("$verb=\"", "hx-target=\"#sheet\"", "hx-select=\"#sheet\"", "hx-select-oob=\"#bar,#series\"")
        expected.forEach { assertTrue(tag.contains(it), "no $it in $tag") }
    }

    private fun body(): String = World.response.body()

    private fun playButton(title: String): String {
        val match = PLAY.find(World.entry(title))
        return requireNotNull(match) { "no Play control" }.value
    }

    private fun groupLinks(): List<String> =
        GROUP_LINK
            .findAll(body())
            .map { it.value }
            .toList()
            .also { assertTrue(it.isNotEmpty()) }

    private fun toggle(): String = requireNotNull(TOGGLE.find(body())) { "no read toggle" }.value

    private fun markAllForm(): String = requireNotNull(MARK_ALL.find(body())) { "no Mark all read form" }.value

    private fun thumbnailOf(title: String) = "http://example.test/$title.jpg"

    private fun itemId(title: String): Long = app.itemId(title)

    private companion object {
        val PLAY = Regex("<button[^>]*class=\"play\"[^>]*>")
        val STOP = Regex("<button[^>]*class=\"stop\"[^>]*>")
        val GROUP_LINK = Regex("<a class=\"group-link\"[^>]*>")
        val TOGGLE = Regex("<a class=\"read-toggle\"[^>]*>")
        val MARK_ALL = Regex("<form[^>]*action=\"/read-all\"[^>]*>")
        val HREF = Regex("href=\"/[^\"]*\"")
    }
}
