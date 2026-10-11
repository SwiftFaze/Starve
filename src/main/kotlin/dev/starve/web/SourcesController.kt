package dev.starve.web

import dev.starve.application.SubscribeResult
import dev.starve.application.SubscriptionService
import dev.starve.domain.Source
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SourceView(
    val id: Long,
    val title: String,
    val url: String,
    val lastFetched: String?,
    val error: String?,
)

@Controller
class SourcesController(
    private val subscriptions: SubscriptionService,
) {
    @GetMapping("/sources")
    fun sources(model: Model): String = render(model, null)

    @PostMapping("/sources")
    fun add(
        @RequestParam url: String,
        model: Model,
    ): String = render(model, messageFor(subscriptions.subscribe(url)))

    @PostMapping("/sources/{id}/delete")
    fun remove(
        @PathVariable id: Long,
    ): String {
        subscriptions.unsubscribe(id)
        return "redirect:/sources"
    }

    private fun render(
        model: Model,
        message: String?,
    ): String {
        model.addAttribute("sources", subscriptions.list().map { it.toView() })
        model.addAttribute("message", message)
        return "sources"
    }

    private fun messageFor(result: SubscribeResult): String =
        when (result) {
            is SubscribeResult.Subscribed -> "Subscribed to ${result.source.title}"
            SubscribeResult.NotHttpUrl -> "The feed URL must be an http or https URL"
            SubscribeResult.AlreadySubscribed -> "You are already subscribed to that feed"
            SubscribeResult.NotAFeed -> "That URL is not a valid RSS or Atom feed"
            SubscribeResult.Unreachable -> "That URL could not be fetched"
        }

    private fun Source.toView() = SourceView(id, title, url, lastFetchedAt?.let { TIME_FORMAT.format(it) }, lastError)

    private companion object {
        val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)
    }
}
