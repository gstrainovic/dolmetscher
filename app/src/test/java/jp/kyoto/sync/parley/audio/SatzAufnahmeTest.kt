package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Jeder Satz, der an die Erkennung geht, liegt danach als WAV vor, damit man ihn mit allen Erkennern prüfen kann. */
class SatzAufnahmeTest {

    @get:Rule val tmp = TemporaryFolder()

    private var zeit = 1_763_244_000_000L
    private fun aufnahme(max: Int = 100) = SatzAufnahme(tmp.root, max) { zeit }

    private fun rahmen(wert: Int) = ByteArray(4800) { if (it % 2 == 0) wert.toByte() else 0 } // 100 ms bei 24 kHz

    @Test
    fun aSentenceIsSavedAsWavWithAllItsSound() {
        val a = aufnahme()
        repeat(10) { a.add(rahmen(10)) }
        val name = a.satzEnde()!!
        val wav = tmp.root.resolve(name).readBytes()
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(10 * 4800, jp.kyoto.sync.parley.audio.WavPcm.to24k(wav).size)
    }

    @Test
    fun withoutSoundNothingIsSaved() {
        assertNull(aufnahme().satzEnde())
        assertEquals(0, tmp.root.listFiles()!!.size)
    }

    @Test
    fun theNextSentenceStartsEmpty() {
        val a = aufnahme()
        a.add(rahmen(10)); a.satzEnde()
        zeit += 5_000
        a.add(rahmen(10))
        val wav = tmp.root.resolve(a.satzEnde()!!).readBytes()
        assertEquals(4800, WavPcm.to24k(wav).size)
    }

    @Test
    fun onlyTheNewestRecordingsAreKept() {
        val a = aufnahme(max = 2)
        val namen = (1..3).map { zeit += 5_000; a.add(rahmen(10)); a.satzEnde()!! }
        assertEquals(namen.drop(1).toSet(), tmp.root.list()!!.toSet())
    }
}
