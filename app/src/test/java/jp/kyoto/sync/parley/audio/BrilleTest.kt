package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Start verbindet die Brille selbst wieder, die Stopp getrennt hat; nie ein fremdes Gerät wie Auto oder Lautsprecher. */
class BrilleTest {

    private val brille = Gekoppelt("AA:BB", audio = true)
    private val auto = Gekoppelt("CC:DD", audio = true)
    private val uhr = Gekoppelt("EE:FF", audio = false)

    @Test
    fun verbindetDieZuletztGetrennteBrille() {
        assertEquals("AA:BB", Brille.wahl(listOf(auto, brille, uhr), gemerkt = "AA:BB"))
    }

    @Test
    fun ohneGemerkteBrilleDasEinzigeGekoppelteAudiogeraet() {
        assertEquals("AA:BB", Brille.wahl(listOf(brille, uhr), gemerkt = null))
    }

    @Test
    fun beiMehrerenAudiogeraetenOhneGemerkteBrilleKeines() {
        assertNull(Brille.wahl(listOf(brille, auto), gemerkt = null))
    }

    /** Fest eingerichtet: ist die Brille einmal mit der App verbunden, merkt die App sie sich ohne Stopp. */
    @Test
    fun merktSichDieVerbundeneBrille() {
        assertEquals("AA:BB", Brille.zuMerken(listOf(Gekoppelt("", audio = false), Gekoppelt("AA:BB", audio = true)), gemerkt = null))
        assertNull(Brille.zuMerken(listOf(Gekoppelt("AA:BB", audio = true)), gemerkt = "AA:BB"))
        assertNull(Brille.zuMerken(listOf(Gekoppelt("", audio = true)), gemerkt = null))
    }

    @Test
    fun istDieGemerkteBrilleEntkoppeltGiltDieRegelOhneGemerkte() {
        assertEquals("CC:DD", Brille.wahl(listOf(auto, uhr), gemerkt = "AA:BB"))
    }
}
