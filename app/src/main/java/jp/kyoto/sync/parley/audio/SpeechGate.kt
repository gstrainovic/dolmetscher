package jp.kyoto.sync.parley.audio

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lässt Ton nur durch, wenn jemand spricht, damit Stille nichts kostet.
 *
 * Arbeitet mit Rahmen von 100 ms (PCM16 mono). Sprache heisst: lauter als das Dreifache des
 * Hintergrundpegels, der sich langsam an gleichmässigen Lärm anpasst. Geöffnet wird nach zwei
 * lauten Rahmen hintereinander (ein Klick öffnet nicht); dann gehen die halbe Sekunde davor und
 * alles Weitere durch. Nach [HANGOVER_FRAMES] ruhigen Rahmen schliesst das Tor und meldet
 * [onEnd]; die durchgelassene Ruhe lässt auch die Spracherkennung des Anbieters das Satzende
 * erkennen.
 */
class SpeechGate(
    private val onSend: (ByteArray) -> Unit,
    private val onEnd: () -> Unit,
    /** Alle 5 s lautester Pegel, Hintergrund und Zustand, für die Fehlersuche am Gerät. */
    private val trace: (String) -> Unit = {},
    /**
     * Sprachwahrscheinlichkeit 0..1 in diesem Rahmen: Silero VAD ([SileroSprache]). Öffnen ab
     * [VAD_START], offen bleiben bis unter [VAD_HALTEN]. Ohne Erkenner gilt die Pegelschwelle unten; sie verlor
     * geflüsterte Sätze.
     */
    private val sprache: ((ByteArray) -> Float)? = null,
) {
    private var statFrames = 0
    private var statMax = 0.0
    private val recent = ArrayDeque<ByteArray>()
    private var noiseFloor = -1.0
    private var loudInRow = 0
    private var open = false
    private var quietInRow = 0

    fun feed(frame: ByteArray) {
        val level = rms(frame)
        if (noiseFloor < 0) noiseFloor = level
        val p = sprache?.invoke(frame)
        val pegelLaut = level > max(ABS_MIN, noiseFloor * FACTOR)
        val loud = if (p == null) pegelLaut else p >= if (open) VAD_HALTEN else VAD_START
        statMax = max(statMax, level)
        if (++statFrames >= 50) {
            trace("Pegel max ${statMax.toInt()}, Hintergrund ${noiseFloor.toInt()}, Schwelle ${max(ABS_MIN, noiseFloor * FACTOR).toInt()}, Tor ${if (open) "offen" else "zu"}")
            statFrames = 0
            statMax = 0.0
        }

        if (open) {
            onSend(frame)
            quietInRow = if (loud) 0 else quietInRow + 1
            if (quietInRow >= HANGOVER_FRAMES) {
                open = false
                quietInRow = 0
                loudInRow = 0
                onEnd()
            }
            return
        }

        if (loud) {
            loudInRow++
            if (loudInRow >= OPEN_AFTER_FRAMES) {
                open = true
                recent.forEach(onSend)
                recent.clear()
                onSend(frame)
                return
            }
        } else {
            loudInRow = 0
        }
        // Hintergrundpegel nur bei geschlossenem Tor nachführen, langsam auch nach oben.
        noiseFloor = noiseFloor * (1 - ADAPT) + level * ADAPT
        recent.addLast(frame)
        while (recent.size > PREROLL_FRAMES + OPEN_AFTER_FRAMES - 1) recent.removeFirst()
    }

    private fun rms(pcm: ByteArray): Double {
        var sum = 0.0
        val n = pcm.size / 2
        for (i in 0 until n) {
            val s = ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)).toShort().toDouble()
            sum += s * s
        }
        return if (n == 0) 0.0 else sqrt(sum / n)
    }

    companion object {
        const val PREROLL_FRAMES = 5
        const val OPEN_AFTER_FRAMES = 2
        /**
         * 0.6 s Ruhe beendet einen Satz; kürzere Atempausen bleiben im Satz. Jede
         * Zehntelsekunde mehr verzögert die Übersetzung, weniger zerlegt Sätze bei Atempausen.
         */
        const val HANGOVER_FRAMES = 6

        /**
         * Ein geflüsterter Satz im Mitschnitt: Silero mit 24 dB Verstärkung 0.16 bis 0.99 im Satz, höchstens 0.04 in der
         * Stille; mit Start 0.3 und Halten 0.15 (Silero `neg_threshold` = Start − 0.15) kam der ganze Satz durch
         * (`messungen/vad_simulation.py`).
         */
        const val VAD_START = 0.3f
        const val VAD_HALTEN = 0.15f
        private const val FACTOR = 3.0
        /**
         * Auch leises Reden und Flüstern soll durchkommen. Testhandy: normales
         * Sprechen Pegel 200 bis 330, ruhiges Zimmer 0 bis 60; das Dreifache des Hintergrunds gilt zusätzlich.
         */
        private const val ABS_MIN = 60.0
        private const val ADAPT = 0.05
    }
}
