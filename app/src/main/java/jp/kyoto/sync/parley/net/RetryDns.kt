package jp.kyoto.sync.parley.net

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Namensauflösung mit Wiederholung: Auf dem Testhandy scheiterte `api.mistral.ai` zeitweise («No address
 * associated with hostname»), Sekunden davor und danach ging es. Ohne Wiederholung stoppt das die ganze App.
 */
class RetryDns(
    private val delegate: Dns = Dns.SYSTEM,
    private val versuche: Int = 3,
    private val pauseMs: Long = 700,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        var letzter: UnknownHostException? = null
        repeat(versuche) { i ->
            try {
                return delegate.lookup(hostname)
            } catch (e: UnknownHostException) {
                letzter = e
                if (i < versuche - 1) Thread.sleep(pauseMs)
            }
        }
        throw letzter!!
    }
}
