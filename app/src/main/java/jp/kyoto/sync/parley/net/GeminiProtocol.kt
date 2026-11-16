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
            // Spricht das Gegenüber weiter, läuft die Übersetzung zu Ende.
            .put("realtimeInputConfig", JSONObject().put("activityHandling", "NO_INTERRUPTION"))
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
