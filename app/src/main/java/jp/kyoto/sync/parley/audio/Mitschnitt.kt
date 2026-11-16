package jp.kyoto.sync.parley.audio

import java.io.File
import java.io.FileOutputStream

/**
 * Schneidet den ganzen Mikrofonton einer Sitzung mit (PCM16 mono 16 kHz, ohne Kopf), höchstens [maxSekunden],
 * damit sich die Sprecherkennung mit echten Aufnahmen auf dem Laptop einstellen lässt. Holen:
 * `adb exec-out run-as ch.strainovic.dolmetscher cat files/aufnahmen/mitschnitt.pcm`. Enthält echte Gespräche:
 * vor dem Einsatz abschalten.
 */
class Mitschnitt(datei: File, maxSekunden: Int = 600) {
    private val max = maxSekunden * 32_000L
    private var geschrieben = 0L
    private val out = runCatching { datei.parentFile?.mkdirs(); FileOutputStream(datei) }.getOrNull()

    @Synchronized
    fun add(pcm24k: ByteArray) {
        val o = out ?: return
        if (geschrieben >= max) return
        val pcm = Downsample16k.apply(pcm24k)
        val n = minOf(pcm.size.toLong(), max - geschrieben).toInt()
        runCatching { o.write(pcm, 0, n) }
        geschrieben += n
    }

    @Synchronized
    fun close() {
        runCatching { out?.close() }
    }
}
