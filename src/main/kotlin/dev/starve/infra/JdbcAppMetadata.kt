package dev.starve.infra

import dev.starve.application.AppMetadata
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcAppMetadata(
    private val jdbc: JdbcClient,
) : AppMetadata {
    override fun find(key: String): String? =
        jdbc
            .sql("SELECT value FROM app_metadata WHERE key = ?")
            .param(key)
            .query(String::class.java)
            .optional()
            .orElse(null)

    /** `INSERT OR IGNORE` on the primary key, so two starts racing cannot both store a key. */
    override fun putIfAbsent(
        key: String,
        value: String,
    ): Boolean =
        jdbc
            .sql("INSERT OR IGNORE INTO app_metadata (key, value) VALUES (?, ?)")
            .params(key, value)
            .update() == 1

    override fun put(
        key: String,
        value: String,
    ) {
        jdbc
            .sql(
                "INSERT INTO app_metadata (key, value) VALUES (?, ?) " +
                    "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            ).params(key, value)
            .update()
    }
}
