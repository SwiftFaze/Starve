package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.math.pow

/** Mechanical checks on the served stylesheet: colour contrast per theme and the type floor (light-obi-contrast). */
class StylesheetSteps {
    @Then("in every theme {string} on {string} has a contrast of at least {double}")
    fun contrastInEveryTheme(
        text: String,
        ground: String,
        minimum: Double,
    ) {
        val themes = THEMES.mapValues { (_, selector) -> block(selector) }
        assertEquals(THEMES.keys, themes.keys)
        themes.forEach { (theme, tokens) ->
            val ratio = contrast(token(tokens, text), token(tokens, ground))
            assertTrue(ratio >= minimum, "$theme: $text on $ground is ${"%.2f".format(ratio)}:1")
        }
    }

    @Then("no font size in the stylesheet is below {double}rem")
    fun typeFloor(floor: Double) {
        val tooSmall =
            FONT_DECLARATION
                .findAll(sheet())
                .map { it.value }
                .filter { declaration -> REM.findAll(declaration).any { it.groupValues[1].toDouble() < floor } }
                .toList()
        assertEquals(emptyList<String>(), tooSmall)
    }

    @Then("the keyframes {string} change no opacity")
    fun keyframesWithoutOpacity(name: String) {
        val rule = Regex("@keyframes $name \\{(.*?\\})\\s*\\}", RegexOption.DOT_MATCHES_ALL)
        val keyframes = requireNotNull(rule.find(sheet())) { "no keyframes $name" }
        assertFalse(keyframes.value.contains("opacity"), keyframes.value)
    }

    private fun sheet(): String = World.response.body()

    /** The custom properties set directly in the rule whose selector is exactly [selector]. */
    private fun block(selector: String): Map<String, String> {
        val start = Regex("(?m)^\\s*${Regex.escape(selector)} \\{").find(sheet()) ?: error("no rule $selector")
        val body = sheet().substring(start.range.last + 1, sheet().indexOf('}', start.range.last))
        return PROPERTY.findAll(body).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun token(
        tokens: Map<String, String>,
        name: String,
    ): String = tokens[name] ?: block(":root")[name] ?: error("no $name")

    private fun contrast(
        a: String,
        b: String,
    ): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + FLARE) / (dark + FLARE)
    }

    /** WCAG 2 relative luminance of a #rrggbb colour. */
    private fun luminance(hex: String): Double {
        val channels = (1..5 step 2).map { hex.substring(it, it + 2).toInt(HEX) / MAX_CHANNEL }
        val linear = channels.map { if (it <= SRGB_KNEE) it / SRGB_SLOPE else linearise(it) }
        return RED * linear[0] + GREEN * linear[1] + BLUE * linear[2]
    }

    private fun linearise(channel: Double): Double = ((channel + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_GAMMA)

    private companion object {
        val THEMES =
            mapOf(
                "dark" to ":root",
                "light (OS)" to ":root:not([data-theme=\"dark\"])",
                "light (switch)" to ":root[data-theme=\"light\"]",
            )
        val PROPERTY = Regex("(--[a-z0-9-]+):\\s*(#[0-9a-fA-F]{6});")
        val FONT_DECLARATION = Regex("font(-size)?:[^;]*;")
        val REM = Regex("(\\d*\\.?\\d+)rem")
        const val HEX = 16
        const val MAX_CHANNEL = 255.0
        const val SRGB_KNEE = 0.04045
        const val SRGB_SLOPE = 12.92
        const val SRGB_OFFSET = 0.055
        const val SRGB_SCALE = 1.055
        const val SRGB_GAMMA = 2.4
        const val RED = 0.2126
        const val GREEN = 0.7152
        const val BLUE = 0.0722
        const val FLARE = 0.05
    }
}
