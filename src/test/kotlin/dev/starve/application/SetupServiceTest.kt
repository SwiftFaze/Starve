package dev.starve.application

import dev.starve.domain.AccountRules
import dev.starve.domain.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private class FakeUsers(
    val rows: MutableList<User> = mutableListOf(),
) : UserRepository {
    override fun exists(): Boolean = rows.isNotEmpty()

    override fun findByUsername(username: String): User? = rows.firstOrNull { it.username.equals(username, true) }

    override fun insertFirstAdmin(
        username: String,
        passwordHash: String,
        at: Instant,
    ): Boolean {
        if (rows.isNotEmpty()) return false
        rows += User(rows.size + 1L, username, passwordHash, admin = true)
        return true
    }

    override fun insert(
        username: String,
        passwordHash: String,
        at: Instant,
    ): User = User(rows.size + 1L, username, passwordHash, admin = false).also { rows += it }
}

class SetupServiceTest {
    private val users = FakeUsers()
    private val clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC)
    private val throttle = FailureThrottle(clock)
    private val service =
        SetupService(users, { "hashed:$it" }, throttle, clock, SecureRandom())
    private val client = "203.0.113.7"

    private fun code(): String = requireNotNull(service.startRun())

    @Test
    fun `setup is pending and a code is issued for each run while no user exists`() {
        assertTrue(service.pending())
        assertNotNull(service.startRun())
    }

    @Test
    fun `no code is issued once a user exists`() {
        users.rows += User(1, "rob", "hash", admin = true)
        assertFalse(service.pending())
        assertNull(service.startRun())
    }

    @Test
    fun `the right code creates the first user as an admin with a hashed password`() {
        val outcome = service.complete(code(), "rob", "correct-horse-1", client)

        assertEquals(SetupOutcome.Created("rob"), outcome)
        assertEquals("rob", (outcome as SetupOutcome.Created).username)
        val stored = users.rows.single()
        assertEquals(User(1, "rob", "hashed:correct-horse-1", admin = true), stored)
        assertEquals(1L, stored.id)
        assertEquals("rob", stored.username)
        assertEquals("hashed:correct-horse-1", stored.passwordHash)
        assertTrue(stored.admin)
    }

    @Test
    fun `a wrong code creates nobody`() {
        assertEquals(SetupOutcome.WrongCode, service.complete("AAAA-AAAA", "rob", "correct-horse-1", client))
        assertTrue(users.rows.isEmpty())
    }

    @Test
    fun `the code is matched case-insensitively with the hyphen optional`() {
        val issued = code()
        val outcome = service.complete(issued.lowercase().replace("-", ""), "rob", "correct-horse-1", client)
        assertEquals(SetupOutcome.Created("rob"), outcome)
    }

    @Test
    fun `five wrong codes from one client throttle the next attempt, even with the right code`() {
        val issued = code()
        repeat(5) { service.complete("AAAA-AAAA", "rob", "correct-horse-1", client) }

        assertEquals(SetupOutcome.Blocked, service.complete(issued, "rob", "correct-horse-1", client))
        assertTrue(users.rows.isEmpty())
    }

    @Test
    fun `validation errors do not count as failed codes`() {
        val issued = code()
        repeat(6) {
            assertEquals(
                SetupOutcome.Invalid(AccountRules.USERNAME_ERROR),
                service.complete(issued, "ro", "correct-horse-1", client),
            )
        }
        assertEquals(SetupOutcome.Created("rob"), service.complete(issued, "rob", "correct-horse-1", client))
    }

    @Test
    fun `a short password is reported and nobody is created`() {
        val outcome = service.complete(code(), "rob", "short-pw1", client)
        assertEquals(SetupOutcome.Invalid(AccountRules.PASSWORD_SHORT), outcome)
        assertEquals(AccountRules.PASSWORD_SHORT, (outcome as SetupOutcome.Invalid).error)
        assertTrue(users.rows.isEmpty())
    }

    @Test
    fun `the code is discarded once setup completes`() {
        val issued = code()
        service.complete(issued, "rob", "correct-horse-1", client)

        assertFalse(service.pending())
        assertEquals(SetupOutcome.AlreadySetUp, service.complete(issued, "eve", "correct-horse-1", client))
        assertNull(service.startRun())
    }

    @Test
    fun `losing the race to create the first user reports setup as done`() {
        val racing =
            object : UserRepository {
                override fun exists(): Boolean = false

                override fun findByUsername(username: String): User? = null

                override fun insertFirstAdmin(
                    username: String,
                    passwordHash: String,
                    at: Instant,
                ): Boolean = false

                override fun insert(
                    username: String,
                    passwordHash: String,
                    at: Instant,
                ): User = error("unused")
            }
        val raced = SetupService(racing, { it }, throttle, clock, SecureRandom())
        val issued = requireNotNull(raced.startRun())

        assertEquals(SetupOutcome.AlreadySetUp, raced.complete(issued, "rob", "correct-horse-1", client))
    }

    @Test
    fun `a throttled client does not block another client`() {
        val issued = code()
        repeat(5) { service.complete("AAAA-AAAA", "rob", "correct-horse-1", client) }

        assertEquals(SetupOutcome.Created("rob"), service.complete(issued, "rob", "correct-horse-1", "198.51.100.2"))
    }

    @Test
    fun `completing setup clears the client's earlier failed codes`() {
        val issued = code()
        repeat(3) { service.complete("AAAA-AAAA", "rob", "correct-horse-1", client) }
        service.complete(issued, "rob", "correct-horse-1", client)
        users.rows.clear()
        val reissued = code()

        repeat(2) { service.complete("AAAA-AAAA", "rob", "correct-horse-1", client) }

        assertEquals(SetupOutcome.Created("rob"), service.complete(reissued, "rob", "correct-horse-1", client))
    }
}
