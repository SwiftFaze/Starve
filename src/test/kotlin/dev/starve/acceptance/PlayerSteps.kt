package dev.starve.acceptance

import dev.starve.application.ItemRepository
import dev.starve.application.SourceRepository
import dev.starve.domain.FeedItem
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant

class PlayerSteps {
    private val app = World.app

    @Before
    fun resetOembed() = World.oembed.clearRoutes()

    @Given("the YouTube oEmbed endpoint responds to the video {string} with HTTP {int}")
    fun oembedResponds(
        videoId: String,
        status: Int,
    ) = World.oembed.routeOembed(videoId, status)

    @Given("the source {string} has an unread item {string} with the link {string} published {int} days ago")
    fun unreadItemWithLink(
        source: String,
        title: String,
        link: String,
        days: Int,
    ) {
        val sources = app.bean(SourceRepository::class.java)
        val owner =
            sources.findAll().firstOrNull { it.title == source }
                ?: sources.insert("http://$source.test/feed", source)
        val published = Instant.now().minus(Duration.ofDays(days.toLong()))
        val item = FeedItem(title, title, link, published, null, null)
        app.bean(ItemRepository::class.java).insertNew(owner.id, listOf(item), Instant.now())
    }

    @When("I request the player of the item {string} via htmx")
    fun requestPlayer(title: String) = requestPlayerOf(itemId(title))

    @When("I request the player of the unknown item {int}")
    fun requestUnknownPlayer(id: Int) = requestPlayerOf(id.toLong())

    @Then("the timeline entry {string} has a Play control")
    fun hasPlayControl(title: String) = assertTrue(playButton(title) != null, "no Play control: ${World.entry(title)}")

    @Then("the timeline entry {string} has no Play control")
    fun hasNoPlayControl(title: String) = assertTrue(playButton(title) == null, "Play control in ${World.entry(title)}")

    @Then("the response contains an iframe whose source is {string}")
    fun iframeSource(source: String) = assertEquals(source, attribute("src"), World.response.body())

    @Then("the iframe allows {string}")
    fun iframeAllows(allow: String) = assertEquals(allow, attribute("allow"))

    @Then("the iframe allows fullscreen")
    fun iframeAllowsFullscreen() = assertTrue(iframeTag().contains("allowfullscreen"), iframeTag())

    @Then("the player is wrapped in a responsive 16:9 container")
    fun responsiveWrapper() = assertTrue(WRAPPER.containsMatchIn(World.response.body()))

    @Then("the response contains a link to {string}")
    fun containsLink(url: String) = assertTrue(World.response.body().contains("href=\"$url\""), World.response.body())

    @Then("the response contains no iframe")
    fun containsNoIframe() = assertFalse(World.response.body().contains("<iframe"))

    private fun requestPlayerOf(id: Long) {
        World.response = app.get("/items/$id/player", mapOf("HX-Request" to "true"))
    }

    private fun playButton(title: String): String? = PLAY.find(World.entry(title))?.value

    private fun iframeTag(): String {
        val body = World.response.body()
        return requireNotNull(IFRAME.find(body)) { "no iframe in $body" }.value
    }

    private fun attribute(name: String): String =
        requireNotNull(Regex("""\s$name="([^"]*)"""").find(iframeTag())) { "no $name in ${iframeTag()}" }
            .groupValues[1]
            .replace("&amp;", "&")

    private fun itemId(title: String): Long =
        app
            .jdbc()
            .sql("SELECT id FROM items WHERE title = ?")
            .param(title)
            .query(Long::class.java)
            .single()

    private companion object {
        val WRAPPER = Regex("""class="ratio-16x9"[^>]*>\s*<iframe""")
        val IFRAME = Regex("<iframe[^>]*>")
        val PLAY = Regex("<button[^>]*class=\"play\"[^>]*>")
    }
}
