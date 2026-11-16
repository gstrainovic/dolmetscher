package jp.kyoto.sync.parley.net

import java.util.concurrent.Executors

/**
 * Kette aus einzeln gewählten Gliedern: Erkennung ([Recognizer]) liefert je Satz Text; dann entweder
 * Gemini Live (übersetzt und spricht in einem, `gemini` gesetzt) oder Text-Übersetzer plus Stimme.
 * Sätze laufen der Reihe nach über einen eigenen Faden, damit die Reihenfolge stimmt.
 */
class ChainSession(
    makeRecognizer: (Recognizer.Listener) -> Recognizer,
    private val gemini: GeminiSession?,
    private val translator: TextTranslator?,
    private val voice: jp.kyoto.sync.parley.audio.SpeechOut?,
    private val listener: TranslationSession.Listener,
    /** Kennt alles, was die App eben gesprochen hat (bei Gemini füttert dessen Untertitel ihn). */
    private val echo: EchoFilter = EchoFilter(),
    private val trace: (String) -> Unit = {},
) : Translator {

    private val worker = Executors.newSingleThreadExecutor()

    private val recognizer = makeRecognizer(object : Recognizer.Listener {
        override fun onText(text: String) {
            if (worker.isShutdown) return // Antwort kam erst nach dem Stopp
            if (echo.isEcho(text)) {
                trace("Echo verworfen: $text")
                return
            }
            listener.onInputTranscript(text + "\n")
            if (gemini != null) {
                gemini.sendText(text)
                return
            }
            try {
                worker.execute {
                    try {
                        // Satz für Satz: der erste Satz wird gesprochen, sobald der Übersetzer ihn fertig hat.
                        translator!!.translateSentences(text) { satz ->
                            echo.spoken(satz)
                            listener.onOutputTranscript("$satz ")
                            listener.onTranslatedAudio(voice!!.speak(satz))
                        }
                        listener.onOutputTranscript("\n")
                    } catch (t: Throwable) {
                        listener.onError(t)
                    }
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                // im selben Augenblick gestoppt
            }
        }

        override fun onError(t: Throwable) = listener.onError(t)
    })

    override fun connect() {
        gemini?.connect()
        voice?.connect()
        translator?.let { t -> worker.execute { t.warmUp() } }
        recognizer.connect()
        listener.onOpen()
    }

    override fun sendAudio(pcm16: ByteArray) = recognizer.sendAudio(pcm16)

    override fun endOfSpeech() = recognizer.endOfSpeech()

    override fun close() {
        recognizer.close()
        gemini?.close()
        worker.shutdownNow()
        voice?.close()
    }
}
