package jp.kyoto.sync.parley.net

import org.junit.Assert.assertEquals
import org.junit.Test

/** Die Stimme spricht Satz für Satz, damit der erste Satz nicht auf den ganzen Text warten muss. */
class SaetzeTest {

    @Test
    fun splitsAfterSentenceMarks() {
        assertEquals(
            listOf("Dobar dan, gospođo Petrović.", "Da li ste popili tablete?", "Hajde!"),
            Saetze.split("Dobar dan, gospođo Petrović. Da li ste popili tablete? Hajde!"),
        )
    }

    @Test
    fun keepsTextWithoutMarksWhole() {
        assertEquals(listOf("Donesi mi dve šargarepe"), Saetze.split("  Donesi mi dve šargarepe "))
    }

    @Test
    fun doesNotSplitNumbersOrShortAbbreviations() {
        assertEquals(listOf("U 3.5 sati dolazi dr. Marković."), Saetze.split("U 3.5 sati dolazi dr. Marković."))
    }
}
