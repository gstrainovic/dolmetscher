package jp.kyoto.sync.parley.net

import jp.kyoto.sync.parley.core.Lang
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Voxtral Mini Transcribe (Mistral): ein Satz als WAV, Antwort ist hochdeutscher Text. */
object VoxtralProtocol {
    const val URL = "https://api.mistral.ai/v1/audio/transcriptions"
    const val MODEL = "voxtral-mini-2602"

    /** PCM16 mono 16 kHz mit WAV-Kopf. */
    fun wav16k(pcm: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000)
            .putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(pcm.size).put(pcm)
        return b.array()
    }

    fun parse(json: String): String? =
        runCatching { JSONObject(json).optString("text").trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
}

/** Gemini Flash als reiner Textübersetzer, mit den letzten Sätzen als Zusammenhang. */
object GeminiFlashProtocol {
    const val URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"

    fun request(target: Lang, recent: List<String>, text: String): String {
        val prompt = buildString {
            append(InterpreterProtocol.textInstructions(target))
            if (recent.isNotEmpty()) {
                append("\n\nEarlier sentences of this conversation (context only, do not translate):\n")
                recent.forEach { append("- ").append(it).append('\n') }
            }
            append("\n\nTranscript to translate:\n").append(text)
        }
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
            .toString()
    }

    fun parse(json: String): String? = runCatching {
        JSONObject(json).getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text").trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}

/** Eigener Dienst auf dem Server: Claude über das Claude-Code-Abo, eine Sitzung je Gespräch. */
object ClaudeServerProtocol {
    fun request(session: String, target: Lang, text: String): String =
        JSONObject().put("sitzung", session).put("ziel", target.code).put("text", text).toString()

    fun parse(json: String): String? =
        runCatching { JSONObject(json).getString("uebersetzung").trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
}

/** Azure-Sprachausgabe per REST, PCM 24 kHz wie die übrige Wiedergabe. */
object AzureProtocol {
    const val FORMAT = "raw-24khz-16bit-mono-pcm"

    fun url(region: String) = "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"

    fun voice(target: Lang): String = when (target) {
        Lang.SR -> "sr-Latn-RS-SophieNeural"
        Lang.HR -> "hr-HR-GabrijelaNeural"
        Lang.BS -> "bs-BA-VesnaNeural"
        Lang.EN -> "en-GB-SoniaNeural"
        Lang.FR -> "fr-CH-ArianeNeural"
        Lang.IT -> "it-IT-ElsaNeural"
        else -> "de-CH-LeniNeural"
    }

    fun ssml(target: Lang, text: String): String {
        val v = voice(target)
        val locale = v.substringBeforeLast('-')
        val esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return "<speak version='1.0' xml:lang='$locale'><voice name='$v'>$esc</voice></speak>"
    }
}
