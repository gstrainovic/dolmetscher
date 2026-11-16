package jp.kyoto.sync.parley.core

import android.annotation.SuppressLint
import android.content.Context

/**
 * 入力文字起こし（原文字幕）に使うモデル。[id] は OpenAI のモデル名。
 *
 * - REALTIME_WHISPER: 低遅延かつ CJK 安全（漢字等のマルチバイト文字がストリーミング配信で
 *   壊れない）。原文字幕の既定。
 * - MINI: 低コスト。ただしバイト単位 BPE のため CJK がストリーミング中に文字化け（U+FFFD）し得る。
 * - FULL: 最高精度。難しい音声向け（MINI 同様 CJK 文字化けの可能性）。
 * - DIARIZE: 話者識別付き（1対1では基本不要）。
 *
 * いずれも翻訳音声・訳文字幕を生成する gpt-realtime-translate とは別の、原文の文字起こし用。
 */
enum class TranscriptionModel(val id: String) {
    REALTIME_WHISPER("gpt-realtime-whisper"),
    MINI("gpt-4o-mini-transcribe"),
    FULL("gpt-4o-transcribe"),
    DIARIZE("gpt-4o-transcribe-diarize"),
    ;

    companion object {
        val DEFAULT = REALTIME_WHISPER
        fun fromId(id: String?): TranscriptionModel = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * API キー以外の軽量設定を端末内 SharedPreferences に保存する。
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("parley_settings", Context.MODE_PRIVATE)

    var transcriptionModel: TranscriptionModel
        get() = TranscriptionModel.fromId(prefs.getString(KEY_TRANSCRIPTION, null))
        set(value) {
            prefs.edit().putString(KEY_TRANSCRIPTION, value.id).apply()
        }

    /** Gewählte Kombination aus Erkennung, Übersetzung und Stimme, je Sprache des Gegenübers. */
    fun setupFuer(partner: Lang): Setup =
        Setup.decode(prefs.getString("${KEY_SETUP}_${partner.code}", null), Setup.vorschlag(partner))

    fun setzeSetup(partner: Lang, value: Setup) {
        prefs.edit().putString("${KEY_SETUP}_${partner.code}", value.encode()).apply()
    }

    /** Sprache der Oberfläche; ohne Wahl Serbisch ([Oberflaeche]). Sofort gespeichert, die Activity lädt danach neu. */
    var oberflaeche: Oberflaeche
        get() = Oberflaeche.aus(prefs.getString(KEY_OBERFLAECHE, null))
        @SuppressLint("ApplySharedPref") // commit: recreate() liest die Wahl gleich danach
        set(value) {
            prefs.edit().putString(KEY_OBERFLAECHE, value.code).commit()
        }

    /** Verstärkung der App über 100 % Medienlautstärke (siehe VolumeLevel). */
    var earphoneGain: Float
        get() = prefs.getFloat(KEY_GAIN, 1.0f)
        set(value) {
            prefs.edit().putFloat(KEY_GAIN, value).apply()
        }

    companion object {
        private const val KEY_TRANSCRIPTION = "transcription_model"
        private const val KEY_GAIN = "earphone_gain"
        private const val KEY_SETUP = "setup"
        private const val KEY_OBERFLAECHE = "oberflaeche"
    }
}
