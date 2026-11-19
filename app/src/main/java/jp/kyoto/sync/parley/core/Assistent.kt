package jp.kyoto.sync.parley.core

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo

/** Ein Audio-Ausgang des Handys, wie ihn `AudioManager.getDevices` meldet. */
data class Ausgang(val id: Int, val typ: Int, val adresse: String = "")

/** Schritte des Einrichtungs-Assistenten; [FERTIG] gibt die Hauptseite frei. */
enum class Schritt { BLUETOOTH, KOPPELN, TEST, FERTIG }

/**
 * Die App läuft nie ohne Kopfhörer oder Brille. Fehlt das Gerät, beim Öffnen oder mitten im Betrieb, zeigt sie
 * den Assistenten statt der Hauptseite. Nur ein Entwickler-Build (`-Pentwickler=true`) kommt ohne Gerät durch.
 */
object Assistent {

    // TYPE_BLE_HEADSET gibt es ab Android 12; der Wert ist eine Konstante, ältere Geräte melden ihn nie.
    @SuppressLint("InlinedApi")
    private val KOPFHOERER = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
    )

    fun istKopfhoerer(typ: Int) = typ in KOPFHOERER

    /** Der Kopfhörer, über den Testton und Übersetzung gehen; nie Lautsprecher oder Hörkapsel des Handys. */
    fun kopfhoerer(ausgaenge: List<Ausgang>): Ausgang? = ausgaenge.firstOrNull { istKopfhoerer(it.typ) }

    /**
     * @param geprueft Kennung des Geräts, dessen Funktionstest in dieser Sitzung bestanden ist, sonst null.
     * @param laeuft Die Übersetzung läuft schon (App geschlossen und wieder geöffnet): kein neuer Test, damit der
     *   Stopp-Knopf erreichbar bleibt. Ohne Gerät läuft der Dienst nie, der Test vor dem Start genügt.
     * @param gepruefteAdresse Bluetooth-Adresse des geprüften Geräts: Stopp trennt die Brille und Start verbindet
     *   sie wieder, dabei vergibt Android eine neue Kennung; dieselbe Brille braucht keinen neuen Test.
     * @param brilleGetrennt Stopp hat die gekoppelte Brille getrennt: Hauptseite statt Assistent, Start verbindet
     *   sie wieder (`audio/Brille.kt`).
     */
    fun schritt(
        bluetoothAn: Boolean,
        ausgaenge: List<Ausgang>,
        geprueft: Int?,
        entwickler: Boolean,
        laeuft: Boolean = false,
        gepruefteAdresse: String? = null,
        brilleGetrennt: Boolean = false,
    ): Schritt {
        if (entwickler) return Schritt.FERTIG
        val geraet = kopfhoerer(ausgaenge)
        val schonGeprueft = geraet != null &&
            (geraet.id == geprueft || (geraet.adresse.isNotEmpty() && geraet.adresse == gepruefteAdresse))
        return when {
            geraet == null && !bluetoothAn -> Schritt.BLUETOOTH
            geraet == null && brilleGetrennt -> Schritt.FERTIG
            geraet == null -> Schritt.KOPPELN
            !schonGeprueft && !laeuft -> Schritt.TEST
            else -> Schritt.FERTIG
        }
    }

    /** Bestanden, wenn die Person den Testton im Gerät hört und das Mikrofon ihre Stimme hört. */
    fun testBestanden(tonGehoert: Boolean, stimmeGehoert: Boolean) = tonGehoert && stimmeGehoert
}
