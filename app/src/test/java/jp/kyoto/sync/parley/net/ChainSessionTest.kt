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

    /** Spricht Vesna selbst Serbisch, meldet der Übersetzer [InterpreterProtocol.SKIP]: nichts vorlesen, nichts anzeigen. */
    @Test
    fun textAlreadyInTargetLanguageIsNotSpoken() {
        var kette: Recognizer.Listener? = null
        val gesprochen = mutableListOf<String>()
        val angezeigt = StringBuilder()
        val fertig = java.util.concurrent.CountDownLatch(2)
        val s = ChainSession(
            makeRecognizer = { l ->
                kette = l
                object : Recognizer {
                    override fun connect() = Unit
                    override fun sendAudio(pcm24k: ByteArray) = Unit
                    override fun endOfSpeech() = Unit
                    override fun close() = Unit
                }
            },
            gemini = null,
            translator = object : TextTranslator {
                override fun translate(text: String) =
                    if (text.startsWith("Nemoj")) InterpreterProtocol.SKIP else "Donesi mleko."
            },
            voice = object : jp.kyoto.sync.parley.audio.SpeechOut {
                override fun speak(text: String): ByteArray {
                    gesprochen += text
                    return ByteArray(0)
                }
            },
            listener = object : TranslationSession.Listener {
                override fun onTranslatedAudio(pcm16: ByteArray) = Unit
                override fun onOutputTranscript(text: String) {
                    angezeigt.append(text)
                    if (text == "\n") fertig.countDown()
                }
                override fun onError(t: Throwable) = throw t
            },
        )
        kette!!.onText("Nemoj da brišeš.")
        kette!!.onText("Bring mir die Milch.")
        org.junit.Assert.assertTrue(fertig.await(5, java.util.concurrent.TimeUnit.SECONDS))
        s.close()
        org.junit.Assert.assertEquals(listOf("Donesi mleko."), gesprochen)
        org.junit.Assert.assertFalse(angezeigt.contains(InterpreterProtocol.SKIP))
    }
}
