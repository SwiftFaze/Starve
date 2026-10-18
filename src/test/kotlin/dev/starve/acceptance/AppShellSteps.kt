package dev.starve.acceptance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.imageio.ImageIO

/** Steps for pwa-shell.feature: the web app manifest, the app icons and the head links to them. */
class AppShellSteps {
    private fun body() = World.response.body()

    private fun manifest(): JsonNode = ObjectMapper().readTree(body())

    private fun head(): String = body().substringBefore("</head>")

    @Then("the response content type is {string}")
    fun contentType(type: String) =
        assertEquals(
            type,
            World.response
                .headers()
                .firstValue("Content-Type")
                .orElse("")
                .substringBefore(';'),
        )

    @Then("the manifest says")
    fun manifestSays(table: DataTable) {
        val manifest = manifest()
        table.asLists().forEach { (key, value) -> assertEquals(value, manifest.path(key).asText(), key) }
    }

    @Then("the manifest lists the icons")
    fun manifestIcons(table: DataTable) {
        val fields = listOf("src", "sizes", "purpose")
        val listed = manifest().path("icons").map { icon -> fields.associateWith { icon.text(it) } }
        assertEquals(table.asMaps(), listed)
        assertTrue(manifest().path("icons").all { it.text("type") == "image/png" }, body())
    }

    @Then("the response is a PNG image of {int} by {int} pixels")
    fun pngOfSize(
        width: Int,
        height: Int,
    ) {
        val path = World.response.uri().path
        val request = HttpRequest.newBuilder(URI.create(World.app.baseUrl() + path)).GET().build()
        val bytes = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray()).body()
        val image = requireNotNull(ImageIO.read(ByteArrayInputStream(bytes))) { "not an image: $path" }
        assertEquals(width to height, image.width to image.height)
    }

    @Then("the page head links the manifest and the touch icon")
    fun headLinks() {
        assertTrue(head().contains("<link rel=\"manifest\" href=\"/manifest.webmanifest\">"), head())
        assertTrue(head().contains("<link rel=\"apple-touch-icon\" href=\"/apple-touch-icon.png\">"), head())
    }

    @Then("the page head has the theme colour {string} for the dark theme and {string} for the light one")
    fun themeColours(
        dark: String,
        light: String,
    ) {
        assertTrue(head().contains(themeColor(dark, "dark")), head())
        assertTrue(head().contains(themeColor(light, "light")), head())
    }

    @Then("the link {string} opens in a new browsing context")
    fun opensOutside(text: String) {
        val anchor = requireNotNull(Html.anchor(body(), text)) { "no link '$text': ${body()}" }
        assertTrue(anchor.contains("target=\"_blank\""), anchor)
        assertTrue(anchor.contains("rel=\"noopener noreferrer\""), anchor)
    }

    private fun themeColor(
        colour: String,
        scheme: String,
    ) = "<meta name=\"theme-color\" content=\"$colour\" media=\"(prefers-color-scheme: $scheme)\">"

    private fun JsonNode.text(field: String): String = path(field).asText()
}
