package jp.kyoto.sync.parley.net

/**
 * Zerlegt eine Übersetzung in Sätze für die Stimme (nach . ! ? gefolgt von Leerraum und Grossbuchstabe).
 * Bekannte Abkürzungen wie «dr.» beenden keinen Satz; kurze Wörter wie «je.» schon.
 */
object Saetze {
    private val GRENZE = Regex("""(?<=[.!?])\s+(?=[\p{Lu}„"«])""")
    private val ABKUERZUNGEN = setOf("dr", "mr", "gđa", "gđ", "gosp", "br", "sv", "npr", "tj", "itd", "str", "z.b", "bzw", "nr", "st")

    fun split(text: String): List<String> {
        val out = mutableListOf<String>()
        for (t in text.trim().split(GRENZE)) {
            val letztes = out.lastOrNull()?.substringAfterLast(' ')?.trimEnd('.')?.lowercase()
            if (letztes != null && letztes in ABKUERZUNGEN) out[out.lastIndex] = out.last() + " " + t else out += t
        }
        return out.filter { it.isNotBlank() }
    }
}
