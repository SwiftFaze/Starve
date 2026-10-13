package dev.starve.application

import dev.starve.domain.Playable
import java.time.Clock

class ReaderService(
    private val items: ItemRepository,
    private val clock: Clock,
) {
    /**
     * The post to show in the reader, marked read; null for an unknown item and for a playable one, which
     * keeps opening through the player.
     */
    fun open(id: Long): PostRef? {
        val post = items.findPost(id)
        if (post == null || Playable.of(post.link) != null) return null
        items.markRead(id, clock.instant())
        return post
    }
}
