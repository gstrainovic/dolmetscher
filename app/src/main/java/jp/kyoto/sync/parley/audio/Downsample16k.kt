package jp.kyoto.sync.parley.audio

/**
 * PCM16 mono von 24 kHz auf 16 kHz (Suisse Speech verlangt 16 kHz). Glättet zuerst mit einem
 * kurzen Tiefpass [1, 2, 1]/4 und rechnet dann linear auf jede 1.5. Probe um; für Sprache genügt das.
 */
object Downsample16k {
    fun apply(pcm24: ByteArray): ByteArray {
        val n = pcm24.size / 2
        val s = IntArray(n) { ((pcm24[2 * it + 1].toInt() shl 8) or (pcm24[2 * it].toInt() and 0xFF)).toShort().toInt() }
        val f = IntArray(n) { i -> (s[maxOf(i - 1, 0)] + 2 * s[i] + s[minOf(i + 1, n - 1)]) / 4 }
        val outN = n * 2 / 3
        val out = ByteArray(outN * 2)
        for (j in 0 until outN) {
            val x = j * 1.5
            val i = x.toInt()
            val frac = x - i
            val v = (f[i] * (1 - frac) + f[minOf(i + 1, n - 1)] * frac).toInt()
            out[2 * j] = (v and 0xFF).toByte()
            out[2 * j + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }
}
