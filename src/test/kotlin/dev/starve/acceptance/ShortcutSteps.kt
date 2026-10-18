package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** The timeline's keyboard shortcuts as deck.js binds and lists them (timeline-shortcuts.feature). */
class ShortcutSteps {
    @Then("the script binds the keys {string}")
    fun bindsKeys(keys: String) = assertEquals(keys.split(", ").map(::keyName), bound())

    @Then("the key list names every bound key")
    fun listNamesKeys() {
        val listed = KEY_ROW.findAll(shortcutCode()).joinToString(" ") { it.groupValues[1] }
        val words = listed.split(" ")
        bound().forEach { key -> assertTrue(words.contains(key), "'$key' not listed: $listed") }
    }

    @Then("the shortcuts only run on the timeline, not while typing or with a modifier held")
    fun guarded() {
        val code = shortcutCode()
        assertTrue(code.contains(NO_MODIFIERS))
        assertTrue(code.contains(TIMELINE_ONLY))
        assertTrue(code.contains("if (!shortcut || typing(event.target)) return;"))
    }

    @Then("the sheet is marked as the timeline page")
    fun timelineSheet() {
        assertTrue(World.response.body().contains("id=\"sheet\" tabindex=\"-1\" data-page=\"timeline\""))
    }

    /** The keys of the SHORTCUTS map, in order; the space key reads as "Space". */
    private fun bound(): List<String> {
        val map = shortcutCode().substringAfter("var SHORTCUTS = {").substringBefore("\n  };")
        return BINDING.findAll(map).map { keyName(it.groupValues[1].trim('\'')) }.toList()
    }

    private fun keyName(key: String): String = if (key.isBlank()) "Space" else key

    private fun shortcutCode(): String = World.response.body().substringAfter("Keyboard shortcuts on the timeline")

    private companion object {
        const val NO_MODIFIERS = "if (event.ctrlKey || event.altKey || event.metaKey || event.defaultPrevented) return;"
        const val TIMELINE_ONLY = "if (!document.querySelector('#sheet[data-page=\"timeline\"]')) return;"
        val BINDING = Regex("(?m)^ {4}('[^']+'|[a-z]): ")
        val KEY_ROW = Regex("\\['([^']+)', '")
    }
}
