package dev.starve.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for keyword-tags.feature: tags made on Sources and the timeline views they add. */
class TagSteps {
    private val app = World.app

    // ---- actions -------------------------------------------------------------------------

    @Given("the tag {string} exists with the terms {string}")
    fun tagExists(
        name: String,
        terms: String,
    ) = createTag(name, terms)

    @When("I create the tag {string} with the terms {string}")
    fun createTag(
        name: String,
        terms: String,
    ) = postTag("/tags", "name" to name, "terms" to terms)

    @When("I create the tag {string} with the terms {string} limited to the group {string}")
    fun createLimitedTag(
        name: String,
        terms: String,
        group: String,
    ) = postTag("/tags", "name" to name, "terms" to terms, "group" to app.groupId(group).toString())

    @When("I create the tag {string} with the terms {string} matching the body too")
    fun createBodyTag(
        name: String,
        terms: String,
    ) = postTag("/tags", "name" to name, "terms" to terms, "body" to "1")

    @When("I edit the tag {string} to the terms {string}")
    fun editTerms(
        name: String,
        terms: String,
    ) = postTag("/tags/${tagId(name)}", "name" to name, "terms" to terms)

    @When("I ask to delete the tag {string}")
    fun askToDelete(name: String) {
        World.response = app.get("/sources?delete-tag=${tagId(name)}")
    }

    @When("I delete the tag {string}")
    fun delete(name: String) {
        World.response = app.postForm("/tags/${tagId(name)}/delete")
    }

    @When("I request the timeline of the tag {string}")
    fun requestTagTimeline(name: String) = fetch("/feed?tag=${tagId(name)}")

    @When("I request the timeline of the tag {string} showing read items")
    fun requestTagTimelineShowingRead(name: String) = fetch("/feed?show=all&tag=${tagId(name)}")

    // ---- assertions ----------------------------------------------------------------------

    @Then("the sources page lists the tag {string} with the terms {string}")
    fun listsTag(
        name: String,
        terms: String,
    ) = assertTrue(listedTags().contains(name to terms), "tags: ${listedTags()}")

    @Then("the sources page does not list the tag {string}")
    fun lacksTag(name: String) = assertFalse(listedTags().any { it.first == name }, "tags: ${listedTags()}")

    @Then("there is {int} tag")
    fun tagCount(count: Int) {
        val mine =
            app
                .jdbc()
                .sql("SELECT COUNT(*) FROM tags WHERE owner_id = ?")
                .param(app.currentUserId())
                .query(Long::class.java)
                .single()
        assertEquals(count.toLong(), mine)
    }

    @Then("there are {int} tags")
    fun tagCountPlural(count: Int) = tagCount(count)

    @Then("the new tag form holds the name {string} and the terms {string}")
    fun newTagFormHolds(
        name: String,
        terms: String,
    ) {
        val form = requireNotNull(NEW_TAG_FORM.find(World.sourcesPage)) { "no new tag form" }.value
        assertTrue(form.contains("id=\"new-tag\" type=\"text\" name=\"name\" maxlength=\"200\""), form)
        assertTrue(form.contains("value=\"${Html.escape(name)}\""), form)
        assertTrue(form.contains("value=\"${Html.escape(terms)}\""), form)
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun postTag(
        path: String,
        vararg fields: Pair<String, String>,
    ) {
        World.sourcesPage = app.postForm(path, *fields).body()
    }

    private fun fetch(path: String) {
        World.response = app.get(path)
        World.pages = listOf(World.response.body())
    }

    private fun tagId(name: String): Long =
        app
            .jdbc()
            .sql("SELECT id FROM tags WHERE name = ? ORDER BY owner_id = ? DESC, id LIMIT 1")
            .params(name, app.currentUserId())
            .query(Long::class.java)
            .single()

    private fun listedTags(): List<Pair<String, String>> =
        TAG_ITEM
            .findAll(app.get("/sources").body())
            .map { Html.unescape(it.groupValues[1]) to Html.unescape(it.groupValues[2]) }
            .toList()

    private companion object {
        val TAG_ITEM = Regex("<li class=\"tag\" data-tag=\"([^\"]*)\" data-terms=\"([^\"]*)\"")
        val NEW_TAG_FORM = Regex("<form class=\"new-tag[^\"]*\"[\\s\\S]*?</form>")
    }
}
