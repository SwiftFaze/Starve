package dev.starve.domain

import java.time.Instant

/** One parsed feed entry. [key] is its per-source identity (guid, else link). */
data class FeedItem(
    val key: String,
    val title: String,
    val link: String?,
    val publishedAt: Instant?,
    val author: String?,
    val thumbnailUrl: String?,
)

/** A parsed feed: its own title, when it has one, and its entries. */
data class Feed(
    val title: String?,
    val items: List<FeedItem>,
)
