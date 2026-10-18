package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/** The new-password minimum length, kept values and field-tied errors of the access forms (password-field-hints). */
class AccessFieldSteps {
    @Then("the field {string} has the minimum length {int}")
    fun hasMinLength(
        id: String,
        length: Int,
    ) = assertEquals("$length", attribute(field(id), "minlength"), field(id))

    @Then("the field {string} has no minimum length")
    fun hasNoMinLength(id: String) = assertNull(attribute(field(id), "minlength"), field(id))

    @Then("the field {string} keeps the value {string}")
    fun keepsValue(
        id: String,
        value: String,
    ) = assertEquals(Html.escape(value), attribute(field(id), "value"), field(id))

    @Then("the field {string} has no value")
    fun hasNoValue(id: String) = assertTrue(attribute(field(id), "value").isNullOrEmpty(), field(id))

    @Then("the field {string} is marked invalid and described by the error")
    fun markedInvalid(id: String) {
        val tag = field(id)
        assertEquals("true", attribute(tag, "aria-invalid"), tag)
        val describedBy = requireNotNull(attribute(tag, "aria-describedby")) { "no aria-describedby on $tag" }
        assertTrue(describedBy.split(" ").contains("message"), tag)
        assertTrue(World.response.body().contains("<p id=\"message\""), "no error message")
    }

    @Then("the field {string} is not marked invalid")
    fun notMarkedInvalid(id: String) {
        val tag = field(id)
        assertNull(attribute(tag, "aria-invalid"), tag)
        assertFalse(attribute(tag, "aria-describedby").orEmpty().split(" ").contains("message"), tag)
    }

    @Then("no field is marked invalid")
    fun noFieldInvalid() = assertFalse(World.response.body().contains("aria-invalid"), World.response.body())

    private fun field(id: String): String {
        val pattern = Regex("<input [^>]*id=\"${Regex.escape(id)}\"[^>]*>")
        return requireNotNull(pattern.find(World.response.body())) { "no field $id in ${World.response.body()}" }.value
    }

    private fun attribute(
        tag: String,
        name: String,
    ): String? = Regex("\\s${Regex.escape(name)}=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)
}
