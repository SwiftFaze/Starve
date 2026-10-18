package dev.starve.infra

import dev.starve.application.BackgroundPoller
import dev.starve.application.BackgroundRunner
import dev.starve.application.GroupRepository
import dev.starve.application.ImportService
import dev.starve.application.OpmlReader
import dev.starve.application.PollService
import dev.starve.application.SourceRepository
import dev.starve.domain.PlatformSite
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors

private const val YOUTUBE_FEED_TEMPLATE = "https://www.youtube.com/feeds/videos.xml?channel_id={id}"

/** Wires bulk import: file parsing, the background poll of newly imported sources, and the service itself. */
@Configuration
class ImportConfig(
    @Value("\${starve.youtube.feed-url-template:$YOUTUBE_FEED_TEMPLATE}") private val youtubeFeedUrlTemplate: String,
    private val platforms: List<PlatformSite>,
) {
    @Bean
    fun opmlReader(): OpmlReader = XmlOpmlReader()

    /** Runs import polls on a daemon thread so the upload request never waits for feeds. */
    @Bean
    @ConditionalOnMissingBean
    fun backgroundRunner(): BackgroundRunner {
        val pool = Executors.newCachedThreadPool { task -> Thread(task, "import-poll").apply { isDaemon = true } }
        return BackgroundRunner { pool.execute(it) }
    }

    @Bean
    fun backgroundPoller(
        sources: SourceRepository,
        poller: PollService,
        runner: BackgroundRunner,
    ) = BackgroundPoller(sources, poller, runner)

    @Bean
    fun importService(
        sources: SourceRepository,
        groups: GroupRepository,
        opml: OpmlReader,
        background: BackgroundPoller,
    ) = ImportService(sources, groups, opml, background, youtubeFeedUrlTemplate, platforms)
}
