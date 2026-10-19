package dev.starve.web

import dev.starve.application.HtmlLinks
import dev.starve.application.ImageProxyService
import dev.starve.application.ImageSigner
import dev.starve.domain.FeedRules
import dev.starve.domain.TrackingParams
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import java.net.URLEncoder
import java.time.Duration

/** The signed-in viewer's username, which image links are signed for; null for an anonymous request. */
private fun viewer(): String? = SecurityContextHolder.getContext().authentication?.name

/**
 * What templates put in an image's `src` and a link's `href` (`${images.src(url)}`): every image Starve shows comes
 * through its own `/images` route, signed for the viewer, so the browser never asks the image's site; links out lose
 * their tracking parameters. Signing is an HMAC, cheap enough for every row of every timeline.
 */
@Component
class ImageLinks(
    private val signer: ImageSigner,
    private val html: HtmlLinks,
) {
    /** The proxied address of the http or https image [url]; null for none, another scheme or no viewer. */
    fun src(url: String?): String? {
        val username = viewer() ?: return null
        val image = url?.takeIf { FeedRules.isHttpUrl(it) } ?: return null
        return "/images?src=" + URLEncoder.encode(image, Charsets.UTF_8) + "&sig=" + signer.sign(username, image)
    }

    /** [url] without its tracking parameters. */
    fun link(url: String?): String? = url?.let(TrackingParams::strip)

    /** Stored post HTML with its images proxied and its links cleaned; an image that cannot be proxied stays as is. */
    fun body(post: String?): String? = post?.let { body -> html.rewrite(body, { src(it) ?: it }, TrackingParams::strip) }
}

/** Puts [ImageLinks] in every page's model as `images`, for the templates. */
@ControllerAdvice
class ImageLinksModel(
    private val links: ImageLinks,
) {
    @ModelAttribute("images")
    fun images(): ImageLinks = links
}

/** The image proxy: an image Starve signed for this viewer, from its cache or fetched once from its site. */
@Controller
class ImageController(
    private val signer: ImageSigner,
    private val proxy: ImageProxyService,
) {
    /** Anything not signed for this viewer, and any image that cannot be fetched, is a 404. */
    @GetMapping("/images")
    fun image(
        @RequestParam src: String,
        @RequestParam sig: String,
    ): ResponseEntity<ByteArray> {
        val username = viewer()
        if (username == null || !signer.verifies(username, src, sig)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val image = proxy.image(src) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(image.contentType))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(CACHE_DAYS)).cachePrivate().immutable())
            // Opened on its own, an image (an SVG above all) runs nothing and loads nothing.
            .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
            .body(image.bytes)
    }

    private companion object {
        const val CACHE_DAYS = 365L
    }
}
