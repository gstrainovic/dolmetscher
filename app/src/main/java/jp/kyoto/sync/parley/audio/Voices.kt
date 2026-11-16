package jp.kyoto.sync.parley.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import jp.kyoto.sync.parley.core.Lang
import jp.kyoto.sync.parley.net.AzureProtocol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Liest Text vor und liefert PCM16 mono 24 kHz (blockierend, aus dem Arbeitsfaden der Kette). */
interface SpeechOut {
    /** Vor dem ersten Satz, etwa um eine Live-Sitzung aufzubauen. */
    fun connect() {}
    fun speak(text: String): ByteArray
    fun close() {}
}

/** Azure Neural TTS per REST. */
class AzureVoice(
    private val key: String,
    private val region: String,
    private val target: Lang,
    private val http: OkHttpClient,
) : SpeechOut {
    override fun speak(text: String): ByteArray {
        val req = Request.Builder().url(AzureProtocol.url(region))
            .addHeader("Ocp-Apim-Subscription-Key", key)
            .addHeader("X-Microsoft-OutputFormat", AzureProtocol.FORMAT)
            .addHeader("User-Agent", "dolmetscher-app")
            .post(AzureProtocol.ssml(target, text).toRequestBody("application/ssml+xml".toMediaType()))
            .build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Azure HTTP ${r.code}: ${r.body?.string().orEmpty().take(200)}")
            r.body?.bytes() ?: ByteArray(0)
        }
    }
}

/** Gratis-Stimme: die Sprachausgabe des Handys, über eine Datei, damit Lautstärkeregler und Brille gleich bleiben. */
class AndroidVoice(context: Context, private val target: Lang) : SpeechOut {
    private val dir = context.cacheDir
    private val ready = CountDownLatch(1)
    @Volatile private var initOk = false
    private val tts = TextToSpeech(context) { status ->
        initOk = status == TextToSpeech.SUCCESS
        ready.countDown()
    }

    override fun speak(text: String): ByteArray {
        ready.await(10, TimeUnit.SECONDS)
        check(initOk) { "Android-Sprachausgabe nicht verfügbar" }
        val locale = Locale.forLanguageTag(target.code)
        val res = tts.setLanguage(locale)
        check(res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
            "Die Sprachausgabe des Handys kann kein ${target.displayName}. Bitte in den Android-Einstellungen eine Stimme dafür laden."
        }
        val file = File(dir, "tts-${System.nanoTime()}.wav")
        val done = CountDownLatch(1)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) = done.countDown()
            @Deprecated("Android API") override fun onError(id: String?) = done.countDown()
        })
        tts.synthesizeToFile(text, null, file, file.name)
        done.await(30, TimeUnit.SECONDS)
        return try { WavPcm.to24k(file.readBytes()) } finally { file.delete() }
    }

    override fun close() {
        tts.shutdown()
    }
}

/** WAV-Datei (PCM16 mono) auf PCM16 24 kHz bringen. */
object WavPcm {
    fun to24k(wav: ByteArray): ByteArray {
        if (wav.size < 44) return ByteArray(0)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        val rate = b.getInt(24)
        val channels = b.getShort(22).toInt()
        var pos = 12
        var dataStart = 44
        var dataLen = wav.size - 44
        while (pos + 8 <= wav.size) {
            val id = String(wav, pos, 4)
            val len = b.getInt(pos + 4)
            if (id == "data") { dataStart = pos + 8; dataLen = minOf(len, wav.size - dataStart); break }
            pos += 8 + len
        }
        val n = dataLen / 2 / channels
        val src = IntArray(n) { b.getShort(dataStart + it * 2 * channels).toInt() }
        if (rate == 24_000) return pcm(src)
        val outN = (n.toLong() * 24_000 / rate).toInt()
        val out = IntArray(outN) { j ->
            val x = j.toDouble() * rate / 24_000
            val i = x.toInt().coerceAtMost(n - 1)
            val f = x - i
            (src[i] * (1 - f) + src[minOf(i + 1, n - 1)] * f).toInt()
        }
        return pcm(out)
    }

    private fun pcm(s: IntArray): ByteArray {
        val out = ByteArray(s.size * 2)
        s.forEachIndexed { i, v -> out[2 * i] = (v and 0xFF).toByte(); out[2 * i + 1] = ((v shr 8) and 0xFF).toByte() }
        return out
    }
}
