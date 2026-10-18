package dev.starve.application

import dev.starve.domain.Playable
import dev.starve.domain.ReadMark
import java.time.Clock

sealed interface PlayerResult {
    /** No such item, or its link is not playable. */
    data object NotPlayable : PlayerResult

    data class Embed(
        val playable: Playable,
        val item: ItemRef,
    ) : PlayerResult

    /** The video owner disabled embedding; offer [watchUrl] instead. */
    data class EmbeddingDisabled(
        val watchUrl: String,
        val item: ItemRef,
    ) : PlayerResult
}

class PlayerService(
    private val items: ItemRepository,
    private val embeds: EmbedChecker,
    private val clock: Clock,
) {
    /** Resolves the player of [owner]'s item and marks the item read and opened when it is playable. */
    fun player(
        owner: Long,
        id: Long,
    ): PlayerResult {
        val item = items.find(owner, id)
        val playable = Playable.of(item?.link, item?.audio?.url)
        if (item == null || playable == null) return PlayerResult.NotPlayable
        items.markRead(owner, id, ReadMark.opened(clock.instant()))
        return if (playable is Playable.YouTube && !embeds.allowsEmbedding(playable)) {
            PlayerResult.EmbeddingDisabled(playable.watchUrl, item)
        } else {
            PlayerResult.Embed(playable, item)
        }
    }
}
