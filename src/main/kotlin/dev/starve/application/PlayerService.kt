package dev.starve.application

import dev.starve.domain.Playable
import java.time.Clock

sealed interface PlayerResult {
    /** No such item, or its link is not playable. */
    data object NotPlayable : PlayerResult

    data class Embed(
        val playable: Playable,
    ) : PlayerResult

    /** The video owner disabled embedding; offer [watchUrl] instead. */
    data class EmbeddingDisabled(
        val watchUrl: String,
    ) : PlayerResult
}

class PlayerService(
    private val items: ItemRepository,
    private val embeds: EmbedChecker,
    private val clock: Clock,
) {
    /** Resolves the player of an item and marks the item read when it is playable. */
    fun player(id: Long): PlayerResult {
        val playable = Playable.of(items.findLink(id)?.link) ?: return PlayerResult.NotPlayable
        items.markRead(id, clock.instant())
        return if (playable is Playable.YouTube && !embeds.allowsEmbedding(playable)) {
            PlayerResult.EmbeddingDisabled(playable.watchUrl)
        } else {
            PlayerResult.Embed(playable)
        }
    }
}
