package dev.starve.web

import dev.starve.application.ItemRef
import dev.starve.application.PlayerResult
import dev.starve.application.PlayerService
import dev.starve.domain.Catalogue
import dev.starve.domain.Playable
import dev.starve.domain.Waveform
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.server.ResponseStatusException

/** What the dock shows about the playing item. */
data class NowPlayingView(
    val catalogueNumber: String,
    val title: String,
    val sourceTitle: String,
    val thumbnailUrl: String?,
)

@Controller
class PlayerController(
    private val player: PlayerService,
) {
    /** The docked player fragment of an item; requesting it marks the item read. */
    @GetMapping("/items/{id}/player")
    fun player(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        model: Model,
    ): String {
        model.addAttribute("waveform", Waveform.bars(id))
        when (val result = player.player(owner, id)) {
            PlayerResult.NotPlayable -> {
                throw ResponseStatusException(HttpStatus.NOT_FOUND)
            }

            is PlayerResult.EmbeddingDisabled -> {
                model.addAttribute("kind", "disabled")
                model.addAttribute("watchUrl", result.watchUrl)
                model.addAttribute("item", result.item.toView())
            }

            is PlayerResult.Embed -> {
                model.addAttribute("kind", if (result.playable is Playable.YouTube) "youtube" else "soundcloud")
                model.addAttribute("embedUrl", result.playable.embedUrl)
                model.addAttribute("item", result.item.toView())
            }
        }
        return "player :: content"
    }

    private fun ItemRef.toView() = NowPlayingView(Catalogue.number(id), title, sourceTitle, thumbnailUrl)
}
