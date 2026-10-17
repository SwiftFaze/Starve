package dev.starve.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostHeadingsTest {
    @Test
    fun `a body's top headings move to the level under the post title`() {
        val nested = PostHeadings.underTitle("<h2>A</h2><p>x</p><h3 id=\"b\">B</h3>")
        assertEquals("<h3>A</h3><p>x</p><h4 id=\"b\">B</h4>", nested)
        assertEquals("<h3>A</h3><h4>B</h4>", PostHeadings.underTitle("<H1>A</H1><h2>B</h2>"))
    }

    @Test
    fun `the highest heading anywhere in the body sets the top level`() {
        assertEquals("<h5>A</h5><h3>B</h3><h4>C</h4>", PostHeadings.underTitle("<h4>A</h4><h2>B</h2><h3>C</h3>"))
    }

    @Test
    fun `levels keep their distance and stop at h6`() =
        assertEquals("<h3>A</h3><h6>B</h6><h6>C</h6>", PostHeadings.underTitle("<h1>A</h1><h4>B</h4><h6>C</h6>"))

    @Test
    fun `a body already below the title, or with no headings, is unchanged`() {
        assertEquals("<h3>A</h3><h5>B</h5>", PostHeadings.underTitle("<h3>A</h3><h5>B</h5>"))
        assertEquals("<header><hr><p>h2</p></header>", PostHeadings.underTitle("<header><hr><p>h2</p></header>"))
    }
}
