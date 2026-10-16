package dev.starve.web

/**
 * One favourite star, rendered by `templates/star.html`. [domId] is its element id; [from] names the place it sits in
 * (see [StarPlace]), so a toggle clicked there is answered as that place's; [oob] marks a star sent out of band with
 * another place's answer.
 */
data class StarView(
    val domId: String,
    val itemId: Long,
    val favourite: Boolean,
    val from: String? = null,
    val oob: Boolean = false,
) {
    /** The endpoint a click posts to: it reverses the current state. */
    val action: String
        get() {
            val verb = if (favourite) "unfavourite" else "favourite"
            val query = from?.let { "?from=$it" } ?: ""
            return "/items/$itemId/$verb$query"
        }
}

/**
 * The places an item's favourite star sits: its timeline row and the three panes that can show it. A toggle from any
 * of them answers its own star and refreshes the others out of band.
 */
enum class StarPlace(
    val from: String?,
    private val idPrefix: String,
) {
    ROW(null, "star"),
    DECK("deck", "deck-star"),
    READER("reader", "reader-star"),
    DETAIL("detail", "detail-star"),
    ;

    /** This place's star for [itemId]; its id names the item, so a refresh for an item not on screen matches nothing. */
    fun view(
        itemId: Long,
        favourite: Boolean,
        oob: Boolean = false,
    ) = StarView("$idPrefix-$itemId", itemId, favourite, from, oob)

    companion object {
        /** The place named by a toggle's `from` parameter; anything else, or none, is the timeline row. */
        fun from(param: String?): StarPlace = entries.firstOrNull { it.from != null && it.from == param } ?: ROW
    }
}
