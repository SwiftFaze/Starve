package dev.starve.infra

import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.migration.Context
import org.flywaydb.core.api.migration.JavaMigration
import org.springframework.stereotype.Component
import java.sql.ResultSet

/**
 * V5: decodes item titles stored before [TitleEntities] existed, once. Java rather than SQL because SQLite
 * has no HTML decoding; only rows whose title contains an `&` are read, and only those whose decoded form
 * differs are rewritten.
 */
@Component
class DecodeItemTitlesMigration : JavaMigration {
    override fun getVersion(): MigrationVersion = MigrationVersion.fromVersion("5")

    override fun getDescription(): String = "decode html entities in item titles"

    override fun getChecksum(): Int? = null

    override fun canExecuteInTransaction(): Boolean = true

    override fun migrate(context: Context) {
        val changed =
            storedTitles(context).mapNotNull { (id, title) ->
                TitleEntities.decode(title).takeIf { it != title }?.let { id to it }
            }
        context.connection.prepareStatement("UPDATE items SET title = ? WHERE id = ?").use { update ->
            changed.forEach { (id, title) ->
                update.setString(1, title)
                update.setLong(2, id)
                update.addBatch()
            }
            update.executeBatch()
        }
    }

    private fun storedTitles(context: Context): List<Pair<Long, String>> =
        context.connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id, title FROM items WHERE instr(title, '&') > 0").use { it.rows() }
        }

    private fun ResultSet.rows(): List<Pair<Long, String>> =
        generateSequence { if (next()) getLong("id") to getString("title") else null }.toList()
}
