package dev.starve.application

import dev.starve.domain.GroupRules
import dev.starve.domain.NameCheck
import dev.starve.domain.Tag
import dev.starve.domain.TagRules
import dev.starve.domain.TermsCheck

sealed interface TagResult {
    data class Saved(
        val tag: Tag,
    ) : TagResult

    data object Blank : TagResult

    data object TooLong : TagResult

    data object Duplicate : TagResult

    data object NoTerms : TagResult

    data object TooManyTerms : TagResult

    /** [term] is not words with an optional trailing `*`. */
    data class BadTerm(
        val term: String,
    ) : TagResult

    /** The tag, or the group it is limited to, is not the owner's. */
    data object Missing : TagResult
}

/** The items this tag lists: its group's sources' (all when it has none) whose text matches its terms. */
val Tag.narrowing: Narrowing get() = Narrowing(groupId, match)

/** What was entered for a tag: its name, its comma-separated terms, whether bodies match, and its limiting group. */
data class TagForm(
    val name: String,
    val terms: String,
    val matchBody: Boolean = false,
    val groupId: Long? = null,
)

/** Creating, editing and deleting a user's keyword tags. Names follow the group name rules, unique among tags. */
class TagService(
    private val tags: TagRepository,
    private val groups: GroupRepository,
) {
    fun list(owner: Long): List<Tag> = tags.findAll(owner)

    fun create(
        owner: Long,
        form: TagForm,
    ): TagResult = checked(owner, null, form) { tags.insert(owner, it) }

    /** Replaces [owner]'s tag [id] with [form]; renaming it to a case-variant of its own name is allowed. */
    fun update(
        owner: Long,
        id: Long,
        form: TagForm,
    ): TagResult {
        if (tags.findById(owner, id) == null) return TagResult.Missing
        return checked(owner, id, form) { it.also { tags.update(owner, it) } }
    }

    /** Deletes [owner]'s tag [id], never its items; false when [owner] has no such tag. */
    fun delete(
        owner: Long,
        id: Long,
    ): Boolean = tags.delete(owner, id)

    private fun checked(
        owner: Long,
        id: Long?,
        form: TagForm,
        save: (Tag) -> Tag,
    ): TagResult {
        if (form.groupId != null && groups.findById(owner, form.groupId) == null) return TagResult.Missing
        val name =
            when (val check = GroupRules.checkName(form.name)) {
                NameCheck.Blank -> return TagResult.Blank
                NameCheck.TooLong -> return TagResult.TooLong
                is NameCheck.Valid -> check.name
            }
        if (tags.findByName(owner, name)?.let { it.id != id } == true) return TagResult.Duplicate
        return when (val check = TagRules.checkTerms(form.terms)) {
            TermsCheck.Empty -> TagResult.NoTerms
            TermsCheck.TooMany -> TagResult.TooManyTerms
            is TermsCheck.Invalid -> TagResult.BadTerm(check.term)
            is TermsCheck.Valid -> TagResult.Saved(save(Tag(id ?: 0, name, check.terms, form.matchBody, form.groupId)))
        }
    }
}
