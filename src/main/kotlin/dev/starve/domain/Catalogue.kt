package dev.starve.domain

/** Every item's catalogue number: "STV" plus its id, zero-padded to four digits. */
object Catalogue {
    fun number(itemId: Long): String = "STV %04d".format(itemId)
}
