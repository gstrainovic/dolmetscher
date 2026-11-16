package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Ganzer Mikrofonton einer Sitzung, damit sich die Sprecherkennung mit echten Aufnahmen einstellen lässt. */
class MitschnittTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun everyFrameIsKeptAt16kHz() {
        val datei = tmp.root.resolve("mitschnitt.pcm")
        val m = Mitschnitt(datei, maxSekunden = 60)
        repeat(10) { m.add(ByteArray(4800)) } // 1 s bei 24 kHz
        m.close()
        assertEquals(32_000L, datei.length()) // 1 s bei 16 kHz
    }

    @Test
    fun stopsAtTheLimitSoThePhoneDoesNotFillUp() {
        val datei = tmp.root.resolve("mitschnitt.pcm")
        val m = Mitschnitt(datei, maxSekunden = 2)
        repeat(50) { m.add(ByteArray(4800)) } // 5 s
        m.close()
        assertEquals(64_000L, datei.length())
    }
}
