package jp.kyoto.sync.parley.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Laufprotokoll für die Auswertung von Tests (`files/protokoll.txt`, per `adb exec-out run-as … cat`):
 * gewählte Variante, Satzende, erkannter Text, erster hörbarer Ton, Übersetzung, Verbindungen, Fehler.
 * Die Zeit nach «+» zählt ab dem letzten gesprochenen Wort (Satzende der Sprecherkennung minus Nachlauf).
 * Höchstens [maxBytes]; darüber bleibt die neuere Hälfte. Ohne [datei] schreibt es nichts (Benutzerversion,
 * [Datenschutz.protokoll]).
 */
class Protokoll(
    private val datei: File?,
    private val maxBytes: Int = 1_000_000,
    private val uhr: () -> Long = System::currentTimeMillis,
) {
    private var letztesWort = 0L
    private var tonSeitSatz = true
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    fun start(variante: String) = schreib("START $variante")

    fun satzende(nachlaufMs: Long) {
        letztesWort = uhr() - nachlaufMs
        tonSeitSatz = false
        schreib("SATZENDE")
    }

    fun erkannt(text: String) = schreib("ERKANNT ${seit()} ${text.trim()}")

    /** Erster hörbarer Ton der Übersetzung nach einem Satz. */
    @Synchronized
    fun ton() {
        if (tonSeitSatz) return
        tonSeitSatz = true
        schreib("TON ${seit()}")
    }

    fun uebersetzt(text: String) = schreib("ÜBERSETZT ${seit()} ${text.trim()}")
    fun info(text: String) = schreib("INFO $text")
    fun fehler(text: String) = schreib("FEHLER $text")

    private fun seit(): String =
        if (letztesWort == 0L) "+?" else String.format(Locale.ROOT, "+%.1fs", (uhr() - letztesWort) / 1000.0)

    @Synchronized
    private fun schreib(zeile: String) {
        val datei = datei ?: return
        val fertig = "${format.format(Date(uhr()))} $zeile"
        runCatching {
            datei.appendText("$fertig\n")
            if (datei.length() > maxBytes) {
                val t = datei.readText()
                val rest = t.substring(t.length - maxBytes / 2)
                datei.writeText(rest.substringAfter('\n'))
            }
        }
    }
}
