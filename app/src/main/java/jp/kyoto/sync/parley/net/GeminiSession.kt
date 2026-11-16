package jp.kyoto.sync.parley.net

import android.os.Handler
import android.os.Looper
import android.util.Log
import jp.kyoto.sync.parley.core.Lang
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Dolmetscher über die Gemini Live API, direkt vom Handy zu Google mit dem eigenen Schlüssel.
 *
 * Google trennt Verbindungen nach rund 10 Minuten (mit `goAway` angekündigt) oder bei Störungen;
 * dann baut die Sitzung still eine neue Verbindung auf ([ReconnectPolicy]). Erst wenn das
 * mehrmals hintereinander scheitert, meldet sie einen Fehler.
 */
class GeminiSession(
    private val apiKey: String,
    private val target: Lang,
    private val http: OkHttpClient,
    private val listener: TranslationSession.Listener,
    private val trace: (String) -> Unit = {},
    private val instructions: String = InterpreterProtocol.instructions(target),
) : Translator, jp.kyoto.sync.parley.audio.Vorlesesitzung {

    @Volatile private var ws: WebSocket? = null
    private val policy = ReconnectPolicy()
    private val handler = Handler(Looper.getMainLooper())

    // Sätze erst nach setupComplete schicken; vorher (Aufbau, Neuverbindung) festhalten statt verlieren.
    private val texts = ReadyQueue<String> { t ->
        ws?.send(GeminiProtocol.text(t))
        trace("Gemini: Text geschickt")
    }

    override fun connect() = open()

    private fun open() {
        texts.notReady()
        trace("Gemini: verbinde")
        val req = Request.Builder().url(URL).addHeader("x-goog-api-key", apiKey).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(GeminiProtocol.setup(target, instructions))
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(webSocket, text)

            // Gemini schickt JSON auch als Binärrahmen.
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(webSocket, bytes.utf8())

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Verbindung gestört (Ziel=${target.code})", t)
                dropped(webSocket, t.message ?: t.toString())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (policy.active) dropped(webSocket, "Gemini $code: $reason")
            }
        })
    }

    private fun handle(socket: WebSocket, text: String) {
        if (GeminiProtocol.isSetupComplete(text)) {
            policy.connected()
            trace("Gemini: bereit")
            texts.ready()
        }
        if (GeminiProtocol.isGoAway(text)) {
            dropped(socket, "goAway")
            return
        }
        for (e in GeminiProtocol.parse(text)) {
            when (e) {
                is InterpreterEvent.Audio -> listener.onTranslatedAudio(e.pcm16)
                is InterpreterEvent.OutputTranscript -> listener.onOutputTranscript(e.text)
                is InterpreterEvent.InputTranscript -> listener.onInputTranscript(e.text)
                is InterpreterEvent.Error -> listener.onError(RuntimeException(e.message))
            }
        }
    }

    /** Verbindung weg: neu verbinden, solange die Sitzung läuft und die Regel es erlaubt. */
    @Synchronized
    private fun dropped(socket: WebSocket, reason: String) {
        if (socket !== ws) return // alte Verbindung, schon ersetzt
        ws = null
        texts.notReady()
        trace("Gemini: getrennt ($reason)")
        runCatching { socket.close(1000, null) }
        val delay = policy.nextDelayMs()
        if (delay == null) {
            if (policy.active) listener.onError(RuntimeException(reason))
            return
        }
        Log.i(TAG, "Neu verbinden in $delay ms ($reason)")
        handler.postDelayed({ if (policy.active) open() }, delay)
    }

    override fun sendAudio(pcm16: ByteArray) {
        ws?.send(GeminiProtocol.appendAudio(pcm16))
    }

    /** Fertig erkannter Satz statt Ton (Kette mit Suisse Speech). */
    override fun sendText(text: String) = texts.offer(text)

    override fun endOfSpeech() {
        ws?.send(GeminiProtocol.audioStreamEnd())
    }

    override fun close() {
        policy.stop()
        handler.removeCallbacksAndMessages(null)
        runCatching { ws?.close(1000, "client closing") }
        ws = null
        listener.onClosed()
    }

    companion object {
        private const val TAG = "GeminiSession"
        private const val URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
}
