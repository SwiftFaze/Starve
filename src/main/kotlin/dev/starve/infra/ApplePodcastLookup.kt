package dev.starve.infra

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.starve.domain.FeedRules

/** Reads the iTunes lookup API's answer for one podcast: `{"results": [{"feedUrl": "..."}]}`. */
object ApplePodcastLookup {
    private val mapper = ObjectMapper()

    /** The first result's http(s) `feedUrl`; null when there is none or [json] is not such an answer. */
    fun feedUrl(json: String): String? =
        readTree(json)
            ?.path("results")
            ?.firstOrNull { it.path("feedUrl").isTextual }
            ?.path("feedUrl")
            ?.asText()
            ?.trim()
            ?.takeIf { FeedRules.isHttpUrl(it) }

    private fun readTree(json: String): JsonNode? =
        try {
            mapper.readTree(json)
        } catch (_: JsonProcessingException) {
            null
        }
}
