package dev.starve.acceptance

import dev.starve.application.ImageCacheService
import dev.starve.application.ItemRepository
import dev.starve.domain.FeedItem
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.time.Instant

/** Steps for image-proxy.feature: images served through Starve's signed image route, and its cache. */
class ImageProxySteps {
    private val app = World.app

    @Given("the source {string} has an unread item {string} whose stored thumbnail is {string} on the feed server's numeric address")
    fun thumbnailOnNumericAddress(
        source: String,
        title: String,
        path: String,
    ) {
        val numeric = World.feedServer().baseUrl.replace("localhost", "127.0.0.1") + path
        val item = FeedItem(title, title, "https://example.com/$title", Instant.now(), null, numeric)
        app.bean(ItemRepository::class.java).insertNew(app.ownedSource(source).id, listOf(item), Instant.now())
    }

    @When("I request the image of the timeline entry {string}")
    fun requestImage(title: String) = get(imageOf(title))

    @When("I request the image of the timeline entry {string} with its signature changed")
    fun requestWithChangedSignature(title: String) = get(imageOf(title).replace(Regex("sig=.(.*)$"), "sig=A$1"))

    @When("I request the image of the timeline entry {string} for another address with its signature")
    fun requestOtherAddress(title: String) = get(imageOf(title).replace("thumb.png", "other.png"))

    @When("I request the image of the timeline entry {string} without a signature")
    fun requestUnsigned(title: String) = get(imageOf(title).substringBefore("&sig="))

    @When("I request the image of the timeline entry {string} signed in as {string}")
    fun requestAsOther(
        title: String,
        username: String,
    ) {
        val path = imageOf(title)
        app.signIn(username)
        get(path)
    }

    @When("the item {string} is deleted from the database")
    fun deleteItem(title: String) {
        app
            .jdbc()
            .sql("DELETE FROM items WHERE title = ?")
            .param(title)
            .update()
    }

    @When("the image cache is pruned")
    fun prune() {
        app.bean(ImageCacheService::class.java).prune()
    }

    @Then("every image on the page comes from Starve's image route")
    fun everyImageProxied() {
        val sources = IMG_SRC.findAll(World.response.body()).map { it.groupValues[1] }.toList()
        assertTrue(sources.isNotEmpty(), World.response.body())
        assertTrue(sources.all { it.startsWith("/images?src=") }, sources.toString())
    }

    @Then("the response is an image of type {string}, cached privately for a year")
    fun cachedImage(type: String) {
        val headers = World.response.headers()
        assertEquals(type, headers.firstValue("Content-Type").orElse(""))
        val cache = headers.firstValue("Cache-Control").orElse("")
        assertTrue(cache.contains("max-age=31536000") && cache.contains("private") && cache.contains("immutable"), cache)
        assertTrue(headers.firstValue("Content-Security-Policy").orElse("").contains("sandbox"))
    }

    @Then("the response has the header {string} set to {string}")
    fun responseHeader(
        name: String,
        value: String,
    ) = assertEquals(
        value,
        World.response
            .headers()
            .firstValue(name)
            .orElse(null),
    )

    @Then("the image cache holds {int} image")
    fun cacheHolds(count: Int) {
        val dir =
            app.dbPath
                .toAbsolutePath()
                .parent
                .resolve("images")
        val files = Files.list(dir).use { stream -> stream.filter { Files.isRegularFile(it) }.count() }
        assertEquals(count.toLong(), files)
    }

    /** The image route the timeline entry [title] shows its picture through, as a path to request. */
    private fun imageOf(title: String): String {
        val entry =
            World.pages
                .joinToString("")
                .split("<article")
                .first { it.contains("data-title=\"$title\"") }
        val src = requireNotNull(IMG_SRC.find(entry)) { "no image in $entry" }.groupValues[1]
        return src.replace("&amp;", "&")
    }

    private fun get(path: String) {
        World.response = app.get(path)
    }

    private companion object {
        val IMG_SRC = Regex("<img [^>]*src=\"([^\"]*)\"")
    }
}
