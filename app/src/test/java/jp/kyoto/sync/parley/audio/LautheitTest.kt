package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Flüstern ist zu leise für Sprecherkennung und Erkennung; verstärkt hörte Gemini Flüstern («s Chüngeli»). */
class LautheitTest {

    private fun pcm(vararg s: Int): ByteArray {
        val b = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        s.forEach { b.putShort(it.toShort()) }
        return b.array()
    }

    private fun samples(p: ByteArray): List<Int> {
        val b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
        return List(p.size / 2) { b.getShort(it * 2).toInt() }
    }

    @Test
    fun aWhisperedSentenceIsBroughtToFullLoudness() {
        val leise = pcm(100, -200, 150, -50)
        val laut = samples(Lautheit.normalisiert(leise))
        assertEquals(Lautheit.ZIEL_SPITZE, laut.maxOf { kotlin.math.abs(it) })
        assertEquals(-2 * laut[0], laut[1]) // Form bleibt gleich
    }

    @Test
    fun silenceStaysSilence() {
        assertEquals(listOf(0, 0, 0), samples(Lautheit.normalisiert(pcm(0, 0, 0))))
    }

    @Test
    fun aFixedGainClipsInsteadOfWrappingAround() {
        val s = samples(Lautheit.verstaerkt(pcm(100, 20_000, -20_000), faktor = 4f))
        assertEquals(400, s[0])
        assertTrue(s[1] == Short.MAX_VALUE.toInt() && s[2] == Short.MIN_VALUE.toInt())
    }
}
