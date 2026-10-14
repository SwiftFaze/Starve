package dev.starve.infra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BodyImagesTest {
    private val base = "https://blog.example/posts/one"

    @Test
    fun `the first image of the body is used, resolved against the base`() {
        assertEquals(
            "https://blog.example/posts/pic.png",
            BodyImages.firstUrl("<p>Intro</p><img src=\"pic.png\"><img src=\"second.png\">", base),
        )
    }

    @Test
    fun `a zero or one pixel image is skipped in favour of the next`() {
        val html = """<img src="https://t.example/p.gif" width="1" height="1"><img src="https://img.example/r.png">"""
        assertEquals("https://img.example/r.png", BodyImages.firstUrl(html, base))
        assertEquals(
            "https://img.example/s.png",
            BodyImages.firstUrl(
                """<img src="https://t.example/z.gif" width="0"><img src="https://img.example/s.png">""",
                base,
            ),
        )
    }

    @Test
    fun `an image with a width or height other than 0 or 1 is not a pixel`() {
        assertEquals(
            "https://img.example/big.png",
            BodyImages.firstUrl("""<img src="https://img.example/big.png" width="640" height="480">""", base),
        )
    }

    @Test
    fun `an image with no usable http source is passed over`() {
        val html = """<img src="data:image/png;base64,AAAA"><img src="javascript:alert(1)"><img src="ok.png">"""
        assertEquals("https://blog.example/posts/ok.png", BodyImages.firstUrl(html, base))
    }

    @Test
    fun `a body with no image, or only pixels, has no image`() {
        assertNull(BodyImages.firstUrl("<p>No images</p>", base))
        assertNull(BodyImages.firstUrl("""<img src="https://t.example/p.gif" width="1" height="1">""", base))
    }

    @Test
    fun `a pixel is recognised by its height alone, and padding around the size is ignored`() {
        val html =
            """<img src="https://t.example/a.gif" height="1">""" +
                """<img src="https://t.example/b.gif" width=" 0 ">""" +
                """<img src="https://img.example/ok.png">"""
        assertEquals("https://img.example/ok.png", BodyImages.firstUrl(html, base))
    }
}
