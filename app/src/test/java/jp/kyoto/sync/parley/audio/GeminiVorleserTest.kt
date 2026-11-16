package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gemini liest Claudes Übersetzung vor. */
class GeminiVorleserTest {

    private class FalscheSitzung : Vorlesesitzung {
        val texte = mutableListOf<String>()
        var verbunden = false
        var geschlossen = false
        override fun connect() { verbunden = true }
        override fun sendText(text: String) { texte += text }
        override fun close() { geschlossen = true }
    }

    private val sitzungen = mutableListOf<FalscheSitzung>()
    private val vorleser = GeminiVorleser(neuNach = 3) { FalscheSitzung().also { sitzungen += it } }

    @Test
    fun eachTranslatedSentenceGoesToGeminiToBeRead() {
        vorleser.connect()
        vorleser.speak("Iz kuhinje.")
        vorleser.speak("I zeca, molim te.")
        assertEquals(listOf("Iz kuhinje.", "I zeca, molim te."), sitzungen.single().texte)
        assertTrue(sitzungen.single().verbunden)
    }

    /** Der Verlauf wächst je Satz und kostet als Eingabe mit; ein Neustart hält die Kosten tief (docs/varianten.md). */
    @Test
    fun afterSomeSentencesAFreshSessionStarts() {
        vorleser.connect()
        repeat(4) { vorleser.speak("Satz $it") }
        assertEquals(2, sitzungen.size)
        assertTrue(sitzungen[0].geschlossen)
        assertTrue(sitzungen[1].verbunden)
        assertEquals(listOf("Satz 3"), sitzungen[1].texte)
    }

    @Test
    fun closingEndsTheCurrentSession() {
        vorleser.connect()
        vorleser.close()
        assertTrue(sitzungen.single().geschlossen)
    }
}
