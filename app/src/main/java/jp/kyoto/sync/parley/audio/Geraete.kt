package jp.kyoto.sync.parley.audio

import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import jp.kyoto.sync.parley.core.Assistent
import jp.kyoto.sync.parley.core.Ausgang

/** Liest Bluetooth-Zustand und Audio-Ausgänge des Handys für den Assistenten ([Assistent]). */
object Geraete {

    fun ausgaenge(ctx: Context): List<Ausgang> =
        audioManager(ctx).getDevices(AudioManager.GET_DEVICES_OUTPUTS).map {
            Ausgang(it.id, it.type, if (android.os.Build.VERSION.SDK_INT >= 28) it.address.orEmpty() else "")
        }

    /** Der Kopfhörer als Android-Gerät, damit ein Ton gezielt dorthin geht; null, wenn keiner verbunden ist. */
    fun kopfhoerer(ctx: Context): AudioDeviceInfo? {
        val ziel = Assistent.kopfhoerer(ausgaenge(ctx)) ?: return null
        return audioManager(ctx).getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == ziel.id }
    }

    fun hatKopfhoerer(ctx: Context) = Assistent.kopfhoerer(ausgaenge(ctx)) != null

    /** Ohne Berechtigung (Android 11 und älter ohne BLUETOOTH) gilt Bluetooth als aus; der Assistent fragt dann. */
    fun bluetoothAn(ctx: Context): Boolean = runCatching {
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }.getOrDefault(false)

    private fun audioManager(ctx: Context) = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
}
