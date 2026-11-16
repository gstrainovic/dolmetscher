package jp.kyoto.sync.parley.net

import android.util.Log
import jp.kyoto.sync.parley.core.Config
import jp.kyoto.sync.parley.core.Lang
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Dolmetscher über die OpenAI-Realtime-API (`gpt-realtime-2.1`) per WebSocket, direkt vom
 * Handy zu OpenAI mit dem eigenen Schlüssel. Nachrichten in [InterpreterProtocol].
 */
class InterpreterSession(
    private val apiKey: String,
    private val target: Lang,
    private val transcriptionModel: String,
    private val http: OkHttpClient,
    private val listener: TranslationSession.Listener,
    private val model: String = Config.OPENAI_MODEL,
) : Translator {

    private var ws: WebSocket? = null

    override fun connect() {
        val req = Request.Builder()
            .url(Config.OPENAI_REALTIME_WS + model)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(InterpreterProtocol.sessionUpdate(target, transcriptionModel))
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when (val e = InterpreterProtocol.parse(text)) {
                    is InterpreterEvent.Audio -> listener.onTranslatedAudio(e.pcm16)
                    is InterpreterEvent.OutputTranscript -> listener.onOutputTranscript(e.text)
                    is InterpreterEvent.InputTranscript -> listener.onInputTranscript(e.text)
                    is InterpreterEvent.Error -> listener.onError(RuntimeException(e.message))
                    null -> Unit
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket-Fehler (Ziel=${target.code})", t)
                listener.onError(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed()
            }
        })
    }

    override fun sendAudio(pcm16: ByteArray) {
        ws?.send(InterpreterProtocol.appendAudio(pcm16))
    }

    override fun close() {
        runCatching { ws?.close(1000, "client closing") }
        ws = null
    }

    companion object {
        private const val TAG = "InterpreterSession"
    }
}
