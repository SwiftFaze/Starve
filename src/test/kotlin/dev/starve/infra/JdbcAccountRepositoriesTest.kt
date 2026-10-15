package dev.starve.infra

import dev.starve.domain.User
import dev.starve.domain.UserAccount
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
            .javaMigrations(SearchIndexMigration())
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
    fun `the list is every account, oldest first, ties by id, without hashes`() {
        users.insertFirstAdmin("rob", "hash", now)
        users.insert("late", "h", now.plusSeconds(60))
        users.insert("tie-a", "h", now.plusSeconds(10))
        users.insert("tie-b", "h", now.plusSeconds(10))

        assertEquals(
            listOf("rob", "tie-a", "tie-b", "late"),
            users.listAll().map { it.username },
        )
        assertEquals(UserAccount(1, "rob", true, now), users.listAll().first())
        assertEquals(UserAccount(2, "late", false, now.plusSeconds(60)), users.listAll().last { it.id == 2L })
    }

    @Test
    fun `a user is found by id or not at all`() {
        users.insertFirstAdmin("rob", "hash", now)

        assertEquals(User(1, "rob", "hash", admin = true), users.findById(1))
        assertNull(users.findById(2))
    }

    @Test
    fun `updating a hash changes that user only`() {
        users.insertFirstAdmin("rob", "hash", now)
        users.insert("amy", "h2", now)

        users.updatePasswordHash(2, "new")

        assertEquals("new", users.findById(2)!!.passwordHash)
        assertEquals("hash", users.findById(1)!!.passwordHash)
    }

    @Test
    fun `deleting a user removes them and their data and reports whether anyone was there`() {
        users.insertFirstAdmin("rob", "hash", now)
        users.insert("amy", "h2", now)
        jdbc.sql("INSERT INTO sources (owner_id, url, title) VALUES (2, 'http://a.test/f', 'A')").update()
        jdbc.sql("INSERT INTO groups (owner_id, name) VALUES (2, 'G')").update()
        jdbc.sql("INSERT INTO sources (owner_id, url, title) VALUES (1, 'http://r.test/f', 'R')").update()

        assertTrue(users.delete(2))
        assertFalse(users.delete(2))

        assertNull(users.findById(2))
        assertEquals(1L, jdbc.sql("SELECT COUNT(*) FROM sources").query(Long::class.java).single())
        assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM groups").query(Long::class.java).single())
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
