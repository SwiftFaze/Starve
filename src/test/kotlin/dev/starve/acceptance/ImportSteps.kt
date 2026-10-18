package dev.starve.acceptance

import io.cucumber.datatable.DataTable
import io.cucumber.java.DataTableType
import io.cucumber.java.DocStringType
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** OPML text for the feed server: written out as a doc string, or given as a table of feed paths. */
class FeedOpml(
    val text: String,
)

class ImportSteps {
    private val app = World.app
    private var importPage = ""

    @Given("YouTube feeds are configured to be served by the feed server")
    fun youtubeUsesFeedServer() =
        app.restartWith("--starve.youtube.feed-url-template=${World.feedServer().baseUrl}$YOUTUBE_PATH?channel_id={id}")

    @DocStringType
    fun opmlText(text: String) = FeedOpml(text)

    /** One feed per row, the first column being its path on the feed server. */
    @DataTableType
    fun opmlOfPaths(table: DataTable): FeedOpml {
        val outlines =
            table.cells().joinToString("") { """<outline type="rss" title="Feed" xmlUrl="$FEED_SERVER${it[0]}"/>""" }
        return FeedOpml("""<?xml version="1.0" encoding="UTF-8"?><opml version="2.0"><body>$outlines</body></opml>""")
    }

    @Given("the feed server records requests")
    fun feedServerRecordsRequests() = Unit

    // ---- actions -------------------------------------------------------------------------

    @When("I import the file {string} containing:")
    fun importFile(
        name: String,
        content: String,
    ) = act(name, content.toByteArray())

    @Given("I have imported the file {string} containing:")
    fun haveImportedFile(
        name: String,
        content: String,
    ) = upload(name, content.toByteArray())

    @When("I import the file {string} with a byte order mark and CRLF line endings containing:")
    fun importFileWithBomAndCrlf(
        name: String,
        content: String,
    ) = act(name, (BOM + content.replace("\n", "\r\n")).toByteArray())

    @When("I import the file {string} containing the feed server OPML:")
    fun importOpml(
        name: String,
        content: FeedOpml,
    ) = act(name, withFeedServer(content.text))

    @Given("I have imported the file {string} containing the feed server OPML:")
    fun haveImportedOpml(
        name: String,
        content: FeedOpml,
    ) = upload(name, withFeedServer(content.text))

    @When("I import the file {string} containing the feed server OPML without waiting for the background poll:")
    fun importOpmlWithoutWaiting(
        name: String,
        content: FeedOpml,
    ) = act(name, withFeedServer(content.text))

    @When("I import a file {string} of {int} MB")
    fun importLargeFile(
        name: String,
        megabytes: Int,
    ) = act(name, ByteArray(megabytes * BYTES_PER_MB) { 'a'.code.toByte() })

    @When("the import's background poll completes")
    fun backgroundPollCompletes() = app.importRunner.runPending()

    // ---- assertions ----------------------------------------------------------------------

    @Then("the import summary shows {int} added, {int} already subscribed and {int} failed")
    fun summaryShows(
        added: Int,
        already: Int,
        failed: Int,
    ) = assertEquals(
        listOf(added, already, failed),
        listOf("added", "already-subscribed", "failed").map { count(it) },
        "summary on: $importPage",
    )

    @Then("the import summary lists the failure {string}")
    fun summaryListsFailure(reason: String) = assertTrue(failures().contains(reason), "no $reason in ${failures()}")

    @Then("the import page shows the message {string}")
    fun pageShowsMessage(message: String) = assertTrue(Html.unescape(importPage).contains(message), "no $message")

    @Then("the import response arrived before any request for {string}")
    fun responseBeforeRequest(path: String) {
        assertEquals(emptyList<RecordedRequest>(), requestsFor(path), "fetched during the import request")
        app.importRunner.runPending()
        assertTrue(requestsFor(path).isNotEmpty(), "the background poll never fetched $path")
    }

    @Then("the feed server received no request for {string}")
    fun noRequestFor(path: String) = assertEquals(emptyList<RecordedRequest>(), requestsFor(path))

    @Then("the source {string} is subscribed at the YouTube feed for channel {string}")
    fun subscribedAtYoutubeFeed(
        title: String,
        channelId: String,
    ) {
        val url =
            app
                .jdbc()
                .sql("SELECT url FROM sources WHERE title = ?")
                .param(title)
                .query(String::class.java)
                .single()
        assertEquals("${World.feedServer().baseUrl}$YOUTUBE_PATH?channel_id=$channelId", url)
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun act(
        name: String,
        content: ByteArray,
    ) {
        World.actionHasRun = true
        upload(name, content)
    }

    private fun upload(
        name: String,
        content: ByteArray,
    ) {
        importPage = app.postFile("/import", "file", name, content).body()
    }

    private fun withFeedServer(content: String) = content.replace(FEED_SERVER, World.feedServer().baseUrl).toByteArray()

    private fun failures() = FAILURE.findAll(importPage).map { Html.unescape(it.groupValues[1]) }.toList()

    private fun requestsFor(path: String) = World.feedServer().requests.filter { it.path == path }

    private fun count(id: String): Int =
        requireNotNull(Regex("id=\"$id\">(\\d+)<").find(importPage)) { "no $id in $importPage" }.groupValues[1].toInt()

    @Then("the bar marks only {string} as the current page")
    fun barMarksOnlyCurrent(label: String) {
        val bar = requireNotNull(BAR.find(World.response.body())) { "no bar" }.value
        val current = BAR_LINK.findAll(bar).filter { it.groupValues[1].contains("aria-current=\"page\"") }
        assertEquals(listOf(label), current.map { it.groupValues[2] }.toList(), bar)
    }

    private companion object {
        val BAR = Regex("<header class=\"bar\".*?</header>", RegexOption.DOT_MATCHES_ALL)
        val BAR_LINK = Regex("(<a [^>]*>)([^<]*)</a>")
        const val YOUTUBE_PATH = "/youtube/feeds/videos.xml"
        const val BYTES_PER_MB = 1024 * 1024
        const val FEED_SERVER = "{feedserver}"
        val BOM = Char(0xFEFF).toString()
        val FAILURE = Regex("<li class=\"failure\">([^<]*)</li>")
    }
}
