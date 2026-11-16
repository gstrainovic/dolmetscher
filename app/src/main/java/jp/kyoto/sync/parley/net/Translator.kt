package jp.kyoto.sync.parley.net

import android.content.Context
import jp.kyoto.sync.parley.audio.AndroidVoice
import jp.kyoto.sync.parley.audio.AzureVoice
import jp.kyoto.sync.parley.audio.GeminiVorleser
import jp.kyoto.sync.parley.core.Config
import jp.kyoto.sync.parley.core.Keys
import jp.kyoto.sync.parley.core.Lang
import jp.kyoto.sync.parley.core.Recognition
import jp.kyoto.sync.parley.core.Setup
import jp.kyoto.sync.parley.core.Translation
import jp.kyoto.sync.parley.core.Voice
import okhttp3.OkHttpClient

/**
 * Eine Übersetzungsrichtung: hört Ton (24 kHz PCM16 mono) und liefert übersetzten Ton und
 * Untertitel an den [TranslationSession.Listener].
 */
interface Translator {
    fun connect()
    fun sendAudio(pcm16: ByteArray)

    /** Die Sprecherkennung des Handys hat das Satzende erkannt und schickt bis zum nächsten Satz nichts. */
    fun endOfSpeech() {}

    fun close()

    companion object {
        /** Zuhören (Gegenüber → Benutzer) mit der gewählten Kombination. */
        fun incoming(
            setup: Setup,
            context: Context,
            target: Lang,
            transcriptionModel: String,
            http: OkHttpClient,
            listener: TranslationSession.Listener,
            trace: (String) -> Unit = {},
        ): Translator = when (setup.recognition) {
            Recognition.GEMINI_LIVE -> GeminiSession(Keys.gemini, target, http, listener, trace)
            Recognition.OPENAI -> openAi(Keys.openai, target, transcriptionModel, http, listener)
            Recognition.OPENAI_MINI ->
                InterpreterSession(Keys.openai, target, transcriptionModel, http, listener, Config.OPENAI_MODEL_MINI)
            Recognition.SUISSE, Recognition.VOXTRAL -> {
                val recognizer: (Recognizer.Listener) -> Recognizer = { l ->
                    if (setup.recognition == Recognition.SUISSE) SuisseRecognizer(Keys.suisse, http, l)
                    else VoxtralRecognizer(Keys.mistral, http, l)
                }
                val echo = EchoFilter()
                if (setup.voice == Voice.GEMINI) {
                    // Gemini bekommt den Satz als Text, übersetzt und spricht; Untertitel von Gemini.
                    val gemini = GeminiSession(Keys.gemini, target, http, object : TranslationSession.Listener {
                        override fun onTranslatedAudio(pcm16: ByteArray) = listener.onTranslatedAudio(pcm16)
                        override fun onOutputTranscript(text: String) {
                            echo.spoken(text)
                            listener.onOutputTranscript(text)
                        }
                        override fun onError(t: Throwable) = listener.onError(t)
                    }, trace)
                    ChainSession(recognizer, gemini, null, null, listener, echo, trace)
                } else {
                    val translator = when (setup.translation) {
                        Translation.CLAUDE_SERVER ->
                            ClaudeServerTranslator(Keys.claudeServerUrl, Keys.claudeServerToken, target, http)
                        Translation.GEMINI_FLASH -> GeminiFlashTranslator(Keys.gemini, target, http)
                    }
                    val voice = when (setup.voice) {
                        Voice.AZURE -> AzureVoice(Keys.azure, Keys.azureRegion, target, http)
                        Voice.GEMINI_VORLESEN -> GeminiVorleser {
                            // Nur der Ton geht weiter; Untertitel liefert schon der Textübersetzer.
                            GeminiSession(Keys.gemini, target, http, object : TranslationSession.Listener {
                                override fun onTranslatedAudio(pcm16: ByteArray) = listener.onTranslatedAudio(pcm16)
                                override fun onOutputTranscript(text: String) = Unit
                                override fun onError(t: Throwable) = listener.onError(t)
                            }, trace, InterpreterProtocol.readAloudInstructions(target))
                        }
                        else -> AndroidVoice(context, target)
                    }
                    ChainSession(recognizer, null, translator, voice, listener, echo, trace)
                }
            }
        }

        /**
         * Eigene Sätze (Taste): Suisse Speech und Voxtral erkennen kein Serbisch, deshalb laufen
         * sie bei den Ketten direkt über Gemini Live; sonst über dasselbe Modell wie das Zuhören.
         */
        fun outgoing(
            setup: Setup,
            target: Lang,
            transcriptionModel: String,
            http: OkHttpClient,
            listener: TranslationSession.Listener,
        ): Translator = when (setup.recognition) {
            Recognition.OPENAI -> openAi(Keys.openai, target, transcriptionModel, http, listener)
            Recognition.OPENAI_MINI ->
                InterpreterSession(Keys.openai, target, transcriptionModel, http, listener, Config.OPENAI_MODEL_MINI)
            else -> GeminiSession(Keys.gemini, target, http, listener)
        }

        /**
         * Zielsprachen, die `gpt-realtime-translate` kann, laufen darüber (schneller, Preis je
         * Minute); alle anderen, etwa Serbisch, über den Dolmetscher mit `gpt-realtime-2.1`.
         */
        fun openAi(
            apiKey: String,
            target: Lang,
            transcriptionModel: String,
            http: OkHttpClient,
            listener: TranslationSession.Listener,
        ): Translator =
            if (target.translateModel) {
                TranslationSession(apiKey, target.code, transcriptionModel, http, listener)
            } else {
                InterpreterSession(apiKey, target, transcriptionModel, http, listener)
            }
    }
}
