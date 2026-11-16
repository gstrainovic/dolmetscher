package jp.kyoto.sync.parley.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lautstärke für leise Sprache (PCM16 mono, little-endian): Flüstern kommt auf dem Handy mit Pegel 60 bis 270 an,
 * normales Sprechen mit 460 bis 1200. Verstärkt hörte Gemini aus Flüster-Aufnahmen Mundart, unverstärkt
 * kaum etwas; WhisperJAV nennt Lautheitsausgleich als Hilfe bei sehr leisen Aufnahmen.
 */
object Lautheit {
    /** Rund −3 dB unter Vollaussteuerung. */
    const val ZIEL_SPITZE = 23_000

    /** Ganzen Satz so verstärken, dass die lauteste Stelle [ZIEL_SPITZE] erreicht. */
    fun normalisiert(pcm: ByteArray): ByteArray {
        val spitze = samples(pcm).maxOfOrNull { abs(it) } ?: 0
        if (spitze == 0) return pcm
        return verstaerkt(pcm, ZIEL_SPITZE.toFloat() / spitze)
    }

    /** Fester Faktor, mit Begrenzung statt Überlauf. */
    fun verstaerkt(pcm: ByteArray, faktor: Float): ByteArray {
        val out = ByteBuffer.allocate(pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples(pcm)) {
            out.putShort((s * faktor).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
        return out.array()
    }

    private fun samples(pcm: ByteArray): IntArray {
        val b = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(pcm.size / 2) { b.getShort(it * 2).toInt() }
    }
}
