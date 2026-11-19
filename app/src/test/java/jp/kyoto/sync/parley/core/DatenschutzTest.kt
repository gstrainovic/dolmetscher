package jp.kyoto.sync.parley.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Gespräche bleiben nicht über die laufende Sitzung hinaus auf dem Gerät. */
class DatenschutzTest {

    private val files: File = Files.createTempDirectory("files").toFile()

    private fun alteDaten() {
        File(files, "sessions.json").writeText("""[{"id":"1","partnerText":"Hladno mi je."}]""")
        File(files, "protokoll.txt").writeText("ERKANNT Mir ist kalt.\n")
        File(files, "aufnahmen").mkdirs()
        File(files, "aufnahmen/2026-11-18_10-00-00.wav").writeBytes(ByteArray(10))
        File(files, "aufnahmen/mitschnitt.pcm").writeBytes(ByteArray(10))
        File(files, "silero.onnx").writeBytes(ByteArray(3)) // kein Gespräch, bleibt
    }

    @Test
    fun benutzerversionLoeschtVerlaufProtokollUndAufnahmenVonFrueher() {
        alteDaten()
        Datenschutz.aufraeumen(files, entwickler = false)
        assertEquals(listOf("silero.onnx"), files.list()!!.toList())
    }

    @Test
    fun entwicklerversionBehaeltProtokollUndAufnahmenAberKeinenVerlauf() {
        alteDaten()
        Datenschutz.aufraeumen(files, entwickler = true)
        assertFalse(File(files, "sessions.json").exists())
        assertTrue(File(files, "protokoll.txt").exists())
        assertTrue(File(files, "aufnahmen/mitschnitt.pcm").exists())
    }

    @Test
    fun benutzerversionSchreibtKeinProtokoll() {
        val p = Datenschutz.protokoll(files, entwickler = false)
        p.start("VOXTRAL|CLAUDE_SERVER|GEMINI_VORLESER")
        p.erkannt("Mir ist kalt.")
        p.uebersetzt("Hladno mi je.")
        p.fehler("Kopfhörer getrennt")
        assertEquals(emptyList<String>(), files.walkTopDown().filter { it.isFile }.map { it.name }.toList())
    }

    @Test
    fun entwicklerversionSchreibtDasProtokoll() {
        Datenschutz.protokoll(files, entwickler = true).erkannt("Mir ist kalt.")
        assertTrue(File(files, "protokoll.txt").readText().contains("ERKANNT"))
    }
}
