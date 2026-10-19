package dev.starve.acceptance

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for relative-redirects.feature: redirects that name only a path, so they keep the page's scheme. */
class RedirectSteps {
    private val app = World.app

    @When("I submit the create-group form for {string}")
    fun submitCreateGroup(name: String) {
        World.response = app.postForm("/groups", "name" to name)
    }

    @Then("the redirect's Location is exactly {string}")
    fun locationIs(path: String) {
        assertTrue(World.response.statusCode() in 300..399, "expected a redirect, got ${World.response.statusCode()}")
        val location =
            World.response
                .headers()
                .firstValue("Location")
                .orElseThrow()
        assertEquals(path, location)
    }
}
