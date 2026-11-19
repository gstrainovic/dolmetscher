package jp.kyoto.sync.parley.core

import java.io.File

/**
 * Gespräche bleiben nur im Speicher und nur bis zum Stopp: kein Verlauf, kein Protokoll, keine Aufnahmen auf dem
 * Gerät. Nur ein Entwickler-Build (`BuildConfig.ENTWICKLER`, nie an Benutzer) schreibt Protokoll und Aufnahmen
 * für Tests.
 */
object Datenschutz {

    /** Beim Start der App: Reste früherer Versionen löschen (Verlauf, und in Benutzerversionen Protokoll und Aufnahmen). */
    fun aufraeumen(files: File, entwickler: Boolean) {
        File(files, "sessions.json").delete()
        if (entwickler) return
        File(files, "protokoll.txt").delete()
        File(files, "aufnahmen").deleteRecursively()
    }

    /** Protokoll nach `files/protokoll.txt` nur im Entwickler-Build; sonst schreibt es nichts. */
    fun protokoll(files: File, entwickler: Boolean) =
        Protokoll(if (entwickler) File(files, "protokoll.txt") else null)
}
