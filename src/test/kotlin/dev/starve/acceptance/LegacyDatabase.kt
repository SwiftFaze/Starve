package dev.starve.acceptance

import dev.starve.infra.DecodeItemTitlesMigration
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import java.time.Instant
import javax.sql.DataSource

/** A database file as an older release left it: migrated only part way, holding rows of that older shape. */
class LegacyDatabase(
    private val app: ApplicationHarness,
) {
    private fun dataSource(): DataSource = SimpleDriverDataSource(org.sqlite.JDBC(), "jdbc:sqlite:${app.dbPath}")

    /** Stops the application, starts a fresh file and migrates it only up to [version]. */
    fun migrateOnlyTo(version: String) {
        app.freshDatabase()
        Flyway
            .configure()
            .dataSource(dataSource())
            .locations("classpath:db/migration")
            .javaMigrations(DecodeItemTitlesMigration())
            .target(MigrationVersion.fromVersion(version))
            .load()
            .migrate()
    }

    /** Inserts source [source] holding one item titled each of [titles], bypassing the application. */
    fun hold(
        source: String,
        vararg titles: String,
    ) {
        val jdbc = JdbcClient.create(dataSource())
        val sourceId =
            jdbc
                .sql("INSERT INTO sources (url, title) VALUES (?, ?) RETURNING id")
                .params("http://blog.test/feed", source)
                .query(Long::class.java)
                .single()
        val seen = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        titles.forEachIndexed { index, title ->
            jdbc
                .sql(
                    "INSERT INTO items (source_id, item_key, title, published_at, first_seen_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                ).params(sourceId, "item-$index", title, seen, seen)
                .update()
        }
    }
}
