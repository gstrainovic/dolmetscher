package jp.kyoto.sync.parley.audio

import jp.kyoto.sync.parley.net.VoxtralProtocol
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Speichert jeden Satz, den die Sprecherkennung an die Erkennung schickt, als WAV (16 kHz mono) in [ordner],
 * damit sich echte Aufnahmen später mit allen Erkennern vergleichen lassen. Behält die neuesten [max] Dateien.
 * Holen: `adb exec-out run-as ch.strainovic.dolmetscher tar c files/aufnahmen | tar x`.
 */
class SatzAufnahme(
    private val ordner: File,
    private val max: Int = 200,
    private val uhr: () -> Long = System::currentTimeMillis,
) {
    private val ton = java.io.ByteArrayOutputStream()
    private val format = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)

    /** PCM16 mono 24 kHz, wie es an die Erkennung geht. */
    @Synchronized
    fun add(pcm24k: ByteArray) = ton.write(pcm24k)

    /** Schreibt den Satz und gibt den Dateinamen zurück; ohne Ton nichts. */
    @Synchronized
    fun satzEnde(): String? {
        if (ton.size() == 0) return null
        val pcm = ton.toByteArray()
        ton.reset()
        return runCatching {
            ordner.mkdirs()
            val name = "${format.format(Date(uhr()))}.wav"
            File(ordner, name).writeBytes(VoxtralProtocol.wav16k(Downsample16k.apply(pcm)))
            ordner.listFiles()!!.filter { it.name.endsWith(".wav") }.sortedBy { it.name }.dropLast(max).forEach { it.delete() }
            name
        }.getOrNull()
    }
}
