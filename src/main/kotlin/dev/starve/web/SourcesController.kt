package dev.starve.web

import dev.starve.application.GroupResult
import dev.starve.application.GroupService
import dev.starve.application.SubscribeResult
import dev.starve.application.SubscriptionService
import dev.starve.domain.GroupRules
import dev.starve.domain.Source
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SourceView(
    val id: Long,
    val title: String,
    val url: String,
    val lastFetched: String?,
    val error: String?,
    val groupId: Long?,
)

@Controller
class SourcesController(
    private val subscriptions: SubscriptionService,
    private val groups: GroupService,
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

    @PostMapping("/groups")
    fun createGroup(
        @RequestParam name: String,
        model: Model,
    ): String = groupOutcome(groups.create(name), model)

    @PostMapping("/groups/{id}/rename")
    fun renameGroup(
        @PathVariable id: Long,
        @RequestParam name: String,
        model: Model,
    ): String = groupOutcome(groups.rename(id, name), model)

    @PostMapping("/groups/{id}/delete")
    fun deleteGroup(
        @PathVariable id: Long,
    ): String {
        groups.delete(id)
        return "redirect:/sources"
    }

    /** An empty or absent [group] puts the source back in "Ungrouped". */
    @PostMapping("/sources/{id}/group")
    fun assignGroup(
        @PathVariable id: Long,
        @RequestParam(required = false) group: String?,
    ): String {
        val groupId = group?.takeIf { it.isNotBlank() }?.let { it.toLongOrNull() ?: throw notFound() }
        if (!groups.assign(id, groupId)) throw notFound()
        return "redirect:/sources"
    }

    private fun groupOutcome(
        result: GroupResult,
        model: Model,
    ): String =
        when (result) {
            is GroupResult.Saved -> "redirect:/sources"
            GroupResult.Missing -> throw notFound()
            GroupResult.Blank -> render(model, "group name is required")
            GroupResult.TooLong -> render(model, "at most ${GroupRules.MAX_NAME_LENGTH} characters")
            GroupResult.Duplicate -> render(model, "a group with that name already exists")
        }

    private fun render(
        model: Model,
        message: String?,
    ): String {
        model.addAttribute("sources", subscriptions.list().map { it.toView() })
        model.addAttribute("groups", groups.list())
        model.addAttribute("message", message)
        return "sources"
    }

    private fun messageFor(result: SubscribeResult): String =
        when (result) {
            is SubscribeResult.Subscribed -> "Subscribed to ${result.source.title}"
            SubscribeResult.NotHttpUrl -> "The feed URL must be an http or https URL"
            SubscribeResult.AlreadySubscribed -> "You are already subscribed to that feed"
            SubscribeResult.NotAFeed -> "That URL is not a valid RSS or Atom feed"
            SubscribeResult.NoFeedFound -> "Cannot subscribe: no feed found at this address"
            SubscribeResult.Unreachable -> "That URL could not be fetched"
        }
}

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)

private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

private fun Source.toView(): SourceView {
    val fetched = lastFetchedAt?.let { TIME_FORMAT.format(it) }
    return SourceView(id, title, url, fetched, lastError, groupId)
}
