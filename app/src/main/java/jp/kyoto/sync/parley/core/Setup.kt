package jp.kyoto.sync.parley.core

/** Wer das Gesprochene erkennt. Die letzten drei machen alles in einem (Erkennung, Übersetzung, Stimme). */
enum class Recognition(val label: String, val allInOne: Boolean = false) {
    SUISSE("Suisse Speech"),
    VOXTRAL("Voxtral"),
    GEMINI_LIVE("Gemini Live allein", allInOne = true),
    OPENAI("OpenAI Realtime", allInOne = true),
    OPENAI_MINI("OpenAI Realtime mini", allInOne = true),
}

/** Wer den erkannten Text übersetzt, wenn die Stimme nicht selbst übersetzt (Azure, Android). */
enum class Translation(val label: String) {
    CLAUDE_SERVER("Claude (eigener Server)"),
    GEMINI_FLASH("Gemini Flash"),
}

/** Wer die Übersetzung vorliest. Gemini übersetzt und spricht in einem Schritt. */
enum class Voice(val label: String) {
    GEMINI("Gemini-Stimme"),
    /** Der Textübersetzer (Claude) übersetzt, Gemini Live liest nur vor. */
    GEMINI_VORLESEN("Gemini liest vor"),
    AZURE("Azure-Stimme"),
    ANDROID("Gratis-Stimme (Android)"),
}

/** Gewählte Kombination, in den Einstellungen frei zusammenstellbar. */
data class Setup(val recognition: Recognition, val translation: Translation, val voice: Voice) {
    val allInOne: Boolean get() = recognition.allInOne
    val usesVoice: Boolean get() = !allInOne
    val usesTranslator: Boolean get() = !allInOne && voice != Voice.GEMINI

    fun encode(): String = "${recognition.name}|${translation.name}|${voice.name}"

    companion object {
        /**
         * Vorgeschlagene Kette je Sprache des Gegenübers: Deutsch und Mundart laufen über Voxtral und Claudes
         * Deutung der fehlerhaften Umschrift, Gemini liest vor; nur dort hält das Auslassen fremder Sprachen sicher
         * (messungen/nur-deutsch.md). Andere Sprachen übersetzt Gemini Live allein, weil Voxtral fest Deutsch erkennt.
         */
        fun vorschlag(partner: Lang): Setup = when (partner) {
            Lang.DE_CH -> Setup(Recognition.VOXTRAL, Translation.CLAUDE_SERVER, Voice.GEMINI_VORLESEN)
            else -> Setup(Recognition.GEMINI_LIVE, Translation.CLAUDE_SERVER, Voice.GEMINI)
        }

        val DEFAULT = vorschlag(Lang.DE_CH)

        fun decode(s: String?, standard: Setup = DEFAULT): Setup = runCatching {
            val (r, t, v) = s!!.split("|")
            Setup(Recognition.valueOf(r), Translation.valueOf(t), Voice.valueOf(v))
        }.getOrDefault(standard)
    }
}
