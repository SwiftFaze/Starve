package dev.starve.acceptance

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertTrue

/** The shutter's two paces as theme.js declares and uses them (shutter-cost.feature). */
class ShutterPaceSteps {
    @Then("the shutter pace {string} sweeps for {int} ms and holds for {int} ms")
    fun pace(
        name: String,
        sweep: Int,
        hold: Int,
    ) = assertTrue(script().contains("var $name = { sweep: $sweep, hold: $hold };"), "no $name pace of $sweep/$hold")

    @Then("the theme switch sweeps in and out at the {string} pace")
    fun themePace(name: String) = assertSweeps(handler("closest('.theme-switch')"), name)

    @Then("a page switch sweeps in and out at the {string} pace")
    fun pagePace(name: String) {
        val sweepIn = handler("'htmx:confirm'")
        val sweepOut = handler("'htmx:afterSettle'")
        assertTrue(sweepIn.contains("sweepIn(panel, $name)"), "page switch does not sweep in at $name: $sweepIn")
        assertTrue(sweepOut.contains("sweepOut(panel, $name)"), "page switch does not sweep out at $name: $sweepOut")
    }

    private fun assertSweeps(
        code: String,
        name: String,
    ) {
        assertTrue(code.contains("sweepIn(panel, $name)"), "does not sweep in at $name: $code")
        assertTrue(code.contains("sweepOut(panel, $name)"), "does not sweep out at $name: $code")
    }

    /** The event handler that starts at [marker], up to the next top-level listener. */
    private fun handler(marker: String): String {
        val start = script().indexOf(marker)
        assertTrue(start >= 0, "no handler for $marker")
        val end = script().indexOf("document.addEventListener(", start + marker.length)
        return script().substring(start, if (end < 0) script().length else end)
    }

    private fun script(): String = World.response.body()
}
