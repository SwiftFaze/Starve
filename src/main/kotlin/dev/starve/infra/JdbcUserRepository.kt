package dev.starve.infra

import dev.starve.application.UserAdminRepository
import dev.starve.application.UserRepository
import dev.starve.domain.User
import dev.starve.domain.UserAccount
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant

@Repository
class JdbcUserRepository(
    private val jdbc: JdbcClient,
) : UserRepository,
    UserAdminRepository {
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

    /** One statement, so two signups for one name cannot both pass the check; the loser gets null, not an error. */
    override fun insertIfFree(
        username: String,
        passwordHash: String,
        at: Instant,
    ): User? =
        jdbc
            .sql(
                "INSERT INTO users (username, password_hash, admin, created_at) " +
                    "SELECT ?, ?, 0, ? WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = ?) RETURNING id",
            ).params(username, passwordHash, at.toEpochMilli(), username)
            .query(Long::class.java)
            .optional()
            .map { User(it, username, passwordHash, admin = false) }
            .orElse(null)

    override fun listAll(): List<UserAccount> =
        jdbc
            .sql("SELECT id, username, admin, created_at FROM users ORDER BY created_at, id")
            .query { rs, _ ->
                UserAccount(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getInt("admin") == 1,
                    Instant.ofEpochMilli(rs.getLong("created_at")),
                )
            }.list()

    override fun findById(id: Long): User? =
        jdbc
            .sql("SELECT id, username, password_hash, admin FROM users WHERE id = ?")
            .param(id)
            .query { rs, _ -> rs.toUser() }
            .optional()
            .orElse(null)

    override fun updatePasswordHash(
        id: Long,
        passwordHash: String,
    ) {
        jdbc
            .sql("UPDATE users SET password_hash = ? WHERE id = ?")
            .params(passwordHash, id)
            .update()
    }

    /** The sources, groups and items of the user go with them through the foreign keys' ON DELETE CASCADE. */
    override fun delete(id: Long): Boolean =
        jdbc
            .sql("DELETE FROM users WHERE id = ?")
            .param(id)
            .update() == 1
}

private fun ResultSet.toUser() =
    User(
        id = getLong("id"),
        username = getString("username"),
        passwordHash = getString("password_hash"),
        admin = getInt("admin") == 1,
    )
