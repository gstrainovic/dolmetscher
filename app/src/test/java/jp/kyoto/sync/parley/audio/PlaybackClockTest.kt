package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Solange die Übersetzung hörbar ist, darf das Mikrofon sie nicht wieder aufnehmen (Test: die
 * serbische Ausgabe kam als «erkannter» Satz zurück und wurde nochmals übersetzt).
 */
class PlaybackClockTest {

    private val oneSecond = ByteArray(48_000) // 1 s PCM16 mono 24 kHz

    @Test
    fun busyWhileTheTranslationPlaysAndShortlyAfter() {
        val c = PlaybackClock(tailMs = 500)
        c.wrote(oneSecond, now = 10_000)
        assertTrue(c.busy(now = 10_500))
        assertTrue(c.busy(now = 11_400)) // noch im Nachhall
        assertFalse(c.busy(now = 11_600))
    }

    @Test
    fun chunksThatArriveFasterThanRealTimeQueueUp() {
        val c = PlaybackClock(tailMs = 0)
        repeat(3) { c.wrote(oneSecond, now = 10_000) } // drei Sekunden auf einmal geliefert
        assertTrue(c.busy(now = 12_900))
        assertFalse(c.busy(now = 13_100))
    }

    @Test
    fun idleBeforeAnythingWasPlayed() {
        assertFalse(PlaybackClock().busy(now = 5_000))
    }
}
