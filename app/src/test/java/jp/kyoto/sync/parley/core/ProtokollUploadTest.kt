package jp.kyoto.sync.parley.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Das Laufprotokoll geht auch an den eigenen Server, damit es ohne adb lesbar ist. */
class ProtokollUploadTest {

    private val angekommen = mutableListOf<String>()
    private var netzDa = true
    private val upload = ProtokollUpload(maxZeilen = 3) { zeilen ->
        if (netzDa) angekommen += zeilen
        netzDa
    }

    @Test
    fun linesArriveOnTheServerInOrder() {
        upload.add("START")
        upload.add("SATZENDE")
        upload.flush()
        upload.add("ERKANNT Hallo")
        upload.flush()
        assertEquals(listOf("START", "SATZENDE", "ERKANNT Hallo"), angekommen)
    }

    @Test
    fun withoutNetworkNothingIsLostAndItArrivesLater() {
        netzDa = false
        upload.add("START")
        upload.flush()
        netzDa = true
        upload.add("SATZENDE")
        upload.flush()
        assertEquals(listOf("START", "SATZENDE"), angekommen)
    }

    @Test
    fun longWithoutNetworkKeepsOnlyTheNewestLines() {
        netzDa = false
        listOf("a", "b", "c", "d").forEach(upload::add)
        upload.flush()
        netzDa = true
        upload.flush()
        assertEquals(listOf("b", "c", "d"), angekommen)
    }

    @Test
    fun nothingNewSendsNothing() {
        var aufrufe = 0
        val leer = ProtokollUpload { aufrufe++; true }
        leer.flush()
        assertEquals(0, aufrufe)
    }
}
