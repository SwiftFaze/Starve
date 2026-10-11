package dev.starve.web

import dev.starve.application.PlayerResult
import dev.starve.application.PlayerService
import dev.starve.domain.Playable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.server.ResponseStatusException

@Controller
class PlayerController(
    private val player: PlayerService,
) {
    /** The inline player fragment of an item; requesting it marks the item read. */
    @GetMapping("/items/{id}/player")
    fun player(
        @PathVariable id: Long,
        model: Model,
    ): String {
        when (val result = player.player(id)) {
            PlayerResult.NotPlayable -> {
                throw ResponseStatusException(HttpStatus.NOT_FOUND)
            }

            is PlayerResult.EmbeddingDisabled -> {
                model.addAttribute("kind", "disabled")
                model.addAttribute("watchUrl", result.watchUrl)
            }

            is PlayerResult.Embed -> {
                model.addAttribute("kind", if (result.playable is Playable.YouTube) "youtube" else "soundcloud")
                model.addAttribute("embedUrl", result.playable.embedUrl)
            }
        }
        return "player :: content"
    }
}
