package jp.kyoto.sync.parley.audio

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.TenVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Sprachwahrscheinlichkeit je Rahmen von Silero VAD statt der Lautstärke: lässt laute
 * Geräusche ohne Sprache draussen; die Schwellen setzt [SpeechGate]. Nimmt die Rahmen der App (PCM16 mono 24 kHz),
 * verstärkt sie und gibt dem Modell Fenster bei 16 kHz; ein Rest wartet auf den nächsten Rahmen.
 */
class SileroSprache(
    /** Proben je Fenster bei 16 kHz: Silero 512, TEN VAD 256. */
    private val fenster: Int = 512,
    /** Verstärkung vor dem Modell, damit Flüstern als Sprache zählt. */
    private val verstaerkung: Float = 1f,
    /** Sprachwahrscheinlichkeit 0..1 für ein Fenster. */
    private val modell: (FloatArray) -> Float,
) : (ByteArray) -> Float {
    private val rest = FloatArray(fenster)
    private var restLaenge = 0

    private var hoechste = 0f

    /** Höchste Sprachwahrscheinlichkeit seit der letzten Abfrage, für das Protokoll. */
    @Synchronized
    fun hoechsteSeitAbfrage(): Float = hoechste.also { hoechste = 0f }

    /** Höchste Wahrscheinlichkeit der Fenster in diesem Rahmen. */
    @Synchronized
    override fun invoke(pcm24k: ByteArray): Float {
        val pcm16k = Downsample16k.apply(if (verstaerkung == 1f) pcm24k else Lautheit.verstaerkt(pcm24k, verstaerkung))
        var imRahmen = 0f
        for (i in 0 until pcm16k.size / 2) {
            val s = ((pcm16k[2 * i + 1].toInt() shl 8) or (pcm16k[2 * i].toInt() and 0xFF)).toShort()
            rest[restLaenge++] = s / 32768f
            if (restLaenge == fenster) {
                imRahmen = maxOf(imRahmen, modell(rest.copyOf()))
                restLaenge = 0
            }
        }
        hoechste = maxOf(hoechste, imRahmen)
        return imRahmen
    }

    companion object {
        /**
         * Silero-Modell aus den Assets (`silero_vad.onnx`, über `werkzeuge/sherpa.sh`). Unverstärkt überhört es
         * Flüstern; mit 24 dB kam der ganze geflüsterte Satz durch (`messungen/vad_simulation.py`).
         */
        fun ausAssets(assets: AssetManager, verstaerkung: Float = 16f): SileroSprache {
            val vad = Vad(assets, VadModelConfig(sileroVadModelConfig = SileroVadModelConfig(model = "silero_vad.onnx")))
            return SileroSprache(verstaerkung = verstaerkung) { vad.compute(it) }
        }

        /**
         * TEN VAD (`ten-vad.onnx`, über `werkzeuge/sherpa.sh`), von WhisperJAV für leise Sprache empfohlen; auf
         * Flüster-Stücken erkannte es nichts. Lizenz: Apache 2.0 mit der Bedingung, nicht mit Agora zu
         * konkurrieren.
         */
        fun tenAusAssets(assets: AssetManager, verstaerkung: Float = 4f): SileroSprache {
            val vad = Vad(assets, VadModelConfig(tenVadModelConfig = TenVadModelConfig(model = "ten-vad.onnx")))
            return SileroSprache(fenster = 256, verstaerkung = verstaerkung) { vad.compute(it) }
        }
    }
}
