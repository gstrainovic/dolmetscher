package jp.kyoto.sync.parley.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Protokoll für die Auswertung von Tests: was erkannt, übersetzt und wann, je Variante. */
class ProtokollTest {

    private val datei: File = Files.createTempFile("protokoll", ".txt").toFile()
    private var jetzt = 10_000L
    private val p = Protokoll(datei, maxBytes = 2_000, uhr = { jetzt })

    @Test
    fun timesCountFromTheLastSpokenWord() {
        p.start("SUISSE|CLAUDE_SERVER|GEMINI")
        // Die Sprecherkennung meldet das Satzende 1.5 s nach dem letzten Wort.
        p.satzende(nachlaufMs = 1_500)
        jetzt += 500
        p.erkannt("Mir ist kalt.")
        jetzt += 1_000
        p.ton()
        jetzt += 2_000
        p.uebersetzt("Hladno mi je.")
        val zeilen = datei.readLines()
        assertTrue(zeilen[0].contains("START SUISSE|CLAUDE_SERVER|GEMINI"))
        assertTrue(zeilen.any { it.contains("ERKANNT +2.0s Mir ist kalt.") })
        assertTrue(zeilen.any { it.contains("TON +3.0s") })
        assertTrue(zeilen.any { it.contains("ÜBERSETZT +5.0s Hladno mi je.") })
    }

    @Test
    fun onlyTheFirstSoundAfterASentenceIsRecorded() {
        p.satzende(nachlaufMs = 0)
        p.ton(); p.ton(); p.ton()
        assertEquals(1, datei.readLines().count { it.contains("TON") })
    }

    @Test
    fun theFileStaysSmallAndKeepsTheNewestLines() {
        repeat(200) { p.erkannt("Satz Nummer $it mit etwas Text") }
        assertTrue(datei.length() <= 2_000)
        assertTrue(datei.readText().contains("Satz Nummer 199"))
    }

    @Test
    fun errorsAndConnectionEventsAreRecordedToo() {
        p.fehler("Suisse Speech: Schlüssel ungültig")
        p.info("Gemini bereit")
        assertEquals(1, datei.readLines().count { it.contains("FEHLER Suisse Speech: Schlüssel ungültig") })
        assertEquals(1, datei.readLines().count { it.contains("INFO Gemini bereit") })
    }
}
