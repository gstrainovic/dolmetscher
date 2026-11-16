package jp.kyoto.sync.parley

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import jp.kyoto.sync.parley.audio.Geraete
import jp.kyoto.sync.parley.core.AppSettings
import jp.kyoto.sync.parley.core.Assistent
import jp.kyoto.sync.parley.core.Oberflaeche
import jp.kyoto.sync.parley.core.Ausgang
import jp.kyoto.sync.parley.core.Schritt
import jp.kyoto.sync.parley.ui.AssistentScreen
import jp.kyoto.sync.parley.ui.HistoryScreen
import jp.kyoto.sync.parley.ui.SettingsScreen
import jp.kyoto.sync.parley.ui.TranslationScreen
import jp.kyoto.sync.parley.ui.TranslationViewModel
import jp.kyoto.sync.parley.ui.theme.ParleyTheme

class MainActivity : ComponentActivity() {

    private val vm: TranslationViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { geraeteLesen() }

    private val bluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { geraeteLesen() }

    // Zustand für den Assistenten; ändert sich beim Ein- und Ausstecken, Koppeln und Bluetooth ein/aus.
    private var bluetoothAn by mutableStateOf(false)
    private var ausgaenge by mutableStateOf(emptyList<Ausgang>())

    private val geraeteWache = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = geraeteLesen()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = geraeteLesen()
    }

    private val bluetoothWache = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = geraeteLesen()
    }

    // Oberfläche serbisch oder deutsch, unabhängig von der Handysprache.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Oberflaeche.kontext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNeededPermissions()
        geraeteLesen()
        (getSystemService(AUDIO_SERVICE) as AudioManager)
            .registerAudioDeviceCallback(geraeteWache, Handler(Looper.getMainLooper()))
        ContextCompat.registerReceiver(
            this, bluetoothWache, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            ParleyTheme {
                var screen by rememberSaveable { mutableStateOf("main") }
                // Gerät, dessen Funktionstest seit dem Öffnen der App bestanden ist.
                var geprueft by rememberSaveable { mutableStateOf<Int?>(null) }
                val schritt = Assistent.schritt(bluetoothAn, ausgaenge, geprueft, BuildConfig.ENTWICKLER)

                when {
                    schritt != Schritt.FERTIG -> {
                        val geraet = Assistent.kopfhoerer(ausgaenge)
                        AssistentScreen(
                            schritt = schritt,
                            geraetName = Geraete.kopfhoerer(this)?.productName?.toString(),
                            geraetId = geraet?.id,
                            onBluetoothEin = ::bluetoothEinschalten,
                            onKoppeln = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                            onTestBestanden = { geprueft = it },
                        )
                    }
                    screen == "settings" -> SettingsScreen(
                        vm = vm,
                        oberflaeche = AppSettings(this).oberflaeche,
                        onOberflaeche = {
                            AppSettings(this).oberflaeche = it
                            recreate() // lädt die Texte in der neuen Sprache, die Einstellungsseite bleibt offen
                        },
                        onDone = { screen = "main" },
                    )
                    screen == "history" -> HistoryScreen(vm = vm, onBack = { screen = "main" })
                    else -> TranslationScreen(
                        vm = vm,
                        onOpenSettings = { screen = "settings" },
                        onOpenHistory = { screen = "history" },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        geraeteLesen()
    }

    override fun onDestroy() {
        (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(geraeteWache)
        unregisterReceiver(bluetoothWache)
        super.onDestroy()
    }

    private fun geraeteLesen() {
        bluetoothAn = Geraete.bluetoothAn(this)
        ausgaenge = Geraete.ausgaenge(this)
    }

    /** Systemdialog «Bluetooth einschalten»; ohne Berechtigung dafür die Bluetooth-Einstellungen. */
    private fun bluetoothEinschalten() {
        runCatching { bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            .onFailure { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        permLauncher.launch(perms.toTypedArray())
    }
}
