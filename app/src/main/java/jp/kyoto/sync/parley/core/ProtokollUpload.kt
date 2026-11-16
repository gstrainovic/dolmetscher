package jp.kyoto.sync.parley.core

/**
 * Sammelt Protokollzeilen und schickt sie gebündelt an den eigenen Server (`POST /protokoll`), damit das
 * Protokoll ohne adb lesbar ist. Ohne Netz bleiben die Zeilen liegen und gehen beim
 * nächsten [flush] mit; mehr als [maxZeilen] behält nur die neuesten.
 */
class ProtokollUpload(
    private val maxZeilen: Int = 2_000,
    /** Schickt die Zeilen; true, wenn der Server sie angenommen hat. */
    private val senden: (List<String>) -> Boolean,
) {
    private val offen = ArrayDeque<String>()

    @Synchronized
    fun add(zeile: String) {
        offen.addLast(zeile)
        while (offen.size > maxZeilen) offen.removeFirst()
    }

    fun flush() {
        val zeilen = synchronized(this) { offen.toList() }
        if (zeilen.isEmpty()) return
        if (runCatching { senden(zeilen) }.getOrDefault(false)) {
            synchronized(this) { repeat(minOf(zeilen.size, offen.size)) { offen.removeFirst() } }
        }
    }
}
