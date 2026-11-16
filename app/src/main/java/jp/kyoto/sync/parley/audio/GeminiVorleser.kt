package jp.kyoto.sync.parley.audio

/** Eine Live-Sitzung, die Text bekommt und ihn als Ton an ihren eigenen Zuhörer liefert ([jp.kyoto.sync.parley.net.GeminiSession]). */
interface Vorlesesitzung {
    fun connect()
    fun sendText(text: String)
    fun close()
}

/**
 * Gemini Live als Stimme für die fertige Übersetzung: erster Ton nach 0.35 bis 0.7 s,
 * wortgetreu (messungen/vorlesen.py). Der Ton kommt über den Zuhörer der Sitzung, [speak] liefert
 * deshalb nichts zurück. Nach [neuNach] Sätzen beginnt eine neue Sitzung, weil der mitwachsende Verlauf je Satz
 * als Eingabe mitkostet (docs/varianten.md).
 */
class GeminiVorleser(
    private val neuNach: Int = 20,
    private val neueSitzung: () -> Vorlesesitzung,
) : SpeechOut {
    private var sitzung: Vorlesesitzung? = null
    private var saetze = 0

    @Synchronized
    override fun connect() {
        sitzung = neueSitzung().also { it.connect() }
        saetze = 0
    }

    @Synchronized
    override fun speak(text: String): ByteArray {
        if (sitzung == null || saetze >= neuNach) {
            sitzung?.close()
            connect()
        }
        sitzung!!.sendText(text)
        saetze++
        return ByteArray(0)
    }

    @Synchronized
    override fun close() {
        sitzung?.close()
        sitzung = null
    }
}
