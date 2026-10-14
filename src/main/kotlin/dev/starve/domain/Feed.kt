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
    /** Sanitised HTML of the post, or null when the entry had none worth showing. */
    val body: String? = null,
)

/** A parsed feed: its own title, when it has one, its site link, when it has one, and its entries. */
data class Feed(
    val title: String?,
    val items: List<FeedItem>,
    val siteLink: String? = null,
)
