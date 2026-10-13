package dev.starve.infra

import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.migration.Context
import org.flywaydb.core.api.migration.JavaMigration
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * V10: creates the `item_search` FTS5 table and indexes every item already stored. Java rather than SQL because
 * the body must be indexed as text (markup stripped) by the same [SearchText] the write path uses.
 */
@Component
class SearchIndexMigration : JavaMigration {
    override fun getVersion(): MigrationVersion = MigrationVersion.fromVersion("10")

    override fun getDescription(): String = "index titles, sources and post bodies for full-text search"

    override fun getChecksum(): Int? = null

    override fun canExecuteInTransaction(): Boolean = true

    override fun migrate(context: Context) {
        val connection = context.connection
        connection.createStatement().use { it.execute(CREATE_SQL) }
        insertAll(connection, storedItems(connection))
    }

    private fun storedItems(connection: Connection): List<IndexRow> =
        connection.createStatement().use { statement ->
            statement.executeQuery(SELECT_SQL).use { it.rows() }
        }

    private fun insertAll(
        connection: Connection,
        rows: List<IndexRow>,
    ) {
        connection.prepareStatement(INSERT_SQL).use { insert ->
            rows.forEach { row ->
                bind(insert, row)
                insert.addBatch()
            }
            insert.executeBatch()
        }
    }

    private fun bind(
        insert: PreparedStatement,
        row: IndexRow,
    ) {
        insert.setLong(ROWID, row.id)
        insert.setString(TITLE, row.title)
        insert.setString(SOURCE, row.source)
        insert.setString(BODY, SearchText.bodyText(row.body))
    }

    private fun ResultSet.rows(): List<IndexRow> =
        generateSequence {
            if (!next()) {
                null
            } else {
                IndexRow(getLong("id"), getString("title"), getString("source_title"), getString("body"))
            }
        }.toList()

    private data class IndexRow(
        val id: Long,
        val title: String,
        val source: String,
        val body: String?,
    )

    private companion object {
        const val CREATE_SQL =
            "CREATE VIRTUAL TABLE item_search USING fts5(title, source, body, " +
                "tokenize = 'unicode61 remove_diacritics 2')"
        const val SELECT_SQL =
            "SELECT i.id, i.title, s.title AS source_title, i.body FROM items i JOIN sources s ON s.id = i.source_id"
        const val INSERT_SQL = "INSERT INTO item_search (rowid, title, source, body) VALUES (?, ?, ?, ?)"

        // Parameter positions in INSERT_SQL.
        const val ROWID = 1
        const val TITLE = 2
        const val SOURCE = 3
        const val BODY = 4
    }
}
