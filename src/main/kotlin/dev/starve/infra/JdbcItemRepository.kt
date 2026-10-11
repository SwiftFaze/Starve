package dev.starve.infra

import dev.starve.application.ItemRepository
import dev.starve.domain.FeedItem
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JdbcItemRepository(
    private val jdbc: JdbcClient,
) : ItemRepository {
    override fun insertNew(
        sourceId: Long,
        items: List<FeedItem>,
        firstSeenAt: Instant,
    ) {
        items.forEach { insertIfAbsent(sourceId, it, firstSeenAt) }
    }

    override fun deleteFirstSeenBefore(cutoff: Instant): Int =
        jdbc
            .sql("DELETE FROM items WHERE first_seen_at < ?")
            .param(cutoff.toEpochMilli())
            .update()

    private fun insertIfAbsent(
        sourceId: Long,
        item: FeedItem,
        firstSeenAt: Instant,
    ) {
        jdbc
            .sql(
                "INSERT OR IGNORE INTO items (source_id, item_key, title, link, published_at, " +
                    "author, thumbnail_url, first_seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ).params(
                listOf(
                    sourceId,
                    item.key,
                    item.title,
                    item.link,
                    (item.publishedAt ?: firstSeenAt).toEpochMilli(),
                    item.author,
                    item.thumbnailUrl,
                    firstSeenAt.toEpochMilli(),
                ),
            ).update()
    }
}
