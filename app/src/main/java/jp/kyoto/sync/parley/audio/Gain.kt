package jp.kyoto.sync.parley.audio

/**
 * Verstärkung für PCM16 mono (little-endian). Macht leise Brillen-Lautsprecher lauter, als
 * es die Medienlautstärke des Handys allein erlaubt; Spitzen werden abgeschnitten statt
 * überzulaufen.
 */
object Gain {
    fun apply(pcm16: ByteArray, gain: Float): ByteArray {
        if (gain == 1.0f) return pcm16
        val out = ByteArray(pcm16.size)
        var i = 0
        while (i + 1 < pcm16.size) {
            val s = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort().toInt()
            val v = (s * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = (v and 0xFF).toByte()
            out[i + 1] = ((v shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }
}
