package dev.starve.web

/**
 * One favourite star, rendered by `templates/star.html`. [domId] is its element id; [fromReader] marks the
 * reader pane's star, whose toggle also refreshes the timeline row's star; [oob] marks such a row star,
 * sent out of band with that response.
 */
data class StarView(
    val domId: String,
    val itemId: Long,
    val favourite: Boolean,
    val fromReader: Boolean = false,
    val oob: Boolean = false,
) {
    /** The endpoint a click posts to: it reverses the current state. */
    val action: String
        get() {
            val verb = if (favourite) "unfavourite" else "favourite"
            val from = if (fromReader) "?from=reader" else ""
            return "/items/$itemId/$verb$from"
        }
}
