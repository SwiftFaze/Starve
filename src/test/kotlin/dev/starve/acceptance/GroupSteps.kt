package dev.starve.acceptance

import dev.starve.application.SourceRepository
import io.cucumber.datatable.DataTable
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class GroupSteps {
    private val app = World.app

    @Before
    fun resetSourcesPage() {
        World.sourcesPage = ""
        World.actionHasRun = false
    }

    // ---- actions -------------------------------------------------------------------------

    @Given("the group {string} exists")
    fun groupExists(name: String) = postGroup(name)

    @When("I create the group {string}")
    fun createGroup(name: String) {
        World.actionHasRun = true
        postGroup(name)
    }

    private fun postGroup(name: String) {
        World.sourcesPage = app.postForm("/groups", "name" to name).body()
    }

    @When("I rename the group {string} to {string}")
    fun renameGroup(
        name: String,
        newName: String,
    ) {
        World.actionHasRun = true
        World.sourcesPage = app.postForm("/groups/${groupId(name)}/rename", "name" to newName).body()
    }

    @When("I delete the group {string}")
    fun deleteGroup(name: String) {
        World.actionHasRun = true
        app.postForm("/groups/${groupId(name)}/delete")
    }

    /** Arranges the membership before the first action, and asserts it afterwards. */
    @Given("the source {string} is in the group {string}")
    fun sourceIsInGroup(
        source: String,
        group: String,
    ) {
        if (World.actionHasRun) assertEquals(group, groupNameOf(source)) else assign(source, groupId(group).toString())
    }

    @When("I assign the source {string} to the group {string}")
    fun assignToGroup(
        source: String,
        group: String,
    ) {
        World.actionHasRun = true
        assign(source, groupId(group).toString())
    }

    @When("I assign the source {string} to {string}")
    fun assignToUngrouped(
        source: String,
        option: String,
    ) {
        assertEquals("Ungrouped", option)
        World.actionHasRun = true
        assign(source, "")
    }

    @When("I request the timeline of the group {string}")
    fun requestGroupTimeline(group: String) = fetch("/?group=${groupId(group)}")

    @When("I request the timeline of the group {string} showing read items")
    fun requestGroupTimelineShowingRead(group: String) = fetch("/?show=all&group=${groupId(group)}")

    // ---- assertions on the sources page --------------------------------------------------

    @Then("the sources page lists the group {string}")
    fun sourcesPageListsGroup(name: String) = assertTrue(name in sourcesPageGroups(), "no group '$name'")

    @Then("the sources page does not list the group {string}")
    fun sourcesPageLacksGroup(name: String) = assertFalse(name in sourcesPageGroups(), "unexpected group '$name'")

    @Then("the sources page lists the groups in this order")
    fun sourcesPageGroupOrder(table: DataTable) = assertEquals(table.asList(), sourcesPageGroups())

    @Then("there are {int} groups")
    fun groupCountPlural(count: Int) = groupCount(count)

    @Then("there is {int} group")
    fun groupCount(count: Int) = assertEquals(count.toLong(), scalar("SELECT COUNT(*) FROM groups"))

    @Then("the source {string} is ungrouped")
    fun sourceUngrouped(source: String) = assertNull(groupNameOf(source))

    // ---- assertions on the home page navigation ------------------------------------------

    @Then("the group navigation lists the entries")
    fun navigationEntries(table: DataTable) = assertEquals(table.asList(), navigation().map { it.second })

    @Then("the group navigation lists the groups in this order")
    fun navigationGroupOrder(table: DataTable) =
        assertEquals(table.asList(), navigation().drop(1).map { it.second.substringBeforeLast(" (") })

    @Then("the group navigation marks {string} as current")
    fun navigationMarksCurrent(label: String) =
        assertEquals(listOf(label), navigation().filter { CURRENT.containsMatchIn(it.first) }.map { it.second })

    @Then("the {string} link keeps the group filter")
    fun linkKeepsGroup(text: String) {
        assertEquals("Load more", text)
        val tag = requireNotNull(LOAD_MORE.find(World.response.body())) { "no Load more control" }
        assertTrue(GROUP_PARAM.containsMatchIn(tag.value), "no group filter in ${tag.value}")
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun fetch(path: String) {
        World.response = app.get(path)
        World.pages = listOf(World.response.body())
    }

    private fun assign(
        source: String,
        group: String,
    ) {
        val sources = app.bean(SourceRepository::class.java)
        val owner =
            sources.findAll().firstOrNull { it.title == source }
                ?: sources.insert("http://$source.test/feed", source)
        app.postForm("/sources/${owner.id}/group", "group" to group)
    }

    private fun groupId(name: String): Long =
        app
            .jdbc()
            .sql("SELECT id FROM groups WHERE name = ?")
            .param(name)
            .query(Long::class.java)
            .single()

    private fun groupNameOf(source: String): String? =
        app
            .jdbc()
            .sql("SELECT g.name FROM sources s LEFT JOIN groups g ON g.id = s.group_id WHERE s.title = ?")
            .param(source)
            .query(String::class.java)
            .list()
            .single()

    private fun scalar(sql: String): Long =
        app
            .jdbc()
            .sql(sql)
            .query(Long::class.java)
            .single()

    private fun sourcesPageGroups(): List<String> {
        val page = app.get("/sources").body()
        return GROUP_ITEM.findAll(page).map { it.groupValues[1] }.toList()
    }

    /** The navigation links of the latest response as (tag, label) pairs. */
    private fun navigation(): List<Pair<String, String>> =
        NAV_LINK.findAll(World.response.body()).map { it.groupValues[1] to it.groupValues[2] }.toList()

    private companion object {
        val GROUP_ITEM = Regex("<li class=\"group\"[^>]*data-group=\"([^\"]*)\"")
        val NAV_LINK = Regex("(<a class=\"group-link\"[^>]*>)([^<]*)</a>")
        val CURRENT = Regex("aria-current=\"page\"")
        val LOAD_MORE = Regex("<a[^>]*class=\"load-more\"[^>]*>")
        val GROUP_PARAM = Regex("href=\"[^\"]*(?:\\?|&amp;|&)group=\\d+")
    }
}
