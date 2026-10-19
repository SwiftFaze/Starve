package dev.starve.application

import dev.starve.domain.Playable
import dev.starve.domain.ReadMark
import java.time.Clock

class DetailService(
    private val items: ItemRepository,
    private val soundcloud: SoundCloudOembed,
    private val clock: Clock,
) {
    /**
     * The detail view of [owner]'s playable item, marked read and opened; null for an unknown or another user's item
     * and for one that is not a YouTube video, SoundCloud track or podcast episode. SoundCloud's oEmbed is fetched
     * once: a successful answer is stored with the item, a failed one is not, so the next open asks again.
     */
    fun open(
        owner: Long,
        id: Long,
    ): ItemDetail? {
        val item = items.findDetail(owner, id)
        val playable = Playable.of(item?.link, item?.audioUrl)
        if (item == null || playable == null) return null
        items.markRead(owner, id, ReadMark.opened(clock.instant()))
        val oembed = if (playable is Playable.SoundCloud) soundCloudOembed(item, playable.trackUrl) else null
        return ItemDetail(
            id = item.id,
            link = item.link,
            title = item.title,
            sourceTitle = item.sourceTitle,
            publishedAt = item.publishedAt,
            artist = oembed?.authorName ?: item.author,
            thumbnailUrl = oembed?.thumbnailUrl ?: item.thumbnailUrl,
            description = oembed?.description ?: item.body,
            later = item.later,
            sourceUrl = item.sourceUrl,
            favourite = item.favourite,
        )
    }

    private fun soundCloudOembed(
        item: DetailRef,
        trackUrl: String,
    ): Oembed? = item.oembed ?: soundcloud.fetch(trackUrl)?.also { items.saveOembed(item.id, it) }
}
