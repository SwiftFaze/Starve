package dev.starve.web

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

/** A tag form as posted: [body] is present when "Match the body too" is ticked, and an empty [group] is any group. */
data class TagEntry(
    val name: String = "",
    val terms: String = "",
    val body: String? = null,
    val group: String? = null,
) {
    /** The group id, null for any group; anything but a number is a 404. */
    fun groupId(): Long? = group?.takeIf { it.isNotBlank() }?.let { it.toLongOrNull() ?: throw notFound() }

    fun toForm() = TagForm(name, terms, body != null, groupId())
}

/** The Tags section of Sources: making, editing and deleting keyword tags. A rejection redraws Sources with why. */
@Controller
class TagController(
    private val tags: TagService,
    private val roster: SourceRoster,
) {
    /** Sources with the tag [deleteTag] in its confirming state, holding the form that really deletes it. */
    @GetMapping("/sources", params = ["delete-tag"])
    fun confirmDelete(
        @CurrentUserId owner: Long,
        @RequestParam(name = "delete-tag") deleteTag: Long,
        model: Model,
    ): String {
        model.addAttribute("deletingTag", deleteTag)
        return roster.page(model, owner, null)
    }

    /** A rejected new tag comes back in its form. */
    @PostMapping("/tags")
    fun create(
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
        return outcome(owner, result, model)
    }

    /** Another user's tag, or a group that is not the user's, is answered like an unknown one: 404. */
    @PostMapping("/tags/{id}")
    fun update(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
        @ModelAttribute entry: TagEntry,
        model: Model,
    ): String = outcome(owner, tags.update(owner, id, entry.toForm()), model)

    @PostMapping("/tags/{id}/delete")
    fun delete(
        @CurrentUserId owner: Long,
        @PathVariable id: Long,
    ): String {
        if (!tags.delete(owner, id)) throw notFound()
        return "redirect:/sources"
    }

    private fun outcome(
        owner: Long,
        result: TagResult,
        model: Model,
    ): String {
        val rejection =
            when (result) {
                is TagResult.Saved -> return "redirect:/sources"
                TagResult.Missing -> throw notFound()
                TagResult.Blank -> "tag name is required"
                TagResult.TooLong -> "at most ${GroupRules.MAX_NAME_LENGTH} characters"
                TagResult.Duplicate -> "a tag with that name already exists"
                TagResult.NoTerms -> "at least one term is required"
                TagResult.TooManyTerms -> "at most ${TagRules.MAX_TERMS} terms"
                is TagResult.BadTerm -> "\"${result.term}\" is not a valid term: $TERM_RULE"
            }
        return roster.page(model, owner, rejection, error = true)
    }
}

private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

private const val TERM_RULE = "use letters, digits and spaces, with * only at the end"
