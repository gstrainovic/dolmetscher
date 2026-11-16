package jp.kyoto.sync.parley.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hört das Mikrofon die eigene serbische Übersetzung, kommt sie als «erkannter» Satz zurück (Test:
 * «Donesi mi, molim te, 3 jabuke …»). Solche Sätze verwerfen, ohne das Mikrofon stumm zu schalten.
 */
class EchoFilterTest {

    private var now = 100_000L
    private val f = EchoFilter(windowMs = 20_000, clock = { now })

    @Test
    fun ownTranslationHeardAgainIsAnEcho() {
        f.spoken("Donesi mi molim te 3 jabuke za kuhinju i prestani sa tim ponašanjem.")
        assertTrue(f.isEcho("Donesi mi, molim te, 3 jabuke za kuhinju i prestani sa tim ponašanjem."))
        assertTrue(f.isEcho("Donosi mi molim te jabuke za kuhinju"))
    }

    @Test
    fun theSpeakerContinuingInGermanIsNotAnEcho() {
        f.spoken("Donesi mi dve šargarepe iz kuhinje.")
        assertFalse(f.isEcho("Und bring mir bitte auch den einen Häfel und Milch."))
    }

    @Test
    fun oldTranslationsNoLongerCount() {
        f.spoken("Donesi mi dve šargarepe iz kuhinje.")
        now += 30_000
        assertFalse(f.isEcho("Donesi mi dve šargarepe iz kuhinje."))
    }

    @Test
    fun shortFragmentsAreKept() {
        f.spoken("I sapun.")
        assertFalse(f.isEcho("Und Seife."))
    }
}
