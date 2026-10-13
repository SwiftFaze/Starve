package dev.starve.infra

import dev.starve.domain.FeedRules
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Post images read from a raw feed body. This runs before [BodySanitizer], which drops the width and
 * height attributes that identify tracking pixels.
 */
object BodyImages {
    private val PIXEL_SIZES = setOf("0", "1")
    private val SIZE_ATTRIBUTES = listOf("width", "height")

    /** The first usable `<img>` of [html] (not a tracking pixel, http(s) once resolved against [base]). */
    fun firstUrl(
        html: String,
        base: String?,
    ): String? =
        Jsoup
            .parseBodyFragment(html)
            .select("img[src]")
            .asSequence()
            .filterNot { isTrackingPixel(it) }
            .firstNotNullOfOrNull { FeedRules.absoluteHttpUrl(it.attr("src"), base) }

    /** An image with a width or a height of 0 or 1 is a pixel, whatever it points at. */
    private fun isTrackingPixel(img: Element): Boolean = SIZE_ATTRIBUTES.any { img.attr(it).trim() in PIXEL_SIZES }
}
