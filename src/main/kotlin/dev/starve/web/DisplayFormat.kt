package dev.starve.web

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** How a published time reads on the page. */
internal val DISPLAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)
