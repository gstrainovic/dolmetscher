package jp.kyoto.sync.parley.core

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo

/** Ein Audio-Ausgang des Handys, wie ihn `AudioManager.getDevices` meldet. */
data class Ausgang(val id: Int, val typ: Int)

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
     */
    fun schritt(bluetoothAn: Boolean, ausgaenge: List<Ausgang>, geprueft: Int?, entwickler: Boolean): Schritt {
        if (entwickler) return Schritt.FERTIG
        val geraet = kopfhoerer(ausgaenge)
        return when {
            geraet == null && !bluetoothAn -> Schritt.BLUETOOTH
            geraet == null -> Schritt.KOPPELN
            geraet.id != geprueft -> Schritt.TEST
            else -> Schritt.FERTIG
        }
    }

    /** Bestanden, wenn die Person den Testton im Gerät hört und das Mikrofon ihre Stimme hört. */
    fun testBestanden(tonGehoert: Boolean, stimmeGehoert: Boolean) = tonGehoert && stimmeGehoert
}
