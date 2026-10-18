package dev.starve.infra

import dev.starve.domain.ItemTitles
import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.migration.Context
import org.flywaydb.core.api.migration.JavaMigration
import org.springframework.stereotype.Component
import java.sql.ResultSet

/**
 * V21: gives items stored as [ItemTitles.UNTITLED] before titles were derived (Bluesky and Mastodon posts) a title
 * from the start of their body's text, once, in `items` and in their search row. Java rather than SQL because the
 * text comes from jsoup; an item with no body, or one with no text, stays untitled.
 */
@Component
class DeriveItemTitlesMigration : JavaMigration {
    override fun getVersion(): MigrationVersion = MigrationVersion.fromVersion("21")

    override fun getDescription(): String = "derive titles of untitled items from their text"

    override fun getChecksum(): Int? = null

    override fun canExecuteInTransaction(): Boolean = true

    override fun migrate(context: Context) {
        val derived =
            untitled(context).mapNotNull { (id, body) ->
                ItemTitles.derived(JsoupText.plainText(body)).takeIf { it != ItemTitles.UNTITLED }?.let { id to it }
            }
        listOf("UPDATE items SET title = ? WHERE id = ?", "UPDATE item_search SET title = ? WHERE rowid = ?").forEach {
            context.connection.prepareStatement(it).use { update ->
                derived.forEach { (id, title) ->
                    update.setString(1, title)
                    update.setLong(2, id)
                    update.addBatch()
                }
                update.executeBatch()
            }
        }
    }

    private fun untitled(context: Context): List<Pair<Long, String>> =
        context.connection.prepareStatement("SELECT id, body FROM items WHERE title = ? AND body IS NOT NULL").use {
            it.setString(1, ItemTitles.UNTITLED)
            it.executeQuery().use { rows -> rows.pairs() }
        }

    private fun ResultSet.pairs(): List<Pair<Long, String>> =
        generateSequence { if (next()) getLong("id") to getString("body") else null }.toList()
}
