package jp.kyoto.sync.parley.net

import android.os.Handler
import android.os.Looper
import android.util.Log
import jp.kyoto.sync.parley.audio.Downsample16k
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Spracherkennung als erstes Glied einer Kette: Ton rein, je fertigem Satz hochdeutscher Text raus. */
interface Recognizer {
    interface Listener {
        fun onText(text: String)
        fun onError(t: Throwable)
    }

    fun connect()
    fun sendAudio(pcm24k: ByteArray)
    fun endOfSpeech()
    fun close()
}

/** Suisse Speech live: Schweizerdeutsch zu hochdeutschem Text (`SuisseProtocol`). */
class SuisseRecognizer(
    private val key: String,
    private val http: OkHttpClient,
    private val listener: Recognizer.Listener,
) : Recognizer {
    @Volatile private var ws: WebSocket? = null
    private val policy = ReconnectPolicy()
    private val handler = Handler(Looper.getMainLooper())

    override fun connect() {
        val req = Request.Builder().url(SuisseProtocol.URL).addHeader("X-API-Key", key).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(SuisseProtocol.config())
                policy.connected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when (val e = SuisseProtocol.parse(text)) {
                    is SuisseEvent.Final -> listener.onText(e.text)
                    is SuisseEvent.Error -> listener.onError(RuntimeException(e.message))
                    null -> Unit
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val status = response?.code
                if (status == 401 || status == 403) {
                    policy.stop()
                    listener.onError(RuntimeException("Suisse Speech: Schlüssel ungültig (HTTP $status)"))
                    return
                }
                dropped(webSocket, t.message ?: t.toString())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (policy.active) dropped(webSocket, "Suisse Speech $code: $reason")
            }
        })
    }

    // Suisse Speech beendet einen Strom nach 120 s ohne Daten oder nach 15 Minuten: still neu verbinden.
    @Synchronized
    private fun dropped(socket: WebSocket, reason: String) {
        if (socket !== ws) return
        ws = null
        val delay = policy.nextDelayMs()
        if (delay == null) {
            if (policy.active) listener.onError(RuntimeException(reason))
            return
        }
        Log.i("SuisseRecognizer", "neu verbinden in $delay ms ($reason)")
        handler.postDelayed({ if (policy.active) connect() }, delay)
    }

    override fun sendAudio(pcm24k: ByteArray) {
        ws?.send(SuisseProtocol.appendAudio(pcm24k))
    }

    override fun endOfSpeech() {
        ws?.send(SuisseProtocol.finalize())
    }

    override fun close() {
        policy.stop()
        handler.removeCallbacksAndMessages(null)
        runCatching { ws?.close(1000, "client closing") }
        ws = null
    }
}

/**
 * Voxtral Mini Transcribe (Mistral): sammelt den Ton eines Satzes (die Sprecherkennung des Handys
 * liefert Anfang und Ende) und schickt ihn als WAV; USD 0.003 je Minute.
 */
class VoxtralRecognizer(
    private val key: String,
    private val http: OkHttpClient,
    private val listener: Recognizer.Listener,
) : Recognizer {
    private val buffer = ByteArrayOutputStream()

    override fun connect() = Unit

    @Synchronized
    override fun sendAudio(pcm24k: ByteArray) {
        buffer.write(Downsample16k.apply(pcm24k))
    }

    override fun endOfSpeech() {
        val pcm = synchronized(this) { buffer.toByteArray().also { buffer.reset() } }
        if (pcm.size < 16_000) return // unter einer halben Sekunde: kein Satz
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", VoxtralProtocol.MODEL)
            .addFormDataPart("language", "de")
            .addFormDataPart("context_bias", SuisseProtocol.VOCABULARY.joinToString(","))
            .addFormDataPart("file", "satz.wav", VoxtralProtocol.wav16k(jp.kyoto.sync.parley.audio.Lautheit.normalisiert(pcm)).toRequestBody("audio/wav".toMediaType()))
            .build()
        val req = Request.Builder().url(VoxtralProtocol.URL).addHeader("Authorization", "Bearer $key").post(body).build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = listener.onError(e)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (!it.isSuccessful) listener.onError(RuntimeException("Voxtral HTTP ${it.code}: ${text.take(200)}"))
                    else VoxtralProtocol.parse(text)?.let(listener::onText)
                }
            }
        })
    }

    override fun close() {
        synchronized(this) { buffer.reset() }
    }
}
