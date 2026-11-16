package jp.kyoto.sync.parley.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Sprache der Oberfläche, unabhängig von der Handysprache: Serbisch ist Standard, Deutsch in den Einstellungen
 * wählbar. Die Texte für Englisch und Japanisch aus dem Original bleiben im Repo, sind aber nicht wählbar.
 */
enum class Oberflaeche(val code: String) {
    SR("sr"),
    DE("de"),
    ;

    companion object {
        val STANDARD = SR

        fun aus(code: String?): Oberflaeche = entries.firstOrNull { it.code == code } ?: STANDARD

        /** Kontext mit der gewählten Sprache; für `attachBaseContext` von Activity und Dienst. */
        fun kontext(base: Context): Context {
            val locale = Locale.forLanguageTag(AppSettings(base).oberflaeche.code)
            val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
            return base.createConfigurationContext(config)
        }
    }
}
