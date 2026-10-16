package dev.starve.acceptance

import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class PlayerSteps {
    private val app = World.app

    @Before
    fun resetOembed() = World.oembed.clearRoutes()

    @Given("the YouTube oEmbed endpoint responds to the video {string} with HTTP {int}")
    fun oembedResponds(
        videoId: String,
        status: Int,
    ) = World.oembed.routeOembed(videoId, status)

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

    @Then("nothing covers the YouTube video")
    fun nothingCoversVideo() = assertFalse(World.response.body().contains("class=\"shield\""), World.response.body())

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

    private fun itemId(title: String): Long = app.itemId(title)

    private companion object {
        val WRAPPER = Regex("""class="ratio-16x9"[^>]*>\s*<iframe""")
        val IFRAME = Regex("<iframe[^>]*>")
        val PLAY = Regex("<button[^>]*class=\"play\"[^>]*>")
    }
}
