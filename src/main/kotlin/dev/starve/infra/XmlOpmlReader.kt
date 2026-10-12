package dev.starve.infra

import dev.starve.application.OpmlReader
import dev.starve.domain.FeedRules
import dev.starve.domain.ImportEntry
import dev.starve.domain.ParsedImport
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/** Reads OPML with the JDK XML parser, with DTDs and external entities switched off. */
private val ATTRIBUTES = listOf("title", "text")

class XmlOpmlReader : OpmlReader {
    override fun read(text: String): ParsedImport? {
        val root = parse(text)?.takeIf { it.tagName == "opml" } ?: return null
        val entries = mutableListOf<ImportEntry>()
        val failures = mutableListOf<String>()
        root.children("body").forEach { walk(it, null, entries, failures) }
        return ParsedImport(entries, failures)
    }

    private fun parse(text: String): Element? =
        try {
            secureFactory()
                .newDocumentBuilder()
                .apply { setErrorHandler(DefaultHandler()) }
                .parse(InputSource(StringReader(text)))
                .documentElement
        } catch (_: SAXException) {
            null
        }

    private fun secureFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            try {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (e: ParserConfigurationException) {
                throw IllegalStateException("XML parser cannot be hardened", e)
            }
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

    /** Collects the feeds under [parent]; a feed-less outline is a folder that names its feeds' group. */
    private fun walk(
        parent: Element,
        folder: String?,
        entries: MutableList<ImportEntry>,
        failures: MutableList<String>,
    ) {
        parent.children("outline").forEach { outline ->
            val label = outline.label()
            val url = outline.getAttribute("xmlUrl").trim()
            when {
                url.isEmpty() -> walk(outline, label ?: folder, entries, failures)
                FeedRules.isHttpUrl(url) -> entries += ImportEntry(url, label ?: url, folder)
                else -> failures += "outline '${label ?: url}': not an http(s) URL"
            }
        }
    }

    private fun Element.label(): String? = ATTRIBUTES.map { getAttribute(it).trim() }.firstOrNull { it.isNotEmpty() }

    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE && it.nodeName == name }
            .map { it as Element }
}
