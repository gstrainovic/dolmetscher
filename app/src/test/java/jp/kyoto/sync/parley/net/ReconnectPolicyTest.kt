package jp.kyoto.sync.parley.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReconnectPolicyTest {

    @Test
    fun aDroppedConnectionIsRebuiltWithGrowingWaits() {
        val p = ReconnectPolicy(maxInRow = 3)
        assertEquals(500L, p.nextDelayMs())
        assertEquals(1000L, p.nextDelayMs())
        assertEquals(2000L, p.nextDelayMs())
    }

    @Test
    fun givesUpAfterTooManyFailuresInARow() {
        val p = ReconnectPolicy(maxInRow = 2)
        p.nextDelayMs(); p.nextDelayMs()
        assertNull(p.nextDelayMs())
    }

    @Test
    fun aWorkingConnectionResetsTheCount() {
        val p = ReconnectPolicy(maxInRow = 2)
        p.nextDelayMs(); p.nextDelayMs()
        p.connected()
        assertEquals(500L, p.nextDelayMs())
    }

    @Test
    fun noReconnectAfterTheUserStopped() {
        val p = ReconnectPolicy(maxInRow = 5)
        p.stop()
        assertNull(p.nextDelayMs())
        assertFalse(p.active)
    }
}
