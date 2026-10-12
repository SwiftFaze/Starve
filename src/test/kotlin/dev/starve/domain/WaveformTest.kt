package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaveformTest {
    @Test
    fun `the same item always gets the same waveform`() {
        assertEquals(Waveform.bars(112), Waveform.bars(112))
    }

    @Test
    fun `an item keeps its waveform across releases`() {
        val bars = Waveform.bars(112)

        assertEquals(GOLDEN_112, bars.take(GOLDEN_112.size))
    }

    @Test
    fun `different items get different waveforms`() {
        assertNotEquals(Waveform.bars(112), Waveform.bars(113))
    }

    @Test
    fun `a waveform has a fixed number of bars`() {
        assertEquals(Waveform.BARS, Waveform.bars(1).size)
    }

    @Test
    fun `every bar is a percentage height that stays visible`() {
        val bars = (1L..50L).flatMap { Waveform.bars(it) }

        assertTrue(bars.all { it in Waveform.MIN_HEIGHT..100 }, bars.toString())
    }

    @Test
    fun `a waveform has quiet and loud passages`() {
        val bars = Waveform.bars(42)

        assertTrue(bars.max() - bars.min() >= 40, bars.toString())
    }

    private companion object {
        // The first bars of item 112, recorded when the waveform shipped (issue #22).
        val GOLDEN_112 = listOf(91, 45, 57, 78, 59, 73, 79, 84, 72, 69, 59, 80)
    }
}
