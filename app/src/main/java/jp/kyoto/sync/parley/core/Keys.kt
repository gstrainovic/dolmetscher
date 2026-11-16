package jp.kyoto.sync.parley.core

import jp.kyoto.sync.parley.BuildConfig

/** Fest eingebaute Schlüssel (aus secrets.properties beim Bauen). Leer heisst: nicht eingerichtet. */
object Keys {
    val gemini get() = BuildConfig.GEMINI_KEY
    val suisse get() = BuildConfig.SUISSE_KEY
    val mistral get() = BuildConfig.MISTRAL_KEY
    val openai get() = BuildConfig.OPENAI_KEY
    val azure get() = BuildConfig.AZURE_KEY
    val azureRegion get() = BuildConfig.AZURE_REGION
    val claudeServerUrl get() = BuildConfig.CLAUDE_SERVER_URL
    val claudeServerToken get() = BuildConfig.CLAUDE_SERVER_TOKEN

    /** Namen der Schlüssel, die für diese Kombination fehlen (für eine klare Fehlermeldung). */
    fun missing(setup: Setup): List<String> {
        val need = mutableListOf<Pair<String, String>>()
        when (setup.recognition) {
            Recognition.SUISSE -> need += "Suisse Speech" to suisse
            Recognition.VOXTRAL -> need += "Mistral (Voxtral)" to mistral
            Recognition.GEMINI_LIVE -> need += "Gemini" to gemini
            Recognition.OPENAI, Recognition.OPENAI_MINI -> need += "OpenAI" to openai
        }
        if (setup.usesVoice) when (setup.voice) {
            Voice.GEMINI, Voice.GEMINI_VORLESEN -> need += "Gemini" to gemini
            Voice.AZURE -> { need += "Azure" to azure; need += "Azure-Region" to azureRegion }
            Voice.ANDROID -> Unit
        }
        if (setup.usesTranslator) when (setup.translation) {
            Translation.CLAUDE_SERVER -> { need += "Claude-Server" to claudeServerUrl; need += "Claude-Server-Token" to claudeServerToken }
            Translation.GEMINI_FLASH -> need += "Gemini" to gemini
        }
        return need.filter { it.second.isBlank() }.map { it.first }.distinct()
    }
}
