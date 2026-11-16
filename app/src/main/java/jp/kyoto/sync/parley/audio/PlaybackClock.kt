package jp.kyoto.sync.parley.audio

/**
 * Schätzt, bis wann die bisher geschriebene Übersetzung (PCM16 mono 24 kHz) hörbar ist, plus Nachhall.
 * Der Dienst hört so lange nicht zu, damit das Mikrofon die eigene Ausgabe nicht als neuen Satz aufnimmt.
 */
class PlaybackClock(private val tailMs: Long = 400) {
    private var endMs = 0L

    @Synchronized
    fun wrote(pcm16: ByteArray, now: Long = System.currentTimeMillis()) {
        val durationMs = pcm16.size * 1000L / 48_000
        endMs = maxOf(endMs, now) + durationMs
    }

    @Synchronized
    fun busy(now: Long = System.currentTimeMillis()): Boolean = endMs > 0 && now < endMs + tailMs
}
