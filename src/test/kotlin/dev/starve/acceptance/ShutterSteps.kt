package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Steps for shutter-transition-flash.feature: the links that move between pages swap behind the shutter, the
 * forms do not.
 */
class ShutterSteps {
    @Then("the {string} link to {string} requests its page via htmx and pushes its URL")
    fun linkSwapsPage(
        label: String,
        href: String,
    ) = anchors(label).forEach { link ->
        assertTrue(link.contains("href=\"$href\""), link)
        assertTrue(link.contains("hx-get=\"$href\""), link)
        assertTrue(link.contains("hx-push-url=\"true\""), link)
        assertTrue(link.contains("hx-swap=\"outerHTML\""), link)
        assertTrue(link.contains(" data-switch"), "no data-switch marker, so no shutter: $link")
        val target = attribute(link, "hx-target")
        assertTrue(target.startsWith("#"), link)
        assertTrue(attribute(link, "hx-select") == target, link)
        assertTrue(body().contains("id=\"${target.removePrefix("#")}\""), "the page has no $target to swap: $link")
    }

    @Then("the {string} link to {string} keeps its plain href")
    fun linkKeepsHref(
        label: String,
        href: String,
    ) = anchors(label).forEach { assertTrue(it.contains("href=\"$href\""), it) }

    @Then("the {string} form does not submit via htmx")
    fun formIsPlain(label: String) {
        val form =
            FORM
                .findAll(body())
                .map { it.value }
                .firstOrNull { it.contains(">$label</button>") || it.contains("aria-label=\"$label\"") }
        requireNotNull(form) { "no form with a '$label' button" }
        assertFalse(form.contains("hx-"), form)
    }

    private fun anchors(label: String): List<String> =
        Regex("<a [^>]*>\\s*${Regex.escape(label)}\\s*</a>")
            .findAll(body())
            .map { it.value }
            .toList()
            .also { assertTrue(it.isNotEmpty(), "no '$label' link") }

    private fun attribute(
        tag: String,
        name: String,
    ): String = requireNotNull(Regex("""\s$name="([^"]*)"""").find(tag)) { "no $name in $tag" }.groupValues[1]

    private fun body(): String = World.response.body()

    private companion object {
        val FORM = Regex("<form[^>]*>.*?</form>", RegexOption.DOT_MATCHES_ALL)
    }
}
