package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CatalogueTest {
    @Test
    fun `the catalogue number pads the id to four digits and keeps longer ids whole`() {
        assertEquals("STV 0007", Catalogue.number(7))
        assertEquals("STV 0132", Catalogue.number(132))
        assertEquals("STV 12345", Catalogue.number(12345))
    }
}
