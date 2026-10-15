package dev.starve.infra

import dev.starve.domain.User
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path
import java.time.Instant
import javax.sql.DataSource

class JdbcAccountRepositoriesTest {
    @TempDir
    lateinit var tmp: Path

    private lateinit var dataSource: DataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var users: JdbcUserRepository
    private lateinit var metadata: JdbcAppMetadata
    private val now = Instant.parse("2026-10-14T10:00:00Z")

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConfig().dataSource(tmp.resolve("t.db").toString())
        Flyway
            .configure()
            .dataSource(dataSource)
            .load()
            .migrate()
        jdbc = JdbcClient.create(dataSource)
        users = JdbcUserRepository(jdbc)
        metadata = JdbcAppMetadata(jdbc)
    }

    @AfterEach
    fun tearDown() = (dataSource as AutoCloseable).close()

    @Test
    fun `no user exists until the first admin is inserted`() {
        assertFalse(users.exists())

        users.insertFirstAdmin("rob", "hash", now)

        assertTrue(users.exists())
    }

    @Test
    fun `the first admin is stored with its hash and found regardless of username case`() {
        assertTrue(users.insertFirstAdmin("Rob", "hash", now))

        assertEquals(User(1, "Rob", "hash", admin = true), users.findByUsername("rOB"))
    }

    @Test
    fun `an account without the admin flag is read back as not an admin`() {
        jdbc
            .sql("INSERT INTO users (username, password_hash, admin, created_at) VALUES ('amy', 'h', 0, 0)")
            .update()

        assertFalse(users.findByUsername("amy")!!.admin)
    }

    @Test
    fun `an inserted account is a non-admin that can be found again`() {
        users.insertFirstAdmin("rob", "hash", now)

        val inserted = users.insert("Amy", "h2", now)

        assertEquals(User(2, "Amy", "h2", admin = false), inserted)
        assertEquals(inserted, users.findByUsername("amy"))
    }

    @Test
    fun `insertIfFree stores a free name as a non-admin that can be found again`() {
        users.insertFirstAdmin("rob", "hash", now)

        val inserted = users.insertIfFree("Amy", "h2", now)

        assertEquals(User(2, "Amy", "h2", admin = false), inserted)
        assertEquals(inserted, users.findByUsername("amy"))
    }

    @Test
    fun `insertIfFree refuses a name taken in any case and changes nothing`() {
        users.insertFirstAdmin("rob", "hash", now)

        assertNull(users.insertIfFree("ROB", "other", now))
        assertEquals("hash", users.findByUsername("rob")!!.passwordHash)
    }

    @Test
    fun `an unknown username finds nobody`() {
        users.insertFirstAdmin("rob", "hash", now)

        assertNull(users.findByUsername("eve"))
    }

    @Test
    fun `a second first-admin insert is refused and changes nothing`() {
        users.insertFirstAdmin("rob", "hash", now)

        assertFalse(users.insertFirstAdmin("eve", "other", now))
        assertNull(users.findByUsername("eve"))
    }

    @Test
    fun `metadata keeps the first value stored for a key`() {
        assertNull(metadata.find("k"))

        assertTrue(metadata.putIfAbsent("k", "one"))
        assertFalse(metadata.putIfAbsent("k", "two"))

        assertEquals("one", metadata.find("k"))
    }

    @Test
    fun `put stores a new value and replaces an old one`() {
        metadata.put("k", "one")
        assertEquals("one", metadata.find("k"))

        metadata.put("k", "two")
        assertEquals("two", metadata.find("k"))
    }
}
