package dev.starve.web

import dev.starve.application.ImageCache
import dev.starve.application.ImageProxyService
import dev.starve.application.ImageSigner
import dev.starve.application.ProxiedImage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException

class ImageLinksTest {
    private val signer =
        object : ImageSigner {
            override fun sign(
                username: String,
                url: String,
            ) = "sig-$username-${url.length}"

            override fun verifies(
                username: String,
                url: String,
                signature: String,
            ) = signature == sign(username, url)
        }
    private val links = ImageLinks(signer) { html, image, link -> "${image(html)}|${link(html)}" }

    private fun signIn(username: String) {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken(username, "x")
    }

    @AfterEach
    fun signOut() = SecurityContextHolder.clearContext()

    @Test
    fun `an image is proxied, signed for the viewer`() {
        signIn("rob")
        val url = "https://a.example/x.png?s=1&t=2"

        val src = links.src(url)

        assertEquals("/images?src=https%3A%2F%2Fa.example%2Fx.png%3Fs%3D1%26t%3D2&sig=${signer.sign("rob", url)}", src)
    }

    @Test
    fun `no image, another scheme or no viewer gives no source`() {
        assertNull(links.src("https://a.example/x.png"))
        signIn("rob")
        assertNull(links.src(null))
        assertNull(links.src("data:image/png;base64,AAAA"))
        assertNull(links.src("/relative.png"))
    }

    @Test
    fun `links lose their tracking parameters`() {
        assertEquals("https://a.example/p?id=7", links.link("https://a.example/p?utm_source=x&id=7"))
        assertNull(links.link(null))
    }

    @Test
    fun `a body has its images proxied and its links cleaned, and an image it cannot proxy stays`() {
        signIn("rob")
        val url = "https://a.example/x.png?utm_source=y"

        assertEquals("${links.src(url)}|https://a.example/x.png", links.body(url))
        assertEquals("data:x|data:x", links.body("data:x"))
        assertNull(links.body(null))
    }

    private class Cache(
        private val image: ProxiedImage?,
    ) : ImageCache {
        override fun find(url: String) = image

        override fun store(
            url: String,
            image: ProxiedImage,
        ) = Unit

        override fun prune(
            keep: Set<String>,
            maxBytes: Long,
        ) = 0
    }

    private fun controller(image: ProxiedImage?) = ImageController(signer, ImageProxyService({ null }, Cache(image)))

    @Test
    fun `a signed image is served with its type, a year's private caching and a sandbox`() {
        signIn("rob")
        val url = "https://a.example/x.svg"

        val response = controller(ProxiedImage(byteArrayOf(1), "image/svg+xml")).image(url, signer.sign("rob", url))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("image/svg+xml", response.headers.contentType.toString())
        assertEquals("max-age=31536000, private, immutable", response.headers.cacheControl)
        val policy = response.headers.getFirst("Content-Security-Policy")
        assertEquals("default-src 'none'; style-src 'unsafe-inline'; sandbox", policy)
        assertArrayEquals(byteArrayOf(1), response.body)
    }

    @Test
    fun `an unsigned, another viewer's or an unfetchable image is not found`() {
        val url = "https://a.example/x.png"
        val image = ProxiedImage(byteArrayOf(1), "image/png")
        val status = { action: () -> Unit -> assertThrows(ResponseStatusException::class.java) { action() }.statusCode }

        assertEquals(HttpStatus.NOT_FOUND, status { controller(image).image(url, signer.sign("rob", url)) })
        signIn("bea")
        assertEquals(HttpStatus.NOT_FOUND, status { controller(image).image(url, signer.sign("rob", url)) })
        assertEquals(HttpStatus.NOT_FOUND, status { controller(null).image(url, signer.sign("bea", url)) })
    }

    @Test
    fun `every page's model gets the image links, and none where they are not wired`() {
        val factory = DefaultListableBeanFactory()
        assertNull(ImageLinksModel(factory.getBeanProvider(ImageLinks::class.java)).images())
        factory.registerSingleton("links", links)
        assertSame(links, ImageLinksModel(factory.getBeanProvider(ImageLinks::class.java)).images())
    }
}
