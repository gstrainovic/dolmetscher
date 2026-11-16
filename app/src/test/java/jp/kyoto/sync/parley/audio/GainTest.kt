package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GainTest {

    private fun pcm(vararg samples: Int): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            out[2 * i] = (s and 0xFF).toByte()
            out[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun samples(b: ByteArray): List<Int> =
        (0 until b.size / 2).map { i -> ((b[2 * i + 1].toInt() shl 8) or (b[2 * i].toInt() and 0xFF)).toShort().toInt() }

    @Test
    fun doublingMakesQuietSpeechLouder() {
        assertEquals(listOf(2000, -2000), samples(Gain.apply(pcm(1000, -1000), 2.0f)))
    }

    @Test
    fun loudPeaksAreClippedInsteadOfWrappingAround() {
        assertEquals(listOf(32767, -32768), samples(Gain.apply(pcm(20000, -20000), 3.0f)))
    }

    @Test
    fun gainOfOneLeavesAudioUnchanged() {
        val input = pcm(123, -456, 32767)
        assertArrayEquals(input, Gain.apply(input, 1.0f))
    }
}
