package jp.kyoto.sync.parley.net

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sätze, die ankommen, während Gemini (neu) verbindet, dürfen nicht verloren gehen (Test:
 * 50 s keine Übersetzung). Erst nach `setupComplete` geht etwas raus, in der richtigen Reihenfolge.
 */
class ReadyQueueTest {

    private val sent = mutableListOf<String>()
    private val q = ReadyQueue<String> { sent += it }

    @Test
    fun heldUntilTheConnectionIsReadyThenSentInOrder() {
        q.offer("eins")
        q.offer("zwei")
        assertEquals(emptyList<String>(), sent)
        q.ready()
        assertEquals(listOf("eins", "zwei"), sent)
    }

    @Test
    fun goesStraightOutWhileReady() {
        q.ready()
        q.offer("drei")
        assertEquals(listOf("drei"), sent)
    }

    @Test
    fun aDroppedConnectionHoldsAgainUntilTheNextReady() {
        q.ready()
        q.notReady()
        q.offer("vier")
        assertEquals(emptyList<String>(), sent)
        q.ready()
        assertEquals(listOf("vier"), sent)
    }

    @Test
    fun keepsOnlyTheNewestWhenFlooded() {
        val small = ReadyQueue<Int>(max = 3) { sent += it.toString() }
        (1..5).forEach(small::offer)
        small.ready()
        assertEquals(listOf("3", "4", "5"), sent)
    }
}
