package jp.kyoto.sync.parley.ui

import android.app.Application
import android.media.AudioManager
import jp.kyoto.sync.parley.BuildConfig
import jp.kyoto.sync.parley.R
import jp.kyoto.sync.parley.audio.Brille
import jp.kyoto.sync.parley.audio.Geraete
import jp.kyoto.sync.parley.audio.VolumeLevel
import jp.kyoto.sync.parley.core.Status
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.kyoto.sync.parley.core.AppSettings
import jp.kyoto.sync.parley.core.Conversation
import jp.kyoto.sync.parley.core.Lang
import jp.kyoto.sync.parley.core.SessionBus
import jp.kyoto.sync.parley.core.Setup
import jp.kyoto.sync.parley.core.TranscriptionModel
import jp.kyoto.sync.parley.service.TranslationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 画面の状態は [SessionBus] の StateFlow をそのまま公開し、
 * 操作は [TranslationService] への Intent として送る。
 * Schlüssel sind fest eingebaut ([jp.kyoto.sync.parley.core.Keys]); Einstellungen in [AppSettings].
 * Gespräche werden nicht gespeichert ([jp.kyoto.sync.parley.core.Datenschutz]).
 */
class TranslationViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = AppSettings(app)

    private val _setup = MutableStateFlow(settings.setupFuer(SessionBus.conversation.value.partnerLang))
    /** Gewählte Kombination aus Erkennung, Übersetzung und Stimme für die Sprache des Gegenübers. */
    val setup: StateFlow<Setup> = _setup

    fun setSetup(s: Setup) {
        settings.setzeSetup(SessionBus.conversation.value.partnerLang, s)
        _setup.value = s
    }

    /** Nach einem Sprachwechsel gilt die Kette der neuen Sprache des Gegenübers. */
    private fun setupNachladen() {
        _setup.value = settings.setupFuer(SessionBus.conversation.value.partnerLang)
    }

    private val _transcriptionModel = MutableStateFlow(settings.transcriptionModel)
    val transcriptionModel: StateFlow<TranscriptionModel> = _transcriptionModel

    private val audio = app.getSystemService(AudioManager::class.java)
    private val maxMedia = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    init {
        SessionBus.earphoneGain.value = settings.earphoneGain
    }

    private val _volume = MutableStateFlow(currentVolume())
    /** Ein Regler 0..3: bis 1 Medienlautstärke des Handys, darüber Verstärkung der App. */
    val volume: StateFlow<Float> = _volume

    private fun currentVolume() = VolumeLevel.from(
        mediaSteps = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        maxSteps = maxMedia,
        gain = SessionBus.earphoneGain.value,
    )

    fun setVolume(level: Float) {
        val s = VolumeLevel.split(level, maxMedia)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, s.mediaSteps, 0)
        SessionBus.earphoneGain.value = s.gain
        settings.earphoneGain = s.gain
        _volume.value = level.coerceIn(0f, VolumeLevel.MAX)
    }

    /** Lautstärketasten am Handy verändern die Medienlautstärke; Regler beim Anzeigen nachführen. */
    fun refreshVolume() {
        _volume.value = currentVolume()
    }

    val status = SessionBus.status
    val conversation = SessionBus.conversation
    val partnerTranscript = SessionBus.partnerTranscript
    val partnerSourceTranscript = SessionBus.partnerSourceTranscript
    val errorMessage = SessionBus.errorMessage

    // --- 設定 ---

    fun setTranscriptionModel(model: TranscriptionModel) {
        settings.transcriptionModel = model
        _transcriptionModel.value = model
    }

    // --- 言語選択 ---

    /** 自分の言語を変更。相手と同じになる場合は相手を元の自分言語へ入れ替えて重複を避ける。 */
    fun setMyLang(lang: Lang) {
        val c = SessionBus.conversation.value
        if (lang == c.myLang) return
        val newPartner = if (lang == c.partnerLang) c.myLang else c.partnerLang
        SessionBus.conversation.value = Conversation(myLang = lang, partnerLang = newPartner)
        setupNachladen()
    }

    /** 相手の言語を変更。自分と同じになる場合は自分を元の相手言語へ入れ替えて重複を避ける。 */
    fun setPartnerLang(lang: Lang) {
        val c = SessionBus.conversation.value
        if (lang == c.partnerLang) return
        val newMy = if (lang == c.myLang) c.partnerLang else c.myLang
        SessionBus.conversation.value = Conversation(myLang = newMy, partnerLang = lang)
        setupNachladen()
    }

    // --- セッション制御 ---

    private var anlauf: Job? = null

    /**
     * Hat Stopp die Brille getrennt, verbindet Start sie zuerst wieder (bis [BRILLE_WARTEN_MS]) und startet dann den
     * Dienst; fehlt sie danach, meldet der Dienst den fehlenden Kopfhörer und der Assistent erscheint.
     */
    fun start() {
        val app = getApplication<Application>()
        if (BuildConfig.ENTWICKLER || Geraete.hatKopfhoerer(app) || !Brille.getrennt.value) {
            TranslationService.start(app) // 内部で beginSession() しクリア
            return
        }
        SessionBus.status.value = Status.CONNECTING
        anlauf = viewModelScope.launch {
            Brille.verbinden(app)
            val bis = System.currentTimeMillis() + BRILLE_WARTEN_MS
            while (!Geraete.hatKopfhoerer(app) && System.currentTimeMillis() < bis) delay(250)
            Brille.vergessen(app)
            SessionBus.status.value = Status.IDLE
            // Ohne Gerät nie den Dienst starten: er hielte vor startForeground an, und Android beendet dann die App.
            if (Geraete.hatKopfhoerer(app)) {
                TranslationService.start(app)
            } else {
                SessionBus.errorMessage.value = app.getString(R.string.error_no_headset)
                SessionBus.status.value = Status.ERROR
            }
        }
    }

    /** Stopp beendet die Sitzung: Text weg, Brille getrennt (im Dienst, auch beim Stopp aus der Benachrichtigung). */
    fun stop() {
        if (anlauf?.isActive == true) {
            anlauf?.cancel()
            SessionBus.status.value = Status.IDLE
        }
        TranslationService.stop(getApplication())
    }

    private companion object {
        const val BRILLE_WARTEN_MS = 10_000L
    }

    /** Nach einem Abbruch (Fehler, Kopfhörer getrennt) den Text vom Bildschirm nehmen. */
    fun newConversation() = SessionBus.clearCurrent()
}
