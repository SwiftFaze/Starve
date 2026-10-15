package dev.starve.infra

import dev.starve.application.UserRepository
import dev.starve.domain.User
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant

@Repository
class JdbcUserRepository(
    private val jdbc: JdbcClient,
) : UserRepository {
    override fun exists(): Boolean =
        jdbc
            .sql("SELECT COUNT(*) FROM users")
            .query(Long::class.java)
            .single() > 0

    /** `username` is declared COLLATE NOCASE, so the lookup ignores case. */
    override fun findByUsername(username: String): User? =
        jdbc
            .sql("SELECT id, username, password_hash, admin FROM users WHERE username = ?")
            .param(username)
            .query { rs, _ -> rs.toUser() }
            .optional()
            .orElse(null)

    /** One statement, so the "no user yet" check and the insert cannot be separated by a concurrent request. */
    override fun insertFirstAdmin(
        username: String,
        passwordHash: String,
        at: Instant,
    ): Boolean =
        jdbc
            .sql(
                "INSERT INTO users (username, password_hash, admin, created_at) " +
                    "SELECT ?, ?, 1, ? WHERE NOT EXISTS (SELECT 1 FROM users)",
            ).params(username, passwordHash, at.toEpochMilli())
            .update() == 1

    override fun insert(
        username: String,
        passwordHash: String,
        at: Instant,
    ): User {
        val id =
            jdbc
                .sql("INSERT INTO users (username, password_hash, admin, created_at) VALUES (?, ?, 0, ?) RETURNING id")
                .params(username, passwordHash, at.toEpochMilli())
                .query(Long::class.java)
                .single()
        return User(id, username, passwordHash, admin = false)
    }
}

private fun ResultSet.toUser() =
    User(
        id = getLong("id"),
        username = getString("username"),
        passwordHash = getString("password_hash"),
        admin = getInt("admin") == 1,
    )
