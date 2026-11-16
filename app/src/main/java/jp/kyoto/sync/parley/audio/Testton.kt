package jp.kyoto.sync.parley.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Funktionstest des Assistenten: ein kurzer Ton, der nur im Kopfhörer erklingt, und eine Mikrofonprobe, die auf
 * eine Stimme wartet. Der Ton geht nie an Lautsprecher oder Hörkapsel des Handys: Ist kein Kopfhörer verbunden,
 * wird nichts abgespielt.
 */
object Testton {

    private const val RATE = 24_000

    /** Halbe Sekunde 660 Hz, mit Hann-Fenster, damit es nicht knackt. */
    fun pcm(rate: Int = RATE): ShortArray {
        val n = rate / 2
        return ShortArray(n) { i ->
            val fenster = sin(PI * i / n).let { it * it }
            (6_000 * fenster * sin(2 * PI * 660 * i / rate)).toInt().toShort()
        }
    }

    /** Spricht jemand? RMS über 300 (normales Sprechen in Handynähe liegt deutlich darüber, Stille bei 0 bis 60). */
    fun stimme(samples: ShortArray, anzahl: Int = samples.size): Boolean {
        if (anzahl <= 0) return false
        var summe = 0.0
        for (i in 0 until anzahl) summe += samples[i].toDouble() * samples[i]
        return sqrt(summe / anzahl) > 300
    }

    /** Spielt den Ton im Kopfhörer; false, wenn keiner verbunden ist (dann kein Ton). */
    fun spiele(ctx: Context): Boolean {
        val ziel = Geraete.kopfhoerer(ctx) ?: return false
        val pcm = pcm()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        if (!track.setPreferredDevice(ziel)) {
            track.release()
            return false
        }
        track.write(pcm, 0, pcm.size)
        // Kurz vor dem Abspielen nochmals prüfen: Hat sich der Kopfhörer eben getrennt, schweigen.
        if (Geraete.kopfhoerer(ctx)?.id != ziel.id) {
            track.release()
            return false
        }
        track.play()
        thread(isDaemon = true) {
            Thread.sleep(800)
            track.release()
        }
        return true
    }

    /**
     * Hört über das Mikrofon, bis eine Stimme kommt, und ruft dann [beiStimme] (im Hintergrund-Thread) einmal auf.
     * Gibt eine Funktion zum Beenden zurück; null ohne Mikrofonrecht.
     */
    fun hoereZu(ctx: Context, beiStimme: () -> Unit): (() -> Unit)? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate),
            )
        }.getOrNull() ?: return null
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null
        }
        val laeuft = java.util.concurrent.atomic.AtomicBoolean(true)
        rec.startRecording()
        thread(isDaemon = true) {
            val puffer = ShortArray(rate / 10)
            try {
                while (laeuft.get()) {
                    val n = rec.read(puffer, 0, puffer.size)
                    if (n > 0 && stimme(puffer, n)) {
                        beiStimme()
                        break
                    }
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
        }
        return { laeuft.set(false) }
    }
}
