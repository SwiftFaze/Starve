package dev.starve.acceptance

import dev.starve.infra.DecodeItemTitlesMigration
import dev.starve.infra.SearchIndexMigration
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import java.time.Duration
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
        app.markLegacy()
        Flyway
            .configure()
            .dataSource(dataSource())
            .locations("classpath:db/migration")
            .javaMigrations(DecodeItemTitlesMigration(), SearchIndexMigration())
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

    /** Inserts source [source] holding one item titled [title] with stored post body [body], bypassing the app. */
    fun holdWithBody(
        source: String,
        title: String,
        body: String,
    ) {
        val jdbc = JdbcClient.create(dataSource())
        val sourceId =
            jdbc
                .sql("INSERT INTO sources (url, title) VALUES (?, ?) RETURNING id")
                .params("http://blog.test/feed", source)
                .query(Long::class.java)
                .single()
        val seen = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        jdbc
            .sql(
                "INSERT INTO items (source_id, item_key, title, body, published_at, first_seen_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
            ).params(sourceId, "item-0", title, body, seen, seen)
            .update()
    }

    /** Inserts source [source] holding [count] items published now and read a day ago, bypassing the application. */
    fun holdRead(
        source: String,
        count: Int,
    ) {
        val jdbc = JdbcClient.create(dataSource())
        val sourceId =
            jdbc
                .sql("INSERT INTO sources (url, title) VALUES (?, ?) RETURNING id")
                .params("http://blog.test/feed", source)
                .query(Long::class.java)
                .single()
        val now = Instant.now()
        val yesterday = now.minus(Duration.ofDays(1)).toEpochMilli()
        repeat(count) {
            jdbc
                .sql(
                    "INSERT INTO items (source_id, item_key, title, published_at, first_seen_at, read_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                ).params(
                    sourceId,
                    "item-$it",
                    "Read $it",
                    now.toEpochMilli(),
                    now.toEpochMilli(),
                    yesterday,
                ).update()
        }
    }
}
