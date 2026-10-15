package dev.starve.application

import dev.starve.domain.ThrottleRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private class PasswordTestClock(
    var now: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

class AccountServiceTest {
    private val users = FakeUserAdmin("rob", "amy")
    private val clock = PasswordTestClock(Instant.parse("2026-10-14T10:00:00Z"))
    private val throttle = FailureThrottle(clock)
    private val service =
        AccountService(users, { "hashed:$it" }, { raw, hash -> hash == "hashed:$raw" }, throttle)
    private val client = "203.0.113.7"
    private val amyId = 2L

    private fun change(
        current: String = "pw-amy",
        new: String = "battery-staple-2",
        from: String = client,
        id: Long = amyId,
    ) = service.changePassword(id, current, new, from)

    @Test
    fun `the right current password stores the new hash`() {
        assertEquals(PasswordChange.Changed, change())

        assertEquals("hashed:battery-staple-2", users.findById(amyId)!!.passwordHash)
        assertEquals("hashed:pw-rob", users.findById(1)!!.passwordHash)
    }

    @Test
    fun `a wrong current password changes nothing`() {
        assertEquals(PasswordChange.WrongCurrent, change(current = "nope"))

        assertEquals("hashed:pw-amy", users.findById(amyId)!!.passwordHash)
    }

    @Test
    fun `a new password that breaks a rule is refused with its message and changes nothing`() {
        assertEquals(PasswordChange.Invalid("Password must be at least 10 characters"), change(new = "short"))
        assertEquals(PasswordChange.Invalid("Password is too long"), change(new = "a".repeat(73)))

        assertEquals("hashed:pw-amy", users.findById(amyId)!!.passwordHash)
    }

    @Test
    fun `five wrong current passwords block even the right one`() {
        repeat(ThrottleRules.MAX_FAILURES) { change(current = "nope") }

        assertEquals(PasswordChange.Blocked, change())
        assertEquals("hashed:pw-amy", users.findById(amyId)!!.passwordHash)
    }

    @Test
    fun `four wrong current passwords do not block`() {
        repeat(ThrottleRules.MAX_FAILURES - 1) { change(current = "nope") }

        assertEquals(PasswordChange.Changed, change())
    }

    @Test
    fun `the block ends after fifteen minutes`() {
        repeat(ThrottleRules.MAX_FAILURES) { change(current = "nope") }
        clock.now = clock.now.plus(Duration.ofMinutes(15))

        assertEquals(PasswordChange.Changed, change())
    }

    @Test
    fun `rule failures on the new password do not count`() {
        repeat(ThrottleRules.MAX_FAILURES) { change(new = "short") }

        assertEquals(PasswordChange.Changed, change())
    }

    @Test
    fun `a success resets the count`() {
        repeat(ThrottleRules.MAX_FAILURES - 1) { change(current = "nope") }
        change(current = "pw-amy", new = "pw-amy-new-1")
        repeat(ThrottleRules.MAX_FAILURES - 1) { change(current = "nope") }

        assertEquals(PasswordChange.Changed, change(current = "pw-amy-new-1"))
    }

    @Test
    fun `the block is per user and per client address`() {
        repeat(ThrottleRules.MAX_FAILURES) { change(current = "nope") }

        assertEquals(PasswordChange.Changed, change(from = "198.51.100.9"))
        assertEquals(PasswordChange.Changed, change(current = "pw-rob", id = 1))
    }

    @Test
    fun `the block has its own key, lower-cased, apart from the login key`() {
        users.rows.replaceAll { if (it.id == amyId) it.copy(username = "Amy") else it }
        repeat(ThrottleRules.MAX_FAILURES) { change(current = "nope") }

        assertEquals(true, throttle.isBlocked("password|amy|$client"))
        assertEquals(false, throttle.isBlocked("login|amy|$client"))
    }
}
