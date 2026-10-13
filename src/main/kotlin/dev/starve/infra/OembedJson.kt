package dev.starve.infra

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.starve.application.Oembed

/** The stored form of an item's oEmbed details (`items.oembed_json`): a JSON object of the three fields. */
object OembedJson {
    private const val AUTHOR = "author_name"
    private const val THUMBNAIL = "thumbnail_url"
    private const val DESCRIPTION = "description"
    private val mapper = ObjectMapper()

    fun encode(oembed: Oembed): String =
        mapper.writeValueAsString(
            mapOf(AUTHOR to oembed.authorName, THUMBNAIL to oembed.thumbnailUrl, DESCRIPTION to oembed.description),
        )

    /** The details stored as [json]; null when nothing is stored or the text is not a JSON object. */
    fun decode(json: String?): Oembed? {
        val node = json?.let(::readTree)
        if (node == null || !node.isObject) return null
        return Oembed(text(node, AUTHOR), text(node, THUMBNAIL), text(node, DESCRIPTION))
    }

    private fun readTree(json: String): JsonNode? =
        try {
            mapper.readTree(json)
        } catch (_: JsonProcessingException) {
            null
        }

    /** The text of [field], or null when it is missing, not text or blank. */
    private fun text(
        node: JsonNode,
        field: String,
    ): String? =
        node
            .get(field)
            ?.takeIf { it.isTextual }
            ?.asText()
            ?.takeIf { it.isNotBlank() }
}
