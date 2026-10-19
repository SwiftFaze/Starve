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
    val id: Long,
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
    @AnnouncesRead
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
                model.addAttribute("star", StarPlace.DECK.view(result.item.id, result.item.favourite))
            }

            is PlayerResult.Embed -> {
                model.addAttribute("kind", kindOf(result.playable))
                model.addAttribute("embedUrl", result.playable.embedUrl)
                result.item.audio?.let { audio ->
                    model.addAttribute("listenUrl", result.item.link ?: audio.url)
                    model.addAttribute("duration", audio.durationSeconds)
                    model.addAttribute("total", audio.durationSeconds?.let(::playTime))
                }
                model.addAttribute("item", result.item.toView())
                model.addAttribute("star", StarPlace.DECK.view(result.item.id, result.item.favourite))
                model.addAttribute("resume", result.item.position)
                model.addAttribute("elapsed", result.item.position?.let(::playTime))
            }
        }
        return "player :: content"
    }

    private fun kindOf(playable: Playable): String =
        when (playable) {
            is Playable.YouTube -> "youtube"
            is Playable.SoundCloud -> "soundcloud"
            is Playable.Audio -> "audio"
        }

    private fun ItemRef.toView() = NowPlayingView(id, Catalogue.number(id), title, sourceTitle, thumbnailUrl)
}

/** [seconds] as the deck's clock shows a time: "41:12", or "1:02:03" from an hour on. */
internal fun playTime(seconds: Long): String {
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
