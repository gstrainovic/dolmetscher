package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Die Gratis-Stimme des Handys liefert WAV in eigener Abtastrate; die Wiedergabe braucht 24 kHz. */
class WavPcmTest {

    private fun wav(rate: Int, samples: Int): ByteArray {
        val b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { b.putShort(1000) }
        return b.array()
    }

    @Test
    fun oneSecondStaysOneSecondAt24kHz() {
        assertEquals(48_000, WavPcm.to24k(wav(22_050, 22_050)).size)
        assertEquals(48_000, WavPcm.to24k(wav(24_000, 24_000)).size)
        assertEquals(48_000, WavPcm.to24k(wav(16_000, 16_000)).size)
    }

    @Test
    fun brokenFilesGiveSilenceInsteadOfCrashing() {
        assertEquals(0, WavPcm.to24k(ByteArray(10)).size)
    }
}
