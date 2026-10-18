package dev.starve.web

import dev.starve.application.BackgroundPoller
import dev.starve.application.FeedFetcher
import dev.starve.application.FeedIngestor
import dev.starve.application.FetchResult
import dev.starve.application.ImportService
import dev.starve.application.ImportSummary
import dev.starve.application.MemoryGroups
import dev.starve.application.MemoryItems
import dev.starve.application.MemorySources
import dev.starve.application.OWNER
import dev.starve.application.PollService
import dev.starve.application.quietFavicons
import dev.starve.domain.ParsedImport
import dev.starve.domain.Validators
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.ui.ConcurrentModel
import java.time.Clock

class ImportControllerTest {
    private val sources = MemorySources()
    private val fetcher =
        object : FeedFetcher {
            override fun fetch(
                url: String,
                validators: Validators,
            ): FetchResult = FetchResult.NotModified
        }
    private val service =
        ImportService(
            sources,
            MemoryGroups(),
            { _ -> ParsedImport(emptyList(), emptyList()) },
            BackgroundPoller(
                sources,
                PollService(
                    sources,
                    fetcher,
                    FeedIngestor(sources, MemoryItems(), Clock.systemUTC(), quietFavicons(sources)),
                ),
            ) { it.run() },
            "https://yt.test/{id}",
            emptyList(),
        )
    private val controller = ImportController(service)

    private fun upload(text: String): ConcurrentModel {
        val model = ConcurrentModel()
        val file = MockMultipartFile("file", "f.txt", null, text.toByteArray())
        assertEquals("import", controller.upload(OWNER, file, model))
        return model
    }

    @Test
    fun `the import page renders without a result`() = assertEquals("import", controller.page().viewName)

    @Test
    fun `an unrecognised file is reported as a message`() {
        val model = upload("just text")

        assertEquals(UNRECOGNISED_FILE, model.getAttribute("message"))
        assertEquals(null, model.getAttribute("summary"))
    }

    @Test
    fun `a recognised file puts the summary in the model`() {
        val model = upload("<opml/>")

        assertEquals(ImportSummary(0, 0, emptyList()), model.getAttribute("summary"))
        assertEquals(null, model.getAttribute("message"))
    }

    @Test
    fun `an over-size upload shows the too-large message on the import page`() {
        val view = UploadLimitAdvice().tooLarge()

        assertEquals("import", view.viewName)
        assertEquals(FILE_TOO_LARGE, view.model["message"])
    }
}
