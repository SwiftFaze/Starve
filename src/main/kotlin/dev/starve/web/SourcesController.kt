package dev.starve.web

import dev.starve.application.GroupResult
import dev.starve.application.GroupService
import dev.starve.application.SubscribeResult
import dev.starve.application.SubscriptionService
import dev.starve.application.TagForm
import dev.starve.application.TagResult
import dev.starve.application.TagService
import dev.starve.domain.GroupRules
import dev.starve.domain.TagRules
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
class SourcesController(
    private val subscriptions: SubscriptionService,
    private val groups: GroupService,
    private val roster: SourceRoster,
    private val tags: TagService,
) {
    /**
     * The roster. [remove], [deleteGroup] or [deleteTag] names a row to show in its confirming state, which holds the
     * form that really removes it: the first click only asks.
     */
    @GetMapping("/sources")
    fun sources(
        @CurrentUserId owner: Long,
        model: Model,
        @RequestParam(required = false) remove: Long? = null,
        @RequestParam(name = "delete-group", required = false) deleteGroup: Long? = null,
        @RequestParam(name = "delete-tag", required = false) deleteTag: Long? = null,
    ): String {
        model.addAttribute("removing", remove)
        model.addAttribute("deletingGroup", deleteGroup)
        model.addAttribute("deletingTag", deleteTag)
        return render(owner, model, null)
    }

    /** A rejected [url] comes back in its field, with the reason under it. */
    @PostMapping("/sources")
    fun add(
        @CurrentUserId owner: Long,
        @RequestParam url: String,
        model: Model,
    ): String {
        val result = subscriptions.subscribe(owner, url)
        if (result is SubscribeResult.Subscribed) return render(owner, model, messageFor(result))
        model.addAttribute("url", url)
        model.addAttribute("addError", messageFor(result))
        return render(owner, model, null)
    }

    /** Another user's source is answered like an unknown one: 404. */
    @PostMapping("/sources/{id}/delete")
    fun remove(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
    ): String {
        if (!subscriptions.unsubscribe(owner, id)) throw notFound()
        return "redirect:/sources"
    }

    @PostMapping("/groups")
    fun createGroup(
        @CurrentUserId owner: Long,
        @RequestParam name: String,
        model: Model,
    ): String = groupOutcome(owner, groups.create(owner, name), model)

    @PostMapping("/groups/{id}/rename")
    fun renameGroup(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        @RequestParam(required = false) name: String?,
        model: Model,
    ): String = groupOutcome(owner, groups.rename(owner, id, name.orEmpty()), model)

    @PostMapping("/groups/{id}/delete")
    fun deleteGroup(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
    ): String {
        if (!groups.delete(owner, id)) throw notFound()
        return "redirect:/sources"
    }

    /** A rejected new tag comes back in its form, with the reason above the page. */
    @PostMapping("/tags")
    fun createTag(
        @CurrentUserId owner: Long,
        @ModelAttribute entry: TagEntry,
        model: Model,
    ): String {
        val result = tags.create(owner, entry.toForm())
        if (result !is TagResult.Saved) {
            model.addAttribute("tagName", entry.name)
            model.addAttribute("tagTerms", entry.terms)
            model.addAttribute("tagBody", entry.body != null)
            model.addAttribute("tagGroup", entry.groupId())
        }
        return tagOutcome(owner, result, model)
    }

    /** Another user's tag, or a group that is not the user's, is answered like an unknown one: 404. */
    @PostMapping("/tags/{id}")
    fun updateTag(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        @ModelAttribute entry: TagEntry,
        model: Model,
    ): String = tagOutcome(owner, tags.update(owner, id, entry.toForm()), model)

    @PostMapping("/tags/{id}/delete")
    fun deleteTag(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
    ): String {
        if (!tags.delete(owner, id)) throw notFound()
        return "redirect:/sources"
    }

    /** An empty or absent [group] puts the source back in "Ungrouped". */
    @PostMapping("/sources/{id}/group")
    fun assignGroup(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        @RequestParam(required = false) group: String?,
    ): String {
        val groupId = group?.takeIf { it.isNotBlank() }?.let { it.toLongOrNull() ?: throw notFound() }
        if (!groups.assign(owner, id, groupId)) throw notFound()
        return "redirect:/sources"
    }

    private fun groupOutcome(
        owner: Long,
        result: GroupResult,
        model: Model,
    ): String =
        when (result) {
            is GroupResult.Saved -> "redirect:/sources"
            GroupResult.Missing -> throw notFound()
            GroupResult.Blank -> render(owner, model, "group name is required", error = true)
            GroupResult.TooLong -> render(owner, model, TOO_LONG, error = true)
            GroupResult.Duplicate -> render(owner, model, "a group with that name already exists", error = true)
        }

    private fun tagOutcome(
        owner: Long,
        result: TagResult,
        model: Model,
    ): String {
        val rejection =
            when (result) {
                is TagResult.Saved -> return "redirect:/sources"
                TagResult.Missing -> throw notFound()
                TagResult.Blank -> "tag name is required"
                TagResult.TooLong -> TOO_LONG
                TagResult.Duplicate -> "a tag with that name already exists"
                TagResult.NoTerms -> "at least one term is required"
                TagResult.TooManyTerms -> "at most ${TagRules.MAX_TERMS} terms"
                is TagResult.BadTerm -> "\"${result.term}\" is not a valid term: $TERM_RULE"
            }
        return render(owner, model, rejection, error = true)
    }

    private fun render(
        owner: Long,
        model: Model,
        message: String?,
        error: Boolean = false,
    ): String {
        roster.addTo(model, owner)
        model.addAttribute("message", message)
        model.addAttribute("error", error)
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

private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

private const val TOO_LONG = "at most ${GroupRules.MAX_NAME_LENGTH} characters"

/** A tag form as posted: [body] is present when "Match the body too" is ticked, and an empty [group] is any group. */
data class TagEntry(
    val name: String = "",
    val terms: String = "",
    val body: String? = null,
    val group: String? = null,
) {
    /** The group id, null for any group; not a number is a 404. */
    fun groupId(): Long? = group?.takeIf { it.isNotBlank() }?.let { it.toLongOrNull() ?: throw notFound() }

    fun toForm() = TagForm(name, terms, body != null, groupId())
}

private const val TERM_RULE = "use letters, digits and spaces, with * only at the end"
