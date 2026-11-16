package jp.kyoto.sync.parley.net

import org.junit.Test

/** Eine Antwort, die erst nach dem Stopp ankommt, darf die App nicht abstürzen lassen (Absturz 15.11. 20:44 und 20:51). */
class ChainSessionTest {

    private var erkenner: Recognizer.Listener? = null

    private val session = ChainSession(
        makeRecognizer = { l ->
            erkenner = l
            object : Recognizer {
                override fun connect() = Unit
                override fun sendAudio(pcm24k: ByteArray) = Unit
                override fun endOfSpeech() = Unit
                override fun close() = Unit
            }
        },
        gemini = null,
        translator = object : TextTranslator {
            override fun translate(text: String) = text
        },
        voice = null,
        listener = object : TranslationSession.Listener {
            override fun onTranslatedAudio(pcm16: ByteArray) = Unit
            override fun onOutputTranscript(text: String) = Unit
            override fun onError(t: Throwable) = Unit
        },
    )

    @Test
    fun aLateRecognitionAfterStopIsIgnored() {
        session.close()
        erkenner!!.onText("Bring mal bitte die zwei Rüebli.")
    }
}
