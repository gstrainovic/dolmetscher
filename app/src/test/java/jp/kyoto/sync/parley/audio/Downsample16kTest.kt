package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class Downsample16kTest {

    private fun tone(freq: Double, samples: Int, rate: Int): ByteArray {
        val out = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val s = (8000 * sin(2 * PI * freq * i / rate)).toInt()
            out[2 * i] = (s and 0xFF).toByte()
            out[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun samples(b: ByteArray) =
        (0 until b.size / 2).map { ((b[2 * it + 1].toInt() shl 8) or (b[2 * it].toInt() and 0xFF)).toShort().toInt() }

    @Test
    fun twoThirdsOfTheSamplesRemain() {
        assertEquals(3200, Downsample16k.apply(ByteArray(4800)).size)
    }

    @Test
    fun aSpeechToneSurvivesUnchanged() {
        // 300 Hz bei 24 kHz heruntergerechnet muss dem 300-Hz-Ton bei 16 kHz entsprechen.
        val out = samples(Downsample16k.apply(tone(300.0, 2400, 24_000)))
        val expected = samples(tone(300.0, 1600, 16_000))
        val maxDiff = out.indices.drop(2).maxOf { abs(out[it] - expected[it]) }
        assertTrue("Abweichung $maxDiff", maxDiff < 400)
    }
}
