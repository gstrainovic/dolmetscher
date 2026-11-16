package jp.kyoto.sync.parley.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import jp.kyoto.sync.parley.R
import jp.kyoto.sync.parley.audio.AudioCapturer
import jp.kyoto.sync.parley.audio.AudioPlayer
import jp.kyoto.sync.parley.audio.Gain
import jp.kyoto.sync.parley.audio.Geraete
import jp.kyoto.sync.parley.BuildConfig
import jp.kyoto.sync.parley.audio.Mitschnitt
import jp.kyoto.sync.parley.audio.SatzAufnahme
import jp.kyoto.sync.parley.audio.SileroSprache
import jp.kyoto.sync.parley.audio.PlaybackClock
import jp.kyoto.sync.parley.audio.SpeechGate
import jp.kyoto.sync.parley.audio.Route
import jp.kyoto.sync.parley.core.AppSettings
import jp.kyoto.sync.parley.core.Keys
import jp.kyoto.sync.parley.core.Config
import jp.kyoto.sync.parley.core.Mode
import jp.kyoto.sync.parley.core.Oberflaeche
import jp.kyoto.sync.parley.core.Protokoll
import jp.kyoto.sync.parley.core.ProtokollUpload
import jp.kyoto.sync.parley.core.SessionBus
import jp.kyoto.sync.parley.core.Status
import jp.kyoto.sync.parley.net.TranslationSession
import jp.kyoto.sync.parley.net.Translator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 双方向同時通訳のフォアグラウンドサービス。
 *
 * 設計（ハウリング回避のため、まずは半二重 / プッシュトゥトーク）:
 *  - Mode.LISTENING: マイク=相手の声 → incoming(訳:自分言語) → イヤホン
 *  - Mode.SPEAKING : マイク=自分の声 → outgoing(訳:相手言語) → スピーカー
 *
 * マイクのフレームは現在の Mode のセッションだけに流すので、スピーカー出力が
 * マイクへ回り込むハウリングを構造的に防げる。
 *
 * API キーは [ApiKeyStore] から読み出し、各セッションに直接渡す（BYOK）。
 *
 * 半二重ゆえ片方向が落ちると通訳は成立しないため、致命的エラーでは [fatalError] で
 * 全リソースを解放して停止する（マイクを開いたまま放置しない）。
 */
class TranslationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .dns(jp.kyoto.sync.parley.net.RetryDns())
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket は無期限
        .build()

    private var capturer: AudioCapturer? = null
    private var protokoll: Protokoll? = null
    private var mitschnitt: Mitschnitt? = null

    /** Protokoll zusätzlich auf den eigenen Server, alle 5 s gebündelt. */
    private val upload = ProtokollUpload { zeilen ->
        val body = org.json.JSONObject().put("zeilen", org.json.JSONArray(zeilen)).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(Keys.claudeServerUrl.trimEnd('/') + "/protokoll")
            .addHeader("Authorization", "Bearer ${Keys.claudeServerToken}").post(body).build()
        http.newCall(req).execute().use { it.isSuccessful }
    }
    private var earphonePlayer: AudioPlayer? = null // incoming → 自分の耳
    private var speakerPlayer: AudioPlayer? = null   // outgoing → 相手へ

    private var incoming: Translator? = null // 相手→自分
    private var outgoing: Translator? = null // 自分→相手

    /** Trennt sich Kopfhörer oder Brille, hält die Übersetzung an; die App zeigt dann den Assistenten. */
    private val geraeteWache = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (!BuildConfig.ENTWICKLER && !Geraete.hatKopfhoerer(this@TranslationService)) {
                protokoll?.fehler("Kopfhörer getrennt, Übersetzung angehalten")
                stopSelf()
            }
        }
    }
    private var wacheAktiv = false

    override fun onBind(intent: Intent?): IBinder? = null

    // Meldungen und Benachrichtigung in der Sprache der Oberfläche.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Oberflaeche.kontext(newBase))

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stopSelf()
            ACTION_SET_MODE -> {
                val m = intent.getStringExtra(EXTRA_MODE)?.let { Mode.valueOf(it) } ?: Mode.LISTENING
                SessionBus.mode.value = m
            }
            // intent == null（START_NOT_STICKY 後の異常再起動など）や未知の action は何もしない。
        }
        // リアルタイム通訳はマイクとユーザー操作が前提。OS に勝手に再起動させない。
        return START_NOT_STICKY
    }

    private fun start() {
        val s = SessionBus.status.value
        if (s == Status.RUNNING || s == Status.CONNECTING) return

        // マイク権限が無ければフォアグラウンド(microphone)開始前に弾く（Android 14 で必須）。
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            reportError(IllegalStateException(getString(R.string.error_no_mic_permission)))
            stopSelf()
            return
        }

        // Nie ohne Kopfhörer oder Brille übersetzen (der Assistent lässt es nicht zu, hier zur Sicherheit nochmals).
        if (!BuildConfig.ENTWICKLER && !Geraete.hatKopfhoerer(this)) {
            reportError(IllegalStateException(getString(R.string.error_no_headset)))
            stopSelf()
            return
        }

        startForegroundCompat()
        if (!wacheAktiv) {
            (getSystemService(AUDIO_SERVICE) as AudioManager)
                .registerAudioDeviceCallback(geraeteWache, Handler(Looper.getMainLooper()))
            wacheAktiv = true
        }
        SessionBus.beginSession()
        SessionBus.status.value = Status.CONNECTING

        scope.launch {
            try {
                teardown() // 念のため前回分が残っていれば解放してから組み直す。

                val setup = AppSettings(this@TranslationService).setupFuer(SessionBus.conversation.value.partnerLang)
                val missing = Keys.missing(setup)
                if (missing.isNotEmpty()) {
                    fatalError(IllegalStateException(getString(R.string.error_missing_keys, missing.joinToString(", "))))
                    return@launch
                }

                val transcriptionModel = AppSettings(this@TranslationService).transcriptionModel.id
                val conv = SessionBus.conversation.value
                val wiedergabe = PlaybackClock()
                val log = Protokoll(java.io.File(filesDir, "protokoll.txt"), weiter = upload::add).also { protokoll = it }
                scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        delay(5_000)
                        upload.flush()
                    }
                }
                log.start("${setup.encode()} ${conv.partnerLang.code}->${conv.incomingTarget.code}")
                // Untertitel kommen stückweise; ins Protokoll ganze Zeilen.
                val eingang = StringBuilder()
                val ausgang = StringBuilder()
                fun zeilen(puffer: StringBuilder, stueck: String, schreib: (String) -> Unit) = synchronized(puffer) {
                    puffer.append(stueck)
                    while (true) {
                        val i = puffer.indexOf("\n")
                        if (i < 0) break
                        puffer.substring(0, i).takeIf { it.isNotBlank() }?.let(schreib)
                        puffer.delete(0, i + 1)
                    }
                }

                earphonePlayer = AudioPlayer(this@TranslationService, Route.EARPHONE).apply { start() }
                speakerPlayer = AudioPlayer(this@TranslationService, Route.SPEAKER).apply { start() }

                incoming = Translator.incoming(
                    setup = setup,
                    context = this@TranslationService,
                    target = conv.incomingTarget,
                    source = conv.partnerLang,
                    transcriptionModel = transcriptionModel,
                    http = http,
                    listener = object : TranslationSession.Listener {
                        override fun onTranslatedAudio(pcm16: ByteArray) {
                            // Übersetzung des Gegenübers in die Kopfhörer, mit Verstärkung aus der App
                            earphonePlayer?.write(Gain.apply(pcm16, SessionBus.earphoneGain.value))
                            wiedergabe.wrote(pcm16)
                            log.ton()
                        }

                        override fun onOutputTranscript(text: String) {
                            SessionBus.appendPartnerTranscript(text)
                            zeilen(ausgang, text, log::uebersetzt)
                        }

                        override fun onInputTranscript(text: String) {
                            SessionBus.appendPartnerSource(text) // 相手の原文
                            zeilen(eingang, text, log::erkannt)
                        }

                        override fun onError(t: Throwable) = fatalError(t)
                    },
                    trace = log::info,
                ).also { it.connect() }

                outgoing = Translator.outgoing(
                    setup = setup,
                    target = conv.outgoingTarget,
                    source = conv.myLang,
                    transcriptionModel = transcriptionModel,
                    http = http,
                    listener = object : TranslationSession.Listener {
                        override fun onTranslatedAudio(pcm16: ByteArray) {
                            speakerPlayer?.write(pcm16) // 自分の訳はスピーカーへ
                            wiedergabe.wrote(pcm16)
                        }

                        override fun onOutputTranscript(text: String) {
                            SessionBus.appendMyTranscript(text)
                        }

                        // 自分の原文は表示しない: 出力（訳）音声がスピーカーから流れ、本体マイクへ
                        // 回り込んで（エコー）原文へ混入するため（例:「こんにちは。Guten Tag.」）。
                        // 相手方向はイヤホン出力なのでエコーしにくく、原文を表示する。
                        override fun onError(t: Throwable) = fatalError(t)
                    },
                ).also { it.connect() }

                // マイク取り込み開始。現在の Mode に応じて送り先を切り替える。
                // Beim Zuhören nur Ton schicken, wenn jemand spricht (Kosten).
                // Aufnahmen nur in Testversionen: sie enthalten echte Gespräche.
                val aufnahme = SatzAufnahme(java.io.File(filesDir, "aufnahmen")).takeIf { BuildConfig.DEBUG }
                // Silero mit 24 dB Verstärkung, Schwellen 0.3 und 0.15 in SpeechGate (messungen/vad_simulation.py).
                val silero = runCatching { SileroSprache.ausAssets(assets) }
                    .onSuccess { log.info("Sprecherkennung: Silero VAD, 24 dB verstärkt, Start 0.3, Halten 0.15") }
                    .onFailure { log.fehler("Silero VAD nicht geladen, Pegelschwelle: ${it.message}") }
                    .getOrNull()
                val gate = SpeechGate(
                    sprache = silero,
                    onSend = {
                        aufnahme?.add(it)
                        incoming?.sendAudio(it)
                    },
                    onEnd = {
                        aufnahme?.satzEnde()?.let { log.info("Aufnahme $it") }
                        log.satzende(nachlaufMs = SpeechGate.HANGOVER_FRAMES * Config.CHUNK_MS.toLong())
                        incoming?.endOfSpeech()
                    },
                    trace = { zeile ->
                        log.info(zeile + (silero?.let { ", VAD max %.2f".format(it.hoechsteSeitAbfrage()) } ?: ""))
                    },
                )
                val roh = Mitschnitt(java.io.File(filesDir, "aufnahmen/mitschnitt.pcm"))
                    .takeIf { BuildConfig.DEBUG }.also { mitschnitt = it }
                capturer = AudioCapturer(
                    onFrame = { frame ->
                        roh?.add(frame)
                        when (SessionBus.mode.value) {
                            // Über den Handylautsprecher nimmt das Mikrofon die eigene Übersetzung auf
                            // (Echo im Test): dann während der Wiedergabe nicht zuhören. Mit Brille
                            // oder Kopfhörer weiter zuhören, sonst geht der Rest des Satzes verloren, wenn
                            // das Gegenüber weiterspricht; ein Echo fängt dort EchoFilter am Text ab.
                            Mode.LISTENING ->
                                if (!wiedergabe.busy() || earphonePlayer?.headset() == true) gate.feed(frame)
                            Mode.SPEAKING -> outgoing?.sendAudio(frame)
                        }
                    },
                    onError = { t -> fatalError(t) },
                ).also { it.start() }

                SessionBus.status.value = Status.RUNNING
            } catch (t: Throwable) {
                fatalError(t)
            }
        }
    }

    /** エラーを通知し、全リソースを解放してサービスを止める（onDestroy で teardown）。 */
    private fun fatalError(t: Throwable) {
        protokoll?.fehler(t.message ?: t.toString())
        reportError(t)
        stopSelf()
    }

    private fun reportError(t: Throwable) {
        SessionBus.errorMessage.value = t.message ?: t.toString()
        SessionBus.status.value = Status.ERROR
    }

    /** 取り込み・再生・セッションを全て停止し参照を破棄する（多重 start でのリーク防止）。 */
    private fun teardown() {
        capturer?.stop(); capturer = null
        mitschnitt?.close(); mitschnitt = null
        incoming?.close(); incoming = null
        outgoing?.close(); outgoing = null
        earphonePlayer?.stop(); earphonePlayer = null
        speakerPlayer?.stop(); speakerPlayer = null
    }

    private fun startForegroundCompat() {
        val channelId = "parley_translation"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_running))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        if (wacheAktiv) {
            (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(geraeteWache)
            wacheAktiv = false
        }
        teardown()
        scope.cancel()
        Thread { upload.flush() }.start() // letzte Zeilen (Fehler, Stopp) nicht verlieren
        runCatching {
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
        // エラー終了時は ERROR を保持し、通常停止のみ IDLE に戻す。
        if (SessionBus.status.value != Status.ERROR) SessionBus.status.value = Status.IDLE
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1
        const val ACTION_START = "jp.kyoto.sync.parley.START"
        const val ACTION_STOP = "jp.kyoto.sync.parley.STOP"
        const val ACTION_SET_MODE = "jp.kyoto.sync.parley.SET_MODE"
        const val EXTRA_MODE = "mode"

        fun start(ctx: Context) = ctx.startForegroundService(
            Intent(ctx, TranslationService::class.java).setAction(ACTION_START),
        )

        fun stop(ctx: Context) = ctx.startService(
            Intent(ctx, TranslationService::class.java).setAction(ACTION_STOP),
        )

        fun setMode(ctx: Context, mode: Mode) = ctx.startService(
            Intent(ctx, TranslationService::class.java)
                .setAction(ACTION_SET_MODE)
                .putExtra(EXTRA_MODE, mode.name),
        )
    }
}
