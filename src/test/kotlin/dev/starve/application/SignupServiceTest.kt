package dev.starve.application

import dev.starve.domain.AccountRules
import dev.starve.domain.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private class MovableClock(
    var now: Instant = Instant.parse("2026-10-14T10:00:00Z"),
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

private class MemoryMetadata : AppMetadata {
    val values = mutableMapOf<String, String>()

    override fun find(key: String): String? = values[key]

    override fun putIfAbsent(
        key: String,
        value: String,
    ): Boolean = values.putIfAbsent(key, value) == null

    override fun put(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}

private class SignupUsers : UserRepository {
    val rows = mutableListOf<User>()

    override fun exists(): Boolean = rows.isNotEmpty()

    override fun findByUsername(username: String): User? = rows.firstOrNull { it.username.equals(username, true) }

    override fun insertFirstAdmin(
        username: String,
        passwordHash: String,
        at: Instant,
    ): Boolean = error("unused")

    override fun insert(
        username: String,
        passwordHash: String,
        at: Instant,
    ): User = User(rows.size + 1L, username, passwordHash, admin = false).also { rows += it }

    override fun insertIfFree(
        username: String,
        passwordHash: String,
        at: Instant,
    ): User? = if (findByUsername(username) != null) null else insert(username, passwordHash, at)
}

class SignupSettingsTest {
    private val metadata = MemoryMetadata()
    private val settings = SignupSettings(metadata)

    @Test
    fun `signups are off while nothing is stored`() {
        assertFalse(settings.enabled())
    }

    @Test
    fun `turning signups on and off is read back at once`() {
        settings.set(true)
        assertTrue(settings.enabled())

        settings.set(false)
        assertFalse(settings.enabled())
    }

    @Test
    fun `any stored value other than on counts as off`() {
        metadata.values["signups"] = "yes"

        assertFalse(settings.enabled())
    }
}

class SignupServiceTest {
    private val users = SignupUsers()
    private val clock = MovableClock()
    private val throttle = FailureThrottle(clock)
    private val settings = SignupSettings(MemoryMetadata()).apply { set(true) }
    private val service = SignupService(users, { "hashed:$it" }, throttle, clock, settings)
    private val client = "203.0.113.7"

    private fun signUp(
        username: String = "friend",
        password: String = "correct-horse-2",
        from: String = client,
    ) = service.signUp(username, password, from)

    private fun fail(times: Int) = repeat(times) { signUp(username = "ab") }

    @Test
    fun `a valid signup stores a hashed non-admin account`() {
        assertEquals(SignupOutcome.Created("friend"), signUp())

        assertEquals(User(1, "friend", "hashed:correct-horse-2", admin = false), users.findByUsername("friend"))
    }

    @Test
    fun `a rule-breaking username is invalid and stores nothing`() {
        assertEquals(SignupOutcome.Invalid(AccountRules.USERNAME_ERROR), signUp(username = "ab"))
        assertFalse(users.exists())
    }

    @Test
    fun `a rule-breaking password is invalid and stores nothing`() {
        assertEquals(SignupOutcome.Invalid(AccountRules.PASSWORD_SHORT), signUp(password = "short"))
        assertFalse(users.exists())
    }

    @Test
    fun `the username error wins over the password error`() {
        assertEquals(SignupOutcome.Invalid(AccountRules.USERNAME_ERROR), signUp(username = "ab", password = "x"))
    }

    @Test
    fun `a taken username is refused whatever its case`() {
        signUp(username = "friend")

        assertEquals(SignupOutcome.Taken, signUp(username = "FRIEND"))
        assertEquals(1, users.rows.size)
    }

    @Test
    fun `while signups are off nothing is created and nothing is counted`() {
        settings.set(false)

        repeat(6) { assertEquals(SignupOutcome.Closed, signUp(username = "ab")) }
        settings.set(true)

        assertEquals(SignupOutcome.Created("friend"), signUp())
    }

    @Test
    fun `the fifth failure blocks the client and the block refuses even a valid signup`() {
        fail(4)
        assertEquals(SignupOutcome.Created("friend"), signUp())
        fail(1)

        assertEquals(SignupOutcome.Blocked, signUp(username = "pal"))
        assertEquals(1, users.rows.size)
    }

    @Test
    fun `taken usernames count as failures`() {
        signUp(username = "friend")
        repeat(5) { signUp(username = "friend") }

        assertEquals(SignupOutcome.Blocked, signUp(username = "pal"))
    }

    @Test
    fun `successes are not counted as failures`() {
        repeat(6) { assertEquals(SignupOutcome.Created("friend$it"), signUp(username = "friend$it")) }
    }

    @Test
    fun `the block lifts after 15 minutes`() {
        fail(5)
        clock.now = clock.now.plus(Duration.ofMinutes(15))

        assertEquals(SignupOutcome.Created("friend"), signUp())
    }

    @Test
    fun `another client is not blocked`() {
        fail(5)

        assertEquals(SignupOutcome.Created("friend"), signUp(from = "198.51.100.2"))
    }

    @Test
    fun `signup failures do not share counters with setup or login`() {
        repeat(5) { throttle.recordFailure("setup|$client") }
        repeat(5) { throttle.recordFailure("login|friend|$client") }

        assertEquals(SignupOutcome.Created("friend"), signUp())
    }

    @Test
    fun `failures count under the signup key and nothing else`() {
        fail(5)

        assertTrue(throttle.isBlocked("signup|$client"))
        assertFalse(throttle.isBlocked("setup|$client"))
    }
}
