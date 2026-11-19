package jp.kyoto.sync.parley.core

import android.media.AudioDeviceInfo
import jp.kyoto.sync.parley.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die App läuft nie ohne Kopfhörer oder Brille: Fehlt das Gerät, zeigt sie den Assistenten statt der Hauptseite,
 * beim Öffnen und mitten im Betrieb. Überspringen dürfen nur Entwickler-Builds.
 */
class AssistentTest {

    private val brille = Ausgang(id = 7, typ = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
    private val lautsprecher = Ausgang(id = 1, typ = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)

    private fun schritt(
        bluetoothAn: Boolean = true,
        ausgaenge: List<Ausgang> = listOf(lautsprecher, brille),
        geprueft: Int? = brille.id,
        entwickler: Boolean = false,
        laeuft: Boolean = false,
    ) = Assistent.schritt(bluetoothAn, ausgaenge, geprueft, entwickler, laeuft)

    @Test
    fun laeuftDieUebersetzungSchonKommtDieHauptseiteOhneNeuenTest() {
        // Nach Schliessen und Wiederöffnen der App muss der Stopp-Knopf erreichbar sein.
        assertEquals(Schritt.FERTIG, schritt(geprueft = null, laeuft = true))
    }

    @Test
    fun auchBeiLaufenderUebersetzungNieOhneGeraet() {
        assertEquals(Schritt.KOPPELN, schritt(ausgaenge = listOf(lautsprecher), geprueft = null, laeuft = true))
    }

    @Test
    fun ohneBluetoothUndOhneKabelKopfhoererZuerstBluetoothEinschalten() {
        assertEquals(Schritt.BLUETOOTH, schritt(bluetoothAn = false, ausgaenge = listOf(lautsprecher), geprueft = null))
    }

    @Test
    fun bluetoothAnAberKeinGeraetVerbundenHeisstKoppeln() {
        assertEquals(Schritt.KOPPELN, schritt(ausgaenge = listOf(lautsprecher), geprueft = null))
    }

    @Test
    fun verbundenesGeraetMussErstGetestetWerden() {
        assertEquals(Schritt.TEST, schritt(geprueft = null))
    }

    @Test
    fun getestetesVerbundenesGeraetOeffnetDieHauptseite() {
        assertEquals(Schritt.FERTIG, schritt())
    }

    @Test
    fun trenntSichDasGeraetImBetriebKommtDerAssistentZurueck() {
        assertEquals(Schritt.KOPPELN, schritt(ausgaenge = listOf(lautsprecher)))
        assertEquals(Schritt.BLUETOOTH, schritt(bluetoothAn = false, ausgaenge = listOf(lautsprecher)))
    }

    /** Stopp trennt die Brille, Start verbindet sie wieder: Android vergibt eine neue Kennung, die Adresse bleibt. */
    @Test
    fun dieselbeBrilleNachNeuverbindenBrauchtKeinenNeuenTest() {
        val vorher = Ausgang(id = 7, typ = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, adresse = "AA:BB")
        val nachher = Ausgang(id = 12, typ = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, adresse = "AA:BB")
        val andere = Ausgang(id = 13, typ = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, adresse = "CC:DD")
        assertEquals(
            Schritt.FERTIG,
            Assistent.schritt(true, listOf(lautsprecher, nachher), vorher.id, false, gepruefteAdresse = "AA:BB"),
        )
        assertEquals(
            Schritt.TEST,
            Assistent.schritt(true, listOf(lautsprecher, andere), vorher.id, false, gepruefteAdresse = "AA:BB"),
        )
    }

    /** Nach dem Stopp ist die Brille getrennt; die Hauptseite bleibt, Start verbindet sie wieder. */
    @Test
    fun vonDerAppGetrennteBrilleLaesstDieHauptseiteStehen() {
        assertEquals(
            Schritt.FERTIG,
            Assistent.schritt(true, listOf(lautsprecher), null, false, brilleGetrennt = true),
        )
        // Bluetooth aus: Start kann nicht verbinden, also doch der Assistent.
        assertEquals(
            Schritt.BLUETOOTH,
            Assistent.schritt(false, listOf(lautsprecher), null, false, brilleGetrennt = true),
        )
    }

    @Test
    fun einAnderesGeraetMussNeuGetestetWerden() {
        val andere = Ausgang(id = 9, typ = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        assertEquals(Schritt.TEST, schritt(ausgaenge = listOf(lautsprecher, andere)))
    }

    @Test
    fun kabelKopfhoererGenuegenAuchOhneBluetooth() {
        val kabel = Ausgang(id = 3, typ = AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        assertEquals(Schritt.FERTIG, schritt(bluetoothAn = false, ausgaenge = listOf(lautsprecher, kabel), geprueft = 3))
    }

    @Test
    fun handylautsprecherUndHoerkapselZaehlenNichtAlsKopfhoerer() {
        val hoerkapsel = Ausgang(id = 2, typ = AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
        assertNull(Assistent.kopfhoerer(listOf(lautsprecher, hoerkapsel)))
        assertEquals(Schritt.KOPPELN, schritt(ausgaenge = listOf(lautsprecher, hoerkapsel), geprueft = 1))
    }

    @Test
    fun testtonGehtNurAnDenKopfhoererNieAnDenLautsprecher() {
        assertEquals(brille, Assistent.kopfhoerer(listOf(lautsprecher, brille)))
        assertNull(Assistent.kopfhoerer(listOf(lautsprecher)))
    }

    @Test
    fun nurEntwicklerKommenOhneGeraetZurHauptseite() {
        assertEquals(Schritt.FERTIG, schritt(bluetoothAn = false, ausgaenge = listOf(lautsprecher), entwickler = true))
    }

    @Test
    fun normalerBauIstKeinEntwicklerBau() {
        assertFalse(BuildConfig.ENTWICKLER)
    }

    @Test
    fun testGiltErstMitTonUndStimme() {
        assertFalse(Assistent.testBestanden(tonGehoert = true, stimmeGehoert = false))
        assertFalse(Assistent.testBestanden(tonGehoert = false, stimmeGehoert = true))
        assertTrue(Assistent.testBestanden(tonGehoert = true, stimmeGehoert = true))
    }
}
