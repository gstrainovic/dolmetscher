package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Funktionstest im Assistenten: kurzer, leiser Ton nur im Kopfhörer, und das Mikrofon muss eine Stimme hören. */
class TesttonTest {

    @Test
    fun tonDauertEineHalbeSekundeUndSetztWeichEinUndAus() {
        val pcm = Testton.pcm(rate = 24_000)
        assertEquals(12_000, pcm.size)
        assertEquals(0, pcm.first().toInt())
        assertTrue(abs(pcm.last().toInt()) < 50)
        assertTrue(pcm.maxOf { abs(it.toInt()) } in 3_000..12_000)
    }

    @Test
    fun stilleIstKeineStimme() {
        assertFalse(Testton.stimme(ShortArray(1600) { (it % 3 - 1).toShort() }))
    }

    @Test
    fun normalesSprechenIstEineStimme() {
        assertTrue(Testton.stimme(ShortArray(1600) { if (it % 2 == 0) 900 else -900 }))
    }
}
