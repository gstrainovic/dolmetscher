package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Silero bekommt Fenster von 512 Proben bei 16 kHz; ein Rahmen der App sind 100 ms bei 24 kHz. */
class SileroSpracheTest {

    private fun rahmen() = ByteArray(4800) // 100 ms, 2400 Proben bei 24 kHz

    @Test
    fun everySampleReachesTheModelInWindowsOf512() {
        val fenster = mutableListOf<Int>()
        val s = SileroSprache { fenster += it.size; 0f }
        repeat(10) { s(rahmen()) } // 1 s = 16'000 Proben bei 16 kHz
        assertTrue(fenster.all { it == 512 })
        assertEquals(16_000 / 512, fenster.size)
    }

    /** Fürs Protokoll: höchste Sprachwahrscheinlichkeit seit der letzten Abfrage, damit sich die Schwelle einstellen lässt. */
    @Test
    fun reportsTheHighestProbabilitySinceLastAsked() {
        val werte = ArrayDeque(listOf(0.2f, 0.35f, 0.1f) + List(100) { 0.05f })
        val s = SileroSprache { werte.removeFirst() }
        s(rahmen())
        assertEquals(0.35f, s.hoechsteSeitAbfrage(), 0.001f)
        s(rahmen())
        assertEquals(0.05f, s.hoechsteSeitAbfrage(), 0.001f)
    }

    @Test
    fun speechWhenOneWindowOfTheFrameIsAboveTheThreshold() {
        var n = 0
        val s = SileroSprache { if (n++ == 1) 0.8f else 0.1f }
        assertEquals(0.8f, s(rahmen()), 0.001f)
        assertEquals(0.1f, s(rahmen()), 0.001f)
    }
}
