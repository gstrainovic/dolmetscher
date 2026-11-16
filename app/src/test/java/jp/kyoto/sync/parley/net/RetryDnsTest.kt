package jp.kyoto.sync.parley.net

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

/** Ein kurzer DNS-Aussetzer (Testhandy: «Unable to resolve host api.mistral.ai») stoppt die App nicht. */
class RetryDnsTest {

    private val adresse = InetAddress.getByAddress("api.mistral.ai", byteArrayOf(172.toByte(), 66, 2, 203.toByte()))

    private fun fehltErst(mal: Int): Dns {
        var versuche = 0
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (versuche++ < mal) throw UnknownHostException("No address associated with hostname") else listOf(adresse)
        }
    }

    @Test
    fun aShortDnsDropoutIsBridged() {
        assertEquals(listOf(adresse), RetryDns(fehltErst(2), pauseMs = 0).lookup("api.mistral.ai"))
    }

    @Test(expected = UnknownHostException::class)
    fun aLastingFailureIsStillReported() {
        RetryDns(fehltErst(10), versuche = 3, pauseMs = 0).lookup("api.mistral.ai")
    }
}
