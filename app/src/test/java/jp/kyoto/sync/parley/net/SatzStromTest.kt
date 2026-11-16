package jp.kyoto.sync.parley.net

import org.junit.Assert.assertEquals
import org.junit.Test

/** Claude liefert die Übersetzung stückweise; jeder fertige Satz geht sofort an die Stimme. */
class SatzStromTest {

    @Test
    fun aSentenceIsReleasedAsSoonAsTheNextOneStarts() {
        val s = SatzStrom()
        assertEquals(emptyList<String>(), s.feed("Hladno mi "))
        assertEquals(listOf("Hladno mi je."), s.feed("je. Mo"))
        assertEquals(emptyList<String>(), s.feed("gu li dobiti jaknu?"))
        assertEquals(listOf("Mogu li dobiti jaknu?"), s.finish())
    }

    @Test
    fun severalSentencesInOneChunk() {
        val s = SatzStrom()
        assertEquals(listOf("Dobar dan.", "Kako ste?"), s.feed("Dobar dan. Kako ste? Ja"))
        s.feed(" sam dobro.")
        assertEquals(listOf("Ja sam dobro."), s.finish())
    }

    @Test
    fun nothingLeftAfterFinish() {
        val s = SatzStrom()
        s.feed("Jedan.")
        assertEquals(listOf("Jedan."), s.finish())
        assertEquals(emptyList<String>(), s.finish())
    }
}
