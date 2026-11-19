package jp.kyoto.sync.parley.audio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** Ein gekoppeltes Bluetooth-Gerät: Adresse und ob es ein Audiogerät ist (Kopfhörer, Brille, Auto, Lautsprecher). */
data class Gekoppelt(val adresse: String, val audio: Boolean)

/**
 * Stopp trennt die Brille vom Handy, Start verbindet sie wieder, ohne Umweg über die Bluetooth-Einstellungen. Die
 * Kopplung bleibt. Android bietet dafür keine öffentliche Schnittstelle; `BluetoothA2dp.connect` und `disconnect`
 * sind versteckt, aber für Apps freigegeben (`@UnsupportedAppUsage`) und verlangen nur `BLUETOOTH_CONNECT`.
 * Scheitert der Aufruf (Gerätehersteller, spätere Android-Version), bleibt alles wie ohne diese Funktion.
 */
object Brille {

    private const val PREFS = "brille"
    private const val ADRESSE = "adresse"
    private const val GETRENNT = "getrennt"

    /** Stopp hat die Brille getrennt und Start soll sie wieder verbinden; die Hauptseite bleibt dann stehen. */
    val getrennt = MutableStateFlow(false)

    /** Beim Öffnen der App: gilt die Trennung vom letzten Stopp noch (Brille weiterhin gekoppelt)? */
    @SuppressLint("MissingPermission")
    fun laden(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val adresse = p.getString(ADRESSE, null)
        val gekoppelt = runCatching {
            ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty().any { it.address == adresse }
        }.getOrDefault(false)
        getrennt.value = p.getBoolean(GETRENNT, false) && gekoppelt
    }

    /** Nach dem Verbindungsversuch beim Start: fehlt das Gerät danach, zeigt die App wieder den Assistenten. */
    fun vergessen(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(GETRENNT, false).apply()
        getrennt.value = false
    }

    /**
     * Welche gekoppelte Brille der Start verbindet: die zuletzt von der App getrennte, sonst das einzige gekoppelte
     * Audiogerät. Bei mehreren ohne gemerkte keines, damit nie Auto oder Lautsprecher den Ton bekommen.
     */
    fun wahl(gekoppelt: List<Gekoppelt>, gemerkt: String?): String? {
        if (gemerkt != null && gekoppelt.any { it.adresse == gemerkt }) return gemerkt
        return gekoppelt.filter { it.audio }.singleOrNull()?.adresse
    }

    /** Trennt alle per A2DP verbundenen Geräte und merkt sich die Adresse für den nächsten Start. */
    fun trennen(ctx: Context) = mitA2dp(ctx) { a2dp ->
        val verbunden = a2dp.connectedDevices
        val adresse = verbunden.firstOrNull()?.address ?: return@mitA2dp
        if (verbunden.map { aufruf(a2dp, "disconnect", it) }.none { it.getOrNull() == true }) return@mitA2dp
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ADRESSE, adresse).putBoolean(GETRENNT, true).apply()
        getrennt.value = true
    }

    /** Verbindet die Brille nach [wahl], wenn Bluetooth an ist und noch kein Gerät verbunden ist. */
    @SuppressLint("MissingPermission")
    fun verbinden(ctx: Context) = mitA2dp(ctx) { a2dp ->
        if (a2dp.connectedDevices.isNotEmpty()) return@mitA2dp
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return@mitA2dp
        val geraete = adapter.bondedDevices.orEmpty()
        val gekoppelt = geraete.map {
            Gekoppelt(it.address, it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO)
        }
        val ziel = wahl(gekoppelt, gemerkt(ctx)) ?: return@mitA2dp
        geraete.firstOrNull { it.address == ziel }?.let { aufruf(a2dp, "connect", it) }
    }

    private fun aufruf(a2dp: BluetoothProfile, methode: String, geraet: BluetoothDevice) = runCatching {
        a2dp.javaClass.getMethod(methode, BluetoothDevice::class.java).invoke(a2dp, geraet)
    }

    /** Holt den A2DP-Dienst (kommt asynchron), führt [tun] aus und gibt ihn wieder frei. Ohne Berechtigung nichts. */
    @SuppressLint("MissingPermission")
    private fun mitA2dp(ctx: Context, tun: (BluetoothProfile) -> Unit) {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        runCatching {
            if (!adapter.isEnabled) return
            adapter.getProfileProxy(ctx.applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    runCatching { tun(proxy) }
                    runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
                }

                override fun onServiceDisconnected(profile: Int) = Unit
            }, BluetoothProfile.A2DP)
        }
    }

    private fun gemerkt(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ADRESSE, null)
}
