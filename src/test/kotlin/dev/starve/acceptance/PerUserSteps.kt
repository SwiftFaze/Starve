package dev.starve.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.dao.DataAccessException

/**
 * Steps for specs/features/per-user-data.feature: a second user, switching between users, requests aimed at another
 * user's rows, and the schema checks made straight against the database.
 */
class PerUserSteps {
    private val app = World.app

    // ---- users ---------------------------------------------------------------------------

    @Given("the user {string} exists")
    fun userExists(username: String) = app.seedUser(username)

    @When("I sign in as {string}")
    fun signInAs(username: String) = app.signIn(username)

    @When("the user {string} is deleted from the database")
    fun userDeleted(username: String) {
        app
            .jdbc()
            .sql("DELETE FROM users WHERE username = ?")
            .param(username)
            .update()
    }

    // ---- requests aimed at one row by its id ---------------------------------------------

    /** The `{id}` in [path] is replaced by the id of the [kind] named [name], whichever user owns it. */
    @When("^I (GET|POST) \"([^\"]*)\" with the id of the (item|source|group|tag|user) \"([^\"]*)\"$")
    fun requestWithId(
        method: String,
        path: String,
        kind: String,
        name: String,
    ) = send(method, path, idOf(kind, name))

    @When("^I (POST) \"([^\"]*)\" with the id of the source \"([^\"]*)\" and the group id of \"([^\"]*)\"$")
    fun postWithGroup(
        method: String,
        path: String,
        source: String,
        group: String,
    ) = send(method, path, app.sourceId(source), "group" to app.groupId(group).toString())

    @Then("the response contains {string}")
    fun responseContains(text: String) =
        assertTrue(World.response.body().contains(text), "response lacks '$text': ${World.response.body()}")

    // ---- schema --------------------------------------------------------------------------

    @Then("the database rejects a source without an owner")
    fun rejectsOwnerlessSource() = assertRejected("INSERT INTO sources (url, title) VALUES ('http://x.test/f', 'X')")

    @Then("the database rejects a group without an owner")
    fun rejectsOwnerlessGroup() = assertRejected("INSERT INTO groups (name) VALUES ('Orphans')")

    @Then("the database accepts the source URL {string} under both {string} and {string}")
    fun acceptsSourceUnderBoth(
        url: String,
        first: String,
        second: String,
    ) = listOf(first, second).forEach { insertSource(url, it) }

    @Then("the database rejects the source URL {string} a second time under {string}")
    fun rejectsSourceTwice(
        url: String,
        owner: String,
    ) = assertThrows(DataAccessException::class.java) { insertSource(url, owner) }

    @Then("the database accepts the group name {string} under both {string} and {string}")
    fun acceptsGroupUnderBoth(
        name: String,
        first: String,
        second: String,
    ) = listOf(first, second).forEach { insertGroup(name, it) }

    @Then("the database rejects the group name {string} a second time under {string}")
    fun rejectsGroupTwice(
        name: String,
        owner: String,
    ) = assertThrows(DataAccessException::class.java) { insertGroup(name, owner) }

    // ---- what a deleted user takes with them ---------------------------------------------

    @Then("no source titled {string} exists")
    fun noSourceTitled(title: String) = assertEquals(0L, count("SELECT COUNT(*) FROM sources WHERE title = ?", title))

    @Then("no group named {string} exists")
    fun noGroupNamed(name: String) = assertEquals(0L, count("SELECT COUNT(*) FROM groups WHERE name = ?", name))

    @Then("the source {string} and the group {string} still exist")
    fun sourceAndGroupExist(
        source: String,
        group: String,
    ) {
        assertEquals(1L, count("SELECT COUNT(*) FROM sources WHERE title = ?", source))
        assertEquals(1L, count("SELECT COUNT(*) FROM groups WHERE name = ?", group))
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun idOf(
        kind: String,
        name: String,
    ): Long =
        when (kind) {
            "item" -> app.itemId(name)
            "user" -> app.userId(name)
            "source" -> app.sourceId(name)
            "tag" ->
                app
                    .jdbc()
                    .sql("SELECT id FROM tags WHERE name = ?")
                    .param(name)
                    .query(Long::class.java)
                    .single()
            else -> app.groupId(name)
        }

    private fun send(
        method: String,
        path: String,
        id: Long,
        vararg fields: Pair<String, String>,
    ) {
        val target = path.replace("{id}", id.toString())
        World.response =
            if (method == "GET") app.get(target) else app.postForm(target, *fields)
        World.pages = listOf(World.response.body())
    }

    private fun assertRejected(sql: String) {
        assertThrows(DataAccessException::class.java) { app.jdbc().sql(sql).update() }
    }

    private fun insertSource(
        url: String,
        owner: String,
    ) {
        app
            .jdbc()
            .sql("INSERT INTO sources (owner_id, url, title) VALUES (?, ?, 'Direct')")
            .params(app.userId(owner), url)
            .update()
    }

    private fun insertGroup(
        name: String,
        owner: String,
    ) {
        app
            .jdbc()
            .sql("INSERT INTO groups (owner_id, name) VALUES (?, ?)")
            .params(app.userId(owner), name)
            .update()
    }

    private fun count(
        sql: String,
        vararg params: Any,
    ): Long =
        app
            .jdbc()
            .sql(sql)
            .params(params.toList())
            .query(Long::class.java)
            .single()
}
