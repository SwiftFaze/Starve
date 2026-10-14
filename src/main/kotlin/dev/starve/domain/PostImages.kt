package dev.starve.domain

/** Which images a post shows in the reader. */
object PostImages {
    /**
     * The feed image to show above the body: the item's thumbnail, unless the stored [body] already shows
     * that exact image, so one picture is never shown twice.
     */
    fun headerImage(
        thumbnailUrl: String?,
        body: String?,
    ): String? = thumbnailUrl?.takeUnless { body != null && body.contains("src=\"${attribute(it)}\"") }

    // Sanitised bodies write attribute values with these escapes.
    private fun attribute(url: String) = url.replace("&", "&amp;").replace("\"", "&quot;")
}
