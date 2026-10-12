package dev.starve.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The deck's stand-in waveform for players that report none (YouTube, or SoundCloud before its
 * own arrives): bar heights in percent, shaped by slow swells and seeded by the item id so an
 * item always looks the same.
 */
object Waveform {
    const val BARS = 120
    const val MIN_HEIGHT = 8

    fun bars(itemId: Long): List<Int> {
        val random = Random(itemId)
        val phase = random.nextDouble(2 * PI)
        val swell = 3 + random.nextDouble(4.0)
        return List(BARS) { bar ->
            val at = bar.toDouble() / BARS
            val envelope = 0.3 + 0.7 * abs(sin(at * swell + phase))
            val height = envelope * (0.45 + 0.55 * random.nextDouble()) * 100
            height.roundToInt().coerceIn(MIN_HEIGHT, 100)
        }
    }
}
