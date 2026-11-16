package jp.kyoto.sync.parley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Nur Ton schicken, wenn jemand spricht. Ein Rahmen = 100 ms bei 24 kHz. */
class SpeechGateTest {

    private val sent = mutableListOf<ByteArray>()
    private var ends = 0
    private val gate = SpeechGate(onSend = { sent += it }, onEnd = { ends++ })

    private var tag = 0
    /** Rahmen mit Sinuston der Lautstärke [amp]; das erste Sample trägt eine Kennung zum Wiederfinden. */
    private fun frame(amp: Int): ByteArray {
        val n = 2400
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            val s = (amp * sin(2 * PI * 220 * i / 24_000)).toInt()
            out[2 * i] = (s and 0xFF).toByte()
            out[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        out[0] = (tag++ and 0xFF).toByte()
        return out
    }

    private fun feed(count: Int, amp: Int): List<ByteArray> = List(count) { frame(amp).also(gate::feed) }

    @Test
    fun silenceCostsNothing() {
        feed(300, amp = 20)
        assertEquals(0, sent.size)
    }

    /** Testhandy: normales Sprechen erreicht Pegel 200 bis 330, ein ruhiges Zimmer 0 bis 60. */
    @Test
    fun normalSpeechOnPhoneGetsThrough() {
        feed(20, amp = 20)
        val speech = feed(10, amp = 300) // Pegel (RMS) rund 210
        assertTrue(sent.containsAll(speech))
    }

    /** Auch leises Reden und Flüstern kommt durch. */
    @Test
    fun quietSpeechGetsThroughInAQuietRoom() {
        feed(20, amp = 20)
        val speech = feed(10, amp = 100) // Pegel (RMS) rund 70
        assertTrue(sent.containsAll(speech))
    }

    /** Silero VAD entscheidet, ob gesprochen wird, nicht die Lautstärke. */
    @Test
    fun withAVoiceDetectorAWhisperBelowTheLevelThresholdGetsThrough() {
        var p = 0.02f
        val g = SpeechGate(onSend = { sent += it }, onEnd = { ends++ }, sprache = { p })
        repeat(20) { g.feed(frame(5)) }
        p = 0.9f
        val fluestern = List(10) { frame(30).also(g::feed) } // weit unter der Pegelschwelle
        assertTrue(sent.containsAll(fluestern))
    }

    @Test
    fun withAVoiceDetectorLoudNoiseWithoutSpeechStaysOut() {
        val g = SpeechGate(onSend = { sent += it }, onEnd = { ends++ }, sprache = { 0.04f })
        repeat(30) { g.feed(frame(9000)) } // Geschirr, Türe
        assertEquals(0, sent.size)
    }

    /**
     * Ein geflüsterter Satz im Mitschnitt: Silero lag am Anfang bei 0.9, danach nur noch bei 0.16 bis 0.34, und
     * die App gab nur die ersten 4 s weiter. Offen bleibt das Tor bis unter die zweite Schwelle (Silero
     * `neg_threshold`, Start − 0.15), wie in `messungen/vad_simulation.py` nachgerechnet.
     */
    @Test
    fun aWhisperGettingQuieterInTheSentenceKeepsTheGateOpen() {
        var p = 0.02f
        val g = SpeechGate(onSend = { sent += it }, onEnd = { ends++ }, sprache = { p })
        repeat(10) { g.feed(frame(5)) }
        p = 0.9f
        repeat(5) { g.feed(frame(30)) }
        p = 0.2f
        val leiser = List(30) { frame(30).also(g::feed) }
        assertTrue(sent.containsAll(leiser))
        assertEquals(0, ends)
        p = 0.05f
        repeat(10) { g.feed(frame(5)) }
        assertEquals(1, ends)
    }

    @Test
    fun aQuietWhisperOpensFromThreeTenths() {
        val g = SpeechGate(onSend = { sent += it }, onEnd = { ends++ }, sprache = { 0.34f })
        repeat(5) { g.feed(frame(30)) }
        assertTrue(sent.isNotEmpty())
    }

    @Test
    fun theStartOfASentenceIsNotCutOff() {
        val quiet = feed(20, amp = 20)
        val speech = feed(10, amp = 6000)
        // Die halbe Sekunde vor dem Sprechbeginn geht mit, dann die ganze Rede.
        assertTrue(sent.containsAll(quiet.takeLast(5)))
        assertTrue(sent.containsAll(speech))
    }

    @Test
    fun afterTheSentenceSixTenthsOfASecondOfQuietFollowThenItStops() {
        // 0.6 s statt 1 s, die Übersetzung beginnt früher.
        feed(20, amp = 20)
        feed(10, amp = 6000)
        val after = feed(40, amp = 20)
        assertTrue(sent.containsAll(after.take(6)))
        assertTrue(after.drop(6).none { it in sent })
        assertEquals(1, ends)
    }

    @Test
    fun aSingleClickDoesNotOpen() {
        feed(20, amp = 20)
        feed(1, amp = 8000)
        feed(20, amp = 20)
        assertEquals(0, sent.size)
    }

    @Test
    fun steadyBackgroundNoiseIsIgnoredButLouderSpeechGetsThrough() {
        feed(100, amp = 1500) // Lüftung, Fernseher im Hintergrund
        val noiseSent = sent.size
        val speech = feed(10, amp = 9000)
        assertTrue("Hintergrund geschickt: $noiseSent Rahmen", noiseSent <= 3)
        assertTrue(sent.containsAll(speech))
    }

    @Test
    fun pausesWithinASentenceDoNotCutIt() {
        feed(20, amp = 20)
        val a = feed(5, amp = 6000)
        val pause = feed(5, amp = 20) // 0.5 s Atempause
        val b = feed(5, amp = 6000)
        assertTrue(sent.containsAll(a + pause + b))
        assertEquals(0, ends)
    }
}
