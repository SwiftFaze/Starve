package dev.starve.infra

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import dev.starve.domain.AudioEnclosure
import dev.starve.domain.EpisodeRules
import dev.starve.domain.FeedRules
import org.jdom2.Element

/**
 * A podcast entry's audio, read from the feed: an audio enclosure, else an audio media:content (inside a
 * media:group too), with its length from itunes:duration, else the media:content's duration. Rome keeps the iTunes
 * and Media RSS elements as foreign markup.
 */
internal object EpisodeAudio {
    private const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"

    /** The entry's audio file, its URL resolved against [base]; null when it has none. */
    fun of(
        entry: SyndEntry,
        base: String?,
    ): AudioEnclosure? {
        val media = entry.foreignMarkup.flatMap { if (it.name == "group") it.children else listOf(it) }
        val content = media.firstOrNull { it.name == "content" && isAudio(it) }
        val url =
            entry.enclosures.firstOrNull { it.type?.startsWith("audio/") == true }?.url
                ?: content?.getAttributeValue("url")
        val absolute = url?.let { FeedRules.absoluteHttpUrl(it, base) } ?: return null
        val itunes = entry.foreignMarkup.firstOrNull { it.name == "duration" && it.namespaceURI == ITUNES }?.text
        val seconds =
            EpisodeRules.durationSeconds(itunes) ?: EpisodeRules.durationSeconds(content?.getAttributeValue("duration"))
        return AudioEnclosure(absolute, seconds)
    }

    /** The show's own art (its itunes:image, else its RSS image), which stands in for an episode without art. */
    fun showArt(feed: SyndFeed): String? {
        val itunes = feed.foreignMarkup.firstOrNull { it.name == "image" && it.namespaceURI == ITUNES }
        val url = itunes?.getAttributeValue("href") ?: feed.image?.url
        return url?.let { FeedRules.absoluteHttpUrl(it, feed.link) }
    }

    /** A media:content is audio when its type says so, or its medium does. */
    private fun isAudio(element: Element): Boolean =
        element.getAttributeValue("type")?.startsWith("audio/") == true ||
            element.getAttributeValue("medium") == "audio"
}
