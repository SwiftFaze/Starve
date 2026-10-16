package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Steps for mobile-header-layout.feature: the top bar's menu button, the obi's group picker and the search form
 * at the head of the timeline's list. They read the rendered markup; what folds away on a phone is styling.
 */
class MobileHeaderSteps {
    private val app = World.app

    @Then("the top bar has a menu button controlling its account actions")
    fun barHasMenu() {
        val menu = requireNotNull(Regex("<button[^>]*class=\"bar-menu\"[^>]*>").find(bar())) { "no menu button: ${bar()}" }.value
        assertTrue(menu.contains("aria-controls=\"bar-actions\""), menu)
        assertTrue(menu.contains("aria-expanded=\"false\""), menu)
        assertTrue(bar().contains("id=\"bar-actions\""), "the menu controls no account actions: ${bar()}")
    }

    @Then("the account actions are a link to {string} and a plain sign-out form")
    fun actionsArePlain(href: String) {
        val actions = bar().substringAfter("id=\"bar-actions\"")
        assertTrue(actions.contains("href=\"$href\""), actions)
        val signOut = requireNotNull(Regex("<form[^>]*class=\"signout\"[^>]*>").find(actions)) { actions }.value
        assertTrue(signOut.contains("method=\"post\""), signOut)
        assertFalse(signOut.contains("hx-post"), signOut)
    }

    @Then("the obi has a group picker controlling the group list")
    fun obiHasPicker() {
        val pick = requireNotNull(Regex("<button[^>]*class=\"series-pick\"[^>]*>").find(series())) { series() }.value
        assertTrue(pick.contains("aria-controls=\"groups\""), pick)
        assertTrue(body().contains("<nav class=\"groups\" id=\"groups\""), "no group list for the picker to open")
    }

    @Then("the group list links to the group {string}")
    fun groupListLinks(group: String) {
        val list = body().substringAfter("id=\"groups\"").substringBefore("</nav>")
        assertTrue(list.contains("group=${app.groupId(group)}"), list)
    }

    @Then("the obi has no group picker")
    fun obiHasNoPicker() = assertFalse(body().contains("class=\"series-pick\""), series())

    @Then("the timeline's sheet starts with the search form")
    fun sheetStartsWithSearch() {
        val sheet = body().substringAfter("id=\"sheet\"")
        val form = sheet.indexOf("<form class=\"search\"")
        val sheetBody = sheet.indexOf("id=\"sheet-body\"")
        assertTrue(form >= 0, "no search form in the sheet")
        assertTrue(sheetBody > form, "the search form is not ahead of the sheet body")
    }

    @Then("the search box swaps only the sheet body with its results")
    fun searchSwapsSheetBody() {
        val box = requireNotNull(Regex("<input type=\"search\"[^>]*>").find(body())) { "no search box" }.value
        assertTrue(box.contains("hx-target=\"#sheet-body\""), box)
        assertTrue(box.contains("hx-select=\"#sheet-body\""), box)
    }

    @Then("the top bar has no search box")
    fun barHasNoSearch() = assertFalse(bar().contains("type=\"search\""), bar())

    private fun body(): String = World.response.body()

    private fun bar(): String = body().substringAfter("<header").substringBefore("</header>")

    private fun series(): String = body().substringAfter("id=\"series\"").substringBefore("</div>")
}
