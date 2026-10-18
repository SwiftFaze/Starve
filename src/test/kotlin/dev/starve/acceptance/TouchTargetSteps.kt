package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** Phone touch targets, back to the top and the list's end (phone-touch-targets.feature). */
class TouchTargetSteps {
    @Then("the end of the list has a Mark all read form posting {string}")
    fun listEndForm(action: String) {
        val form = requireNotNull(LIST_END.find(body())) { "no list-end form" }.value
        assertTrue(form.contains("hx-post=\"$action\""), form)
    }

    @Then("the end of the list has no Mark all read form")
    fun noListEndForm() = assertFalse(LIST_END.containsMatchIn(body()), body())

    @Then("on phones the rule {string} sets {string}")
    fun phoneRule(
        selector: String,
        declaration: String,
    ) {
        val phones = body().replace(CR, "").substringAfter("Phone touch targets (#98)")
        val start = phones.indexOf("  ${selector.replace(ESCAPED_NEWLINE, "\n")} {")
        assertTrue(start >= 0, "no phone rule $selector")
        val rule = phones.substring(start, phones.indexOf('}', start))
        assertTrue(rule.contains(declaration), "$selector lacks $declaration: $rule")
    }

    @Then("back to the top hides while a row's actions or a post's head are under it")
    fun topStepsAside() {
        assertTrue(body().contains("el.closest('.item .actions, .reader-head')"))
        assertTrue(body().contains("button.hidden = overControls(button);"))
    }

    private fun body(): String = World.response.body()

    private companion object {
        const val CR = "\r"
        const val ESCAPED_NEWLINE = "\\n"
        val LIST_END = Regex("<form [^>]*class=\"read-all list-end\"[^>]*>")
    }
}
