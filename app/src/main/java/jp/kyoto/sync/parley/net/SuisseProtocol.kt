package jp.kyoto.sync.parley.net

import jp.kyoto.sync.parley.audio.Downsample16k
import org.json.JSONObject
import java.util.Base64

sealed interface SuisseEvent {
    data class Final(val text: String) : SuisseEvent
    data class Error(val message: String) : SuisseEvent
}

/**
 * Live-Spracherkennung von Suisse Speech (`wss://api.suisse-speech.ch/v1/stt/stream`):
 * Schweizerdeutsch rein, hochdeutscher Text raus. Doku: suisse-speech.ch/docs/speech-to-text.
 */
object SuisseProtocol {
    const val URL = "wss://api.suisse-speech.ch/v1/stt/stream"

    /**
     * Mundartwörter aus dem Alltag im Altersheim, die die Erkennung sonst verfehlt. Gemessen mit
     * Suisse-TTS (Zürich): ohne Eintrag «Küngel», «Gipfel», «Anker», «Komfi», «Pfeifen», mit Eintrag
     * richtig. Nur Wörter aufnehmen, die ohne Eintrag falsch kommen: Ein Eintrag schreibt das Wort in
     * Mundart, und «Buuchweh» machte aus richtig erkanntem «Bauchweh» ein «Buchweh». Rüebli kam in
     * Tests am Handy nie richtig («C2-Karate», «Konfi»), mit Suisse-TTS ohne Eintrag 3 von 6, mit Eintrag 6 von 6
     * (Herdöpfel und Chuchi ebenso besser). Höchstens 200 Wörter, je 100 Zeichen.
     */
    val VOCABULARY = listOf(
        "Chüngel", "Znüni", "Zvieri", "Zmittag", "Znacht", "Zmorge", "Gipfeli", "Anke", "Konfi",
        "Schoggi", "Nastuech", "Finke", "Grosi", "Grossätti", "Spitex", "Rollator", "Tschope", "Pfnüsel",
        "Häfeli", "Stägeli", "Lavabo", "Poschte", "Rüebli", "Herdöpfel", "Chuchi",
    )

    /** `finalize_on_pause`: ein Satz kommt fertig, sobald die sprechende Person nach ihm pausiert. */
    fun config(): String =
        JSONObject()
            .put("type", "config")
            .put("lang", "de-CH")
            .put("finalize_on_pause", true)
            .put("vocabulary", org.json.JSONArray(VOCABULARY))
            .toString()

    fun appendAudio(pcm24k: ByteArray): String =
        JSONObject()
            .put("type", "audio.append")
            .put("data", Base64.getEncoder().encodeToString(Downsample16k.apply(pcm24k)))
            .toString()

    /** Satzende laut Sprecherkennung des Handys: sofort festschreiben. */
    fun finalize(): String = JSONObject().put("type", "finalize").toString()

    fun parse(text: String): SuisseEvent? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return when (json.optString("type")) {
            "transcript.final" -> json.optString("text").trim().takeIf { it.isNotEmpty() }?.let { SuisseEvent.Final(it) }
            "error" -> {
                val e = json.optJSONObject("error") ?: json
                SuisseEvent.Error("Suisse Speech: ${e.optString("message")} (${e.optString("code")})")
            }
            else -> null
        }
    }
}
