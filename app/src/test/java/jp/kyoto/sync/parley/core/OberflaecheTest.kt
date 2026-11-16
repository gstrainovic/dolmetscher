package jp.kyoto.sync.parley.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Die Oberfläche ist unabhängig von der Handysprache serbisch; Deutsch ist in den Einstellungen wählbar. */
class OberflaecheTest {

    @Test
    fun ohneWahlIstDieOberflaecheSerbisch() {
        assertEquals(Oberflaeche.SR, Oberflaeche.aus(null))
    }

    @Test
    fun deutschBleibtGewaehlt() {
        assertEquals(Oberflaeche.DE, Oberflaeche.aus("de"))
    }

    @Test
    fun unbekannteSpracheFaelltAufSerbischZurueck() {
        assertEquals(Oberflaeche.SR, Oberflaeche.aus("ja"))
    }

    @Test
    fun serbischZuerstDeutschAlsZweites() {
        assertEquals(listOf(Oberflaeche.SR, Oberflaeche.DE), Oberflaeche.entries.toList())
        assertEquals("sr", Oberflaeche.SR.code)
        assertEquals("de", Oberflaeche.DE.code)
    }
}
