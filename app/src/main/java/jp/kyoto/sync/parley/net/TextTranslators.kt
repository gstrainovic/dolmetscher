package jp.kyoto.sync.parley.net

import jp.kyoto.sync.parley.core.Lang
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/** Übersetzt einen erkannten Satz als Text (blockierend, aus dem Arbeitsfaden der Kette). */
interface TextTranslator {
    fun translate(text: String): String

    /** Übersetzung Satz für Satz, sobald ein Satz fertig ist; ohne Teilantworten am Ende alles auf einmal. */
    fun translateSentences(text: String, onSentence: (String) -> Unit) {
        Saetze.split(translate(text)).forEach(onSentence)
    }

    /** Verbindung oder Sitzung schon vor dem ersten Satz aufbauen. */
    fun warmUp() {}
}

/** Gemini Flash über die API, mit den letzten Sätzen als Zusammenhang. */
class GeminiFlashTranslator(
    private val key: String,
    private val target: Lang,
    private val http: OkHttpClient,
) : TextTranslator {
    private val recent = ArrayDeque<String>()

    override fun translate(text: String): String {
        val req = Request.Builder().url(GeminiFlashProtocol.URL).addHeader("x-goog-api-key", key)
            .post(GeminiFlashProtocol.request(target, recent.toList(), text).toRequestBody(JSON)).build()
        val out = http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("Gemini Flash HTTP ${r.code}: ${body.take(200)}")
            GeminiFlashProtocol.parse(body) ?: error("Gemini Flash: leere Antwort")
        }
        recent.addLast(text)
        while (recent.size > CONTEXT) recent.removeFirst()
        return out
    }

    companion object {
        private const val CONTEXT = 6
    }
}

/** Claude über den eigenen Server (Claude-Code-Abo); der Server hält den Gesprächsverlauf je Sitzung. */
class ClaudeServerTranslator(
    private val url: String,
    private val token: String,
    private val target: Lang,
    private val http: OkHttpClient,
) : TextTranslator {
    private val session = UUID.randomUUID().toString()

    override fun translate(text: String): String {
        val req = Request.Builder().url(url.trimEnd('/') + "/uebersetzen").addHeader("Authorization", "Bearer $token")
            .post(ClaudeServerProtocol.request(session, target, text).toRequestBody(JSON)).build()
        return http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("Claude-Server HTTP ${r.code}: ${body.take(200)}")
            ClaudeServerProtocol.parse(body) ?: error("Claude-Server: leere Antwort")
        }
    }

    /** Teilantworten von `/uebersetzen/strom`: der erste Satz kommt nach rund einer Sekunde. */
    override fun translateSentences(text: String, onSentence: (String) -> Unit) {
        val req = Request.Builder().url(url.trimEnd('/') + "/uebersetzen/strom")
            .addHeader("Authorization", "Bearer $token")
            .post(ClaudeServerProtocol.request(session, target, text).toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Claude-Server HTTP ${r.code}: ${r.body?.string().orEmpty().take(200)}")
            val source = r.body?.source() ?: error("Claude-Server: leere Antwort")
            val strom = SatzStrom()
            while (!source.exhausted()) {
                source.request(1)
                val chunk = source.buffer.readUtf8()
                strom.feed(chunk).forEach(onSentence)
            }
            strom.finish().forEach(onSentence)
        }
    }

    /** Claude-Sitzung auf dem Server schon beim Start anlegen; der erste Satz spart rund zwei Sekunden. */
    override fun warmUp() {
        val req = Request.Builder().url(url.trimEnd('/') + "/vorwaermen").addHeader("Authorization", "Bearer $token")
            .post(ClaudeServerProtocol.request(session, target, "-").toRequestBody(JSON)).build()
        runCatching { http.newCall(req).execute().close() }
    }
}

private val JSON = "application/json".toMediaType()
