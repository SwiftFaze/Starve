package dev.starve.web

/**
 * One ⏲ save toggle, rendered by `templates/later.html`. [domId] is its element id; [from] names the pane it sits in
 * ("reader" or "detail"), so its toggle also refreshes the timeline row's toggle; [oob] marks such a row toggle,
 * sent out of band with that response.
 */
data class LaterView(
    val domId: String,
    val itemId: Long,
    val saved: Boolean,
    val from: String? = null,
    val oob: Boolean = false,
) {
    /** The endpoint a click posts to: it reverses the current state. */
    val action: String
        get() {
            val verb = if (saved) "unlater" else "later"
            val query = from?.let { "?from=$it" } ?: ""
            return "/items/$itemId/$verb$query"
        }
}
