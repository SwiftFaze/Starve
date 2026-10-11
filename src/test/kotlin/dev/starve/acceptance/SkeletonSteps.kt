package dev.starve.acceptance

import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.net.http.HttpResponse
import java.nio.file.Files

class SkeletonSteps {
    private val app = ApplicationHarness()
    private var response: HttpResponse<String>? = null
    private var historyBeforeRestart: List<String> = emptyList()

    @Before
    fun resetDatabase() = app.freshDatabase()

    @After
    fun shutDown() = app.cleanUp()

    @Given("a fresh SQLite database file")
    fun freshDatabaseFile() = app.freshDatabase()

    @Given("the application is running")
    fun applicationIsRunning() = app.start()

    @When("the application starts")
    fun applicationStarts() = app.start()

    @When("I request the home page {string}")
    fun requestHomePage(path: String) = request(path)

    @When("I request {string}")
    fun request(path: String) {
        response = app.get(path)
    }

    @Then("the response status is {int}")
    fun responseStatusIs(status: Int) = assertEquals(status, response!!.statusCode())

    @Then("the page contains {string}")
    fun pageContains(text: String) = assertTrue(response!!.body().contains(text), "page lacks '$text'")

    @Then("the health status is {string}")
    fun healthStatusIs(status: String) {
        val body = response!!.body()
        assertTrue(body.contains("\"status\":\"$status\""), body)
    }

    @Then("the database file exists")
    fun databaseFileExists() = assertTrue(Files.exists(app.dbPath))

    @Then("all schema migrations are recorded as applied")
    fun allMigrationsApplied() {
        val applied = appliedMigrations()
        assertEquals(migrationScripts(), applied.size)
        assertTrue(applied.all { it.endsWith("|1") }, "failed migration in $applied")
    }

    @Given("a marker value is stored in the database")
    fun markerValueStored() {
        app.jdbc().sql("CREATE TABLE test_marker (value TEXT NOT NULL)").update()
        app.jdbc().sql("INSERT INTO test_marker (value) VALUES ('survives')").update()
        historyBeforeRestart = appliedMigrations()
    }

    @When("the application is restarted with the same database file")
    fun restartApplication() = app.restart()

    @Then("the marker value is still present in the database")
    fun markerStillPresent() {
        val value =
            app
                .jdbc()
                .sql("SELECT value FROM test_marker")
                .query(String::class.java)
                .single()
        assertEquals("survives", value)
    }

    @Then("no schema migration is applied a second time")
    fun noMigrationReapplied() = assertEquals(historyBeforeRestart, appliedMigrations())

    private fun appliedMigrations(): List<String> =
        app
            .jdbc()
            .sql("SELECT version || '|' || success FROM flyway_schema_history ORDER BY installed_rank")
            .query(String::class.java)
            .list()

    private fun migrationScripts(): Int =
        PathMatchingResourcePatternResolver()
            .getResources("classpath:db/migration/V*.sql")
            .size
}
