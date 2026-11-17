package jp.kyoto.sync.parley.net

import jp.kyoto.sync.parley.core.Lang
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Nachrichten der Gemini Live API (WebSocket `BidiGenerateContent`) für den Dolmetscher, mit
 * derselben Anweisung wie bei OpenAI. Ohne Android-Abhängigkeit, damit es in Unit-Tests läuft.
 */
object GeminiProtocol {
    const val MODEL = "gemini-3.8-live"

    /** Interne Marken, die Gemini manchmal in den Untertitel schreibt, etwa «<no speech>{pause}». */
    private val MARKER = Regex("""<[a-z _]+>|\{[a-z _]+\}""")

    /** Unvollständiger Anfang einer Marke am Ende eines Stücks, etwa «<no » oder «{pa». */
    private val ANFANG = Regex("""[<{][a-z _]*$""")

    /**
     * Entfernt Marken auch dann, wenn Gemini sie auf mehrere Nachrichten verteilt: Ein angefangenes «<…» oder «{…»
     * am Ende wird zurückgehalten, bis es sich schliesst oder eine neue Zeile kommt.
     */
    class Untertitelfilter {
        private var rest = ""

        fun feed(stueck: String): String {
            val text = (rest + stueck).replace(MARKER, "")
            val offen = if (text.endsWith("\n")) null else ANFANG.find(text)
            rest = offen?.value.orEmpty()
            return if (offen == null) text else text.substring(0, offen.range.first)
        }
    }

    fun setup(target: Lang, instructions: String): String {
        val setup = JSONObject()
            .put("model", "models/$MODEL")
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO")))
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instructions))),
            )
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            // Kein NO_INTERRUPTION: Damit blieb Gemini nach einer stillen Antwort («<no speech>») hängen
            // (messungen/gemini-live-haenger.md). Neue Sprache unterbricht nur die Erzeugung; der Ton ist dann
            // meist schon ganz da und läuft in der App zu Ende.
            // Ohne Verdichtung endet eine reine Tonsitzung nach rund 15 Minuten.
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
        return JSONObject().put("setup", setup).toString()
    }

    fun appendAudio(pcm16: ByteArray): String =
        JSONObject()
            .put(
                "realtimeInput",
                JSONObject().put(
                    "audio",
                    JSONObject()
                        .put("mimeType", "audio/pcm;rate=24000")
                        .put("data", Base64.getEncoder().encodeToString(pcm16)),
                ),
            )
            .toString()

    /** Bereits erkannter Text statt Ton (Kette mit Suisse Speech): Gemini übersetzt und spricht ihn. */
    fun text(text: String): String =
        JSONObject().put("realtimeInput", JSONObject().put("text", text)).toString()

    /** Meldet eine Pause im Tonstrom, damit Gemini zwischengespeicherten Ton sofort verarbeitet. */
    fun audioStreamEnd(): String =
        JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)).toString()

    fun isSetupComplete(text: String): Boolean = has(text, "setupComplete")

    /** Google kündigt an, die Verbindung bald zu trennen (rund alle 10 Minuten). */
    fun isGoAway(text: String): Boolean = has(text, "goAway")

    private fun has(text: String, key: String) = runCatching { JSONObject(text).has(key) }.getOrDefault(false)

    fun parse(text: String): List<InterpreterEvent> {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val content = json.optJSONObject("serverContent") ?: return emptyList()
        val events = mutableListOf<InterpreterEvent>()
        content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
            for (i in 0 until parts.length()) {
                val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data").orEmpty()
                if (data.isNotEmpty()) events += InterpreterEvent.Audio(Base64.getDecoder().decode(data))
            }
        }
        content.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }
            ?.let { events += InterpreterEvent.InputTranscript(it) }
        content.optJSONObject("outputTranscription")?.optString("text")
            ?.replace(MARKER, "")?.takeIf { it.isNotBlank() }
            ?.let { events += InterpreterEvent.OutputTranscript(it) }
        if (content.optBoolean("turnComplete")) {
            events += InterpreterEvent.OutputTranscript("\n")
            events += InterpreterEvent.InputTranscript("\n")
        }
        return events
    }
}
