package dev.starve.acceptance

import dev.starve.application.ItemRepository
import dev.starve.domain.FeedItem
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant

/** The statistics line and marks of each source on Sources, and the open marker (source-health-stats.feature). */
class SourceHealthSteps {
    private val app = World.app
    private val legacy = LegacyDatabase(app)

    @Given("the source {string} has {int} unread item(s) published {int} days ago")
    fun unreadItems(
        source: String,
        count: Int,
        days: Int,
    ) {
        insert(source, count, "unread-$days", app.clock.instant().minus(Duration.ofDays(days.toLong())))
    }

    @Given("the source {string} has {int} item(s) read without being opened")
    fun skippedItems(
        source: String,
        count: Int,
    ) = mark(insert(source, count, "skipped", app.clock.instant()), "read_at")

    @Given("the source {string} has {int} item(s) read after being opened")
    fun openedItems(
        source: String,
        count: Int,
    ) {
        val ids = insert(source, count, "opened", app.clock.instant())
        mark(ids, "read_at")
        mark(ids, "opened_at")
    }

    @Given("the database is migrated only up to before open tracking")
    fun migratedBeforeOpenTracking() = legacy.migrateOnlyTo("13")

    @Given("it holds a source {string} with {int} items read before the upgrade")
    fun heldReadItems(
        source: String,
        count: Int,
    ) = legacy.holdRead(source, count)

    @Then("the stats of the source {string} read {string}")
    fun statsRead(
        source: String,
        text: String,
    ) = assertEquals(text, textOf(group(row(source), STATS).replace(MARK, "")))

    @Then("the source {string} is marked {string}")
    fun marked(
        source: String,
        mark: String,
    ) = assertTrue(marks(source).contains(mark), "marks of $source: ${marks(source)}")

    @Then("the source {string} is not marked")
    fun notMarked(source: String) = assertEquals(emptyList<String>(), marks(source))

    @Then("the item {string} is recorded as opened")
    fun recordedOpened(title: String) = assertNotNull(column(title, "opened_at"))

    @Then("the item {string} is read but not recorded as opened")
    fun readNotOpened(title: String) {
        assertNotNull(column(title, "read_at"), "$title is not read")
        assertNull(column(title, "opened_at"))
    }

    private fun insert(
        source: String,
        count: Int,
        kind: String,
        published: Instant,
    ): List<Long> {
        val id = app.ownedSource(source).id
        val items =
            (1..count).map {
                FeedItem("$source-$kind-$it", "$source $kind $it", null, published, null, null)
            }
        app.bean(ItemRepository::class.java).insertNew(id, items, app.clock.instant())
        return items.map { app.itemId(it.title) }
    }

    private fun mark(
        ids: List<Long>,
        column: String,
    ) {
        val at = app.clock.instant().toEpochMilli()
        ids.forEach {
            app
                .jdbc()
                .sql("UPDATE items SET $column = ? WHERE id = ?")
                .params(at, it)
                .update()
        }
    }

    private fun column(
        title: String,
        column: String,
    ): Long? =
        app
            .jdbc()
            .sql("SELECT $column FROM items WHERE id = ?")
            .param(app.itemId(title))
            .query { rs, _ -> rs.getLong(1).takeUnless { rs.wasNull() } }
            .list()
            .single()

    private fun marks(source: String): List<String> =
        MARK
            .findAll(group(row(source), STATS))
            .map { textOf(it.groupValues[1]) }
            .toList()

    private fun row(source: String): String {
        val title = Regex.escape(source)
        val row = Regex("<li class=\"source\" data-title=\"$title\">(.*?)</li>", RegexOption.DOT_MATCHES_ALL)
        return group(World.response.body(), row)
    }

    private fun group(
        text: String,
        pattern: Regex,
    ): String = requireNotNull(pattern.find(text)) { "no $pattern in $text" }.groupValues[1]

    private fun textOf(html: String): String = html.replace(TAG, "").replace(SPACE, " ").trim()

    private companion object {
        val STATS = Regex("<p class=\"stats\"[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
        val MARK = Regex("<span class=\"tag mark\">(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
        val TAG = Regex("<[^>]+>")
        val SPACE = Regex("\\s+")
    }
}
