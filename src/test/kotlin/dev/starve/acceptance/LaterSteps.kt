package dev.starve.acceptance

import dev.starve.application.ItemRepository
import dev.starve.application.LaterRepository
import dev.starve.application.SourceRepository
import dev.starve.domain.FeedItem
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant

/** Steps for read-later.feature: the ⏲ toggles, the Later view, retention and the upgrade. */
class LaterSteps {
    private val app = World.app
    private val legacy = LegacyDatabase(app)

    // ---- fixture setup -------------------------------------------------------------------

    /** Also an arrangement before the first action, and an assertion after it (see [World.actionHasRun]). */
    @Given("the item {string} is saved for later")
    fun itemIsSaved(title: String) {
        if (World.actionHasRun) {
            assertTrue(isSaved(title), "'$title' is not saved for later")
        } else {
            later().save(itemId(title), Instant.now())
        }
    }

    @Given("the item first seen {int} days ago is saved for later")
    fun itemFirstSeenAgoIsSaved(days: Int) {
        later().save(idFirstSeenDaysAgo(days), Instant.now())
    }

    @Given("the items {string}, {string} and {string} are saved for later at the same instant")
    fun itemsSavedAtSameInstant(
        first: String,
        second: String,
        third: String,
    ) {
        val at = Instant.now()
        listOf(first, second, third).forEach { later().save(itemId(it), at) }
    }

    /** Saves every item in timeline order (newest first), one millisecond apart, so the first saved is the newest. */
    @Given("every item is saved for later in timeline order, oldest saved first")
    fun everyItemSavedInTimelineOrder() {
        val base = Instant.now().minus(Duration.ofDays(1))
        val ids =
            app
                .jdbc()
                .sql("SELECT id FROM items ORDER BY published_at DESC, id DESC")
                .query(Long::class.java)
                .list()
        ids.forEachIndexed { index, id -> later().save(id, base.plusMillis(index.toLong())) }
    }

    @Given("the source {string} has a YouTube item {string} published {int} days ago")
    fun youtubeItem(
        source: String,
        title: String,
        days: Int,
    ) {
        val sources = app.bean(SourceRepository::class.java)
        val owner =
            sources.findAll().firstOrNull { it.title == source }
                ?: sources.insert("http://$source.test/feed", source)
        val published = Instant.now().minus(Duration.ofDays(days.toLong()))
        val item = FeedItem(title, title, YOUTUBE, published, null, null)
        app.bean(ItemRepository::class.java).insertNew(owner.id, listOf(item), Instant.now())
    }

    @Given("the database is migrated only up to before read-later")
    fun migratedBeforeReadLater() = legacy.migrateOnlyTo("9")

    // ---- actions -------------------------------------------------------------------------

    @When("I save the item {string} for later via htmx")
    fun saveViaHtmx(title: String) = toggle("later", itemId(title), HTMX)

    @When("I save the item {string} for later")
    fun saveWithoutJavascript(title: String) = toggle("later", itemId(title))

    @When("I save the item {string} for later via htmx from the reader")
    fun saveFromReader(title: String) = toggle("later", itemId(title), HTMX, "?from=reader")

    @When("I save the item {string} for later via htmx from the detail view")
    fun saveFromDetail(title: String) = toggle("later", itemId(title), HTMX, "?from=detail")

    @When("I clear the item {string} from later via htmx")
    fun clearViaHtmx(title: String) = toggle("unlater", itemId(title), HTMX)

    @When("I clear the item first seen {int} days ago from later")
    fun clearFirstSeenAgo(days: Int) = toggle("unlater", idFirstSeenDaysAgo(days))

    @When("I request the timeline of the group {string} with the Later filter")
    fun requestGroupWithLater(group: String) {
        val id =
            app
                .jdbc()
                .sql("SELECT id FROM groups WHERE name = ?")
                .param(group)
                .query(Long::class.java)
                .single()
        World.response = app.get("/feed?group=$id&later=1")
        World.pages = listOf(World.response.body())
    }

    @When("I mark the item {string} as read")
    fun markRead(title: String) {
        World.response = app.postForm("/items/${itemId(title)}/read")
    }

    @When("I mark all items read")
    fun markAllItemsRead() {
        val later = Instant.now().plusSeconds(60)
        World.response = app.postForm("/read-all", "before" to later.toEpochMilli().toString())
    }

    // ---- assertions on the toggles -------------------------------------------------------

    @Then("the timeline entry {string} has a saved later toggle")
    fun entryHasSavedToggle(title: String) = assertToggleState(World.entry(title), itemId(title), true)

    @Then("the timeline entry {string} has an unsaved later toggle")
    fun entryHasUnsavedToggle(title: String) = assertToggleState(World.entry(title), itemId(title), false)

    @Then("the later toggle of {string} saves it via htmx without a page reload")
    fun toggleSavesViaHtmx(title: String) {
        val id = itemId(title)
        assertTrue(toggleForm(World.entry(title), "later-$id").contains("hx-post=\"/items/$id/later\""))
    }

    @Then("the response shows a saved later toggle that clears {string} via htmx")
    fun responseShowsSaved(title: String) = assertResponseToggle(true, "unlater", itemId(title))

    @Then("the response shows an unsaved later toggle that saves {string} via htmx")
    fun responseShowsUnsaved(title: String) = assertResponseToggle(false, "later", itemId(title))

    @Then("the response also updates the later toggle of the timeline entry {string} out of band")
    fun responseUpdatesRowOutOfBand(title: String) {
        val tag = toggleForm(World.response.body(), "later-${itemId(title)}")
        assertTrue(tag.contains("hx-swap-oob=\"true\""), tag)
    }

    @Then("the reader has a saved later toggle")
    fun readerHasSavedToggle() = assertToggleState(World.response.body(), "reader-later", true)

    @Then("the reader has an unsaved later toggle")
    fun readerHasUnsavedToggle() = assertToggleState(World.response.body(), "reader-later", false)

    @Then("the reader's later toggle saves {string} via htmx")
    fun readerToggleSaves(title: String) = assertPaneToggle("reader", false, "later", itemId(title))

    @Then("the reader's later toggle clears {string} via htmx")
    fun readerToggleClears(title: String) = assertPaneToggle("reader", true, "unlater", itemId(title))

    @Then("the detail view has a saved later toggle")
    fun detailHasSavedToggle() = assertToggleState(World.response.body(), "detail-later", true)

    @Then("the detail view has an unsaved later toggle")
    fun detailHasUnsavedToggle() = assertToggleState(World.response.body(), "detail-later", false)

    @Then("the detail view's later toggle saves {string} via htmx")
    fun detailToggleSaves(title: String) = assertPaneToggle("detail", false, "later", itemId(title))

    @Then("the detail view's later toggle clears {string} via htmx")
    fun detailToggleClears(title: String) = assertPaneToggle("detail", true, "unlater", itemId(title))

    @Then("the item {string} is not saved for later")
    fun itemIsNotSaved(title: String) = assertFalse(isSaved(title), "'$title' is saved for later")

    // ---- assertions on the navigation and the Later view ---------------------------------

    @Then("the {string} entry follows the {string} entry")
    fun entryFollowsEntry(
        label: String,
        previous: String,
    ) {
        val anchors = World.navAnchors(World.response.body())
        val index = anchors.indexOfFirst { it.second == label }
        assertTrue(index > 0 && anchors[index - 1].second == previous, "'$label' does not follow '$previous': $anchors")
    }

    @Then("the {string} entry is not marked as current")
    fun entryNotMarkedCurrent(label: String) {
        val anchors = World.navAnchors(World.response.body())
        val tag = requireNotNull(anchors.firstOrNull { it.second == label }) { "no '$label' entry" }.first
        assertFalse(tag.contains("aria-current"), tag)
    }

    @Then("the timeline lists every item exactly once, earliest saved first")
    fun listsEveryItemEarliestSavedFirst() {
        val expected =
            app
                .jdbc()
                .sql("SELECT title FROM items WHERE later_at IS NOT NULL ORDER BY later_at ASC, id ASC")
                .query(String::class.java)
                .list()
        assertEquals(expected, titles())
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun later(): LaterRepository = app.bean(LaterRepository::class.java)

    private fun toggle(
        verb: String,
        id: Long,
        headers: Map<String, String> = emptyMap(),
        query: String = "",
    ) {
        World.actionHasRun = true
        World.response = app.postForm("/items/$id/$verb$query", headers)
    }

    private fun assertResponseToggle(
        saved: Boolean,
        verb: String,
        id: Long,
    ) {
        assertToggleState(World.response.body(), "later-$id", saved)
        assertToggleAction(World.response.body(), "later-$id", "/items/$id/$verb")
    }

    /** The toggle of the [pane] ("reader" or "detail") in the response: its id is "<pane>-later". */
    private fun assertPaneToggle(
        pane: String,
        saved: Boolean,
        verb: String,
        id: Long,
    ) {
        assertToggleState(World.response.body(), "$pane-later", saved)
        assertToggleAction(World.response.body(), "$pane-later", "/items/$id/$verb?from=$pane")
    }

    /** The whole form of the toggle with [domId] in [page]; the button sits inside it. */
    private fun toggleForm(
        page: String,
        domId: String,
    ): String =
        requireNotNull(Regex("<form[^>]*id=\"$domId\".*?</form>", RegexOption.DOT_MATCHES_ALL).find(page)) {
            "no toggle $domId in $page"
        }.value

    private fun assertToggleState(
        page: String,
        id: Long,
        saved: Boolean,
    ) = assertToggleState(page, "later-$id", saved)

    private fun assertToggleState(
        page: String,
        domId: String,
        saved: Boolean,
    ) {
        val form = toggleForm(page, domId)
        assertTrue(form.contains("data-later=\"$saved\""), form)
        assertTrue(form.contains("aria-pressed=\"$saved\""), form)
        assertEquals(saved, form.contains("class=\"saved\""), form)
    }

    private fun assertToggleAction(
        page: String,
        domId: String,
        action: String,
    ) = assertTrue(toggleForm(page, domId).contains("hx-post=\"$action\""), action)

    private fun isSaved(title: String): Boolean =
        app
            .jdbc()
            .sql("SELECT COUNT(*) FROM items WHERE title = ? AND later_at IS NOT NULL")
            .param(title)
            .query(Long::class.java)
            .single() == 1L

    private fun titles(): List<String> = TITLE.findAll(World.pages.joinToString("")).map { it.groupValues[1] }.toList()

    private fun itemId(title: String): Long =
        app
            .jdbc()
            .sql("SELECT id FROM items WHERE title = ?")
            .param(title)
            .query(Long::class.java)
            .single()

    /** The item first seen [days] ago; the backdating steps spread items a few days apart, so an hour is plenty. */
    private fun idFirstSeenDaysAgo(days: Int): Long {
        val at = Instant.now().minus(Duration.ofDays(days.toLong())).toEpochMilli()
        val hour = Duration.ofHours(1).toMillis()
        return app
            .jdbc()
            .sql("SELECT id FROM items WHERE first_seen_at BETWEEN ? AND ?")
            .params(at - hour, at + hour)
            .query(Long::class.java)
            .single()
    }

    private companion object {
        val HTMX = mapOf("HX-Request" to "true")
        const val YOUTUBE = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val TITLE = Regex("<article[^>]*data-title=\"([^\"]*)\"")
    }
}
