package dev.starve.application

import dev.starve.domain.FaviconRules
import dev.starve.domain.Source
import java.time.Instant

/**
 * Looks up a source's site icon when [FaviconRules] says it is due. A lookup that cannot reach the site keeps
 * the icon the source already had; it never fails the poll that asked for it.
 */
class FaviconService(
    private val sources: SourceRepository,
    private val finder: FaviconFinder,
) {
    /** [siteLink] is the feed's own site link, when the fetch brought one (a not-modified poll does not). */
    fun refresh(
        source: Source,
        siteLink: String?,
        now: Instant,
    ) {
        if (!FaviconRules.isDue(source.faviconCheckedAt, now)) return
        val icon =
            try {
                finder.find(FaviconRules.siteUrl(siteLink, source.url))
            } catch (_: FeedFetchException) {
                source.faviconUrl
            }
        sources.recordFavicon(source.id, icon, now)
    }
}
