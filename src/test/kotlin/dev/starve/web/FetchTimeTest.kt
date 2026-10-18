package dev.starve.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class FetchTimeTest {
    private val now = Instant.parse("2026-10-17T17:20:00Z")

    private fun short(at: String) = FetchTime.of(Instant.parse(at), now).short

    @Test
    fun `a fetch today shows only its time`() {
        assertEquals("17:13 UTC", short("2026-10-17T17:13:42Z"))
        assertEquals("00:00 UTC", short("2026-10-17T00:00:00Z"))
    }

    @Test
    fun `a fetch the day before says yesterday`() {
        assertEquals("yesterday 09:02 UTC", short("2026-10-16T09:02:59Z"))
        assertEquals("yesterday 23:59 UTC", short("2026-10-16T23:59:59Z"))
    }

    @Test
    fun `an older fetch shows its date and minutes`() {
        assertEquals("2026-10-15 23:59 UTC", short("2026-10-15T23:59:59Z"))
    }

    @Test
    fun `a fetch after now, from a skewed clock, shows its date`() {
        assertEquals("2026-10-18 01:00 UTC", short("2026-10-18T01:00:00Z"))
    }

    @Test
    fun `the full form keeps the seconds and the iso form is the exact instant`() {
        val time = FetchTime.of(Instant.parse("2026-10-17T17:13:42Z"), now)

        assertEquals("2026-10-17 17:13:42 UTC", time.full)
        assertEquals("2026-10-17T17:13:42Z", time.iso)
    }
}
