package jp.kyoto.sync.parley.core

/**
 * Sprachen für die Übersetzung. [code] ist der ISO-639-1-Code, [displayName] der Name in der
 * eigenen Sprache (Auswahlliste), [englishName] steht in der Anweisung an das Sprachmodell.
 *
 * [translateModel] sagt, ob `gpt-realtime-translate` die Sprache als Ziel kann; alle anderen
 * laufen über den Dolmetscher mit `gpt-realtime-2.1`.
 */
enum class Lang(
    val code: String,
    val displayName: String,
    val englishName: String,
    val translateModel: Boolean = true,
) {
    SR("sr", "Srpski", "Serbian", translateModel = false),
    /** Gegenüber spricht Mundart: eigene Kette mit Claudes Deutung ([Setup.vorschlag]). */
    DE_CH("de-CH", "Schweizerdeutsch", "German"),
    DE("de", "Deutsch", "German"),
    EN("en", "English", "English"),
    FR("fr", "Français", "French"),
    IT("it", "Italiano", "Italian"),
    HR("hr", "Hrvatski", "Croatian", translateModel = false),
    BS("bs", "Bosanski", "Bosnian", translateModel = false),
    ES("es", "Español", "Spanish"),
    PT("pt", "Português", "Portuguese"),
    RU("ru", "Русский", "Russian"),
    HI("hi", "हिन्दी", "Hindi"),
    ID("id", "Bahasa Indonesia", "Indonesian"),
    JA("ja", "日本語", "Japanese"),
    KO("ko", "한국어", "Korean"),
    VI("vi", "Tiếng Việt", "Vietnamese"),
    ;

    companion object {
        fun fromCode(code: String): Lang? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Sprachpaar eines Gesprächs.
 *
 * - incoming (Gegenüber → ich): Stimme des Gegenübers in [myLang] übersetzt, in die Kopfhörer
 * - outgoing (ich → Gegenüber): eigene Stimme in [partnerLang] übersetzt, über den Lautsprecher
 *
 * Die Ausgangssprache erkennt das Modell selbst; angegeben wird nur die Zielsprache.
 */
data class Conversation(
    val myLang: Lang,
    val partnerLang: Lang,
) {
    val incomingTarget: Lang get() = myLang
    val outgoingTarget: Lang get() = partnerLang

    companion object {
        /** Benutzer hört Serbisch, das Gegenüber spricht Deutsch oder Schweizerdeutsch. */
        val DEFAULT = Conversation(myLang = Lang.SR, partnerLang = Lang.DE_CH)

        val JA_EN = Conversation(myLang = Lang.JA, partnerLang = Lang.EN)
    }
}
