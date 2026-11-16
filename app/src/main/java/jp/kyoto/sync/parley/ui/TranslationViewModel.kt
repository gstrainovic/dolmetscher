package jp.kyoto.sync.parley.ui

import android.app.Application
import android.media.AudioManager
import jp.kyoto.sync.parley.audio.VolumeLevel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.kyoto.sync.parley.core.AppSettings
import jp.kyoto.sync.parley.core.ChatSession
import jp.kyoto.sync.parley.core.Conversation
import jp.kyoto.sync.parley.core.HistoryRepository
import jp.kyoto.sync.parley.core.Lang
import jp.kyoto.sync.parley.core.Mode
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
 * Schlüssel sind fest eingebaut ([jp.kyoto.sync.parley.core.Keys]); Einstellungen in [AppSettings],
 * Verlauf in [HistoryRepository].
 */
class TranslationViewModel(app: Application) : AndroidViewModel(app) {

    private val history = HistoryRepository(app)
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

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions

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
    val mode = SessionBus.mode
    val conversation = SessionBus.conversation
    val partnerTranscript = SessionBus.partnerTranscript
    val partnerSourceTranscript = SessionBus.partnerSourceTranscript
    val myTranscript = SessionBus.myTranscript
    val mySourceTranscript = SessionBus.mySourceTranscript
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

    fun swapDirection() {
        val c = SessionBus.conversation.value
        SessionBus.conversation.value = Conversation(myLang = c.partnerLang, partnerLang = c.myLang)
        setupNachladen()
    }

    // --- セッション制御 ---

    fun start() {
        persistCurrent()                            // 直前の会話を履歴へ
        TranslationService.start(getApplication())  // 内部で beginSession() しクリア
    }

    fun stop() {
        persistCurrent()                            // 停止時に履歴へ
        TranslationService.stop(getApplication())
    }

    /** 新しい会話: 直前を履歴へ保存してから画面をクリア。 */
    fun newConversation() {
        persistCurrent()
        SessionBus.clearCurrent()
    }

    fun pttDown() = TranslationService.setMode(getApplication(), Mode.SPEAKING)

    fun pttUp() = TranslationService.setMode(getApplication(), Mode.LISTENING)

    // --- 履歴 ---

    fun loadHistory() {
        viewModelScope.launch { _sessions.value = history.list() }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            history.delete(id)
            _sessions.value = history.list()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            history.clearAll()
            _sessions.value = emptyList()
        }
    }

    /**
     * 現在の会話を履歴へ保存する。値は同期的に読み取ってから書き込むので、
     * 直後にセッションがクリアされても取りこぼさない。空なら何もしない。
     * ID は開始時刻なので upsert により重複しない。
     */
    private fun persistCurrent() {
        val startedAt = SessionBus.currentStartedAt
        val partner = SessionBus.partnerTranscript.value
        val mine = SessionBus.myTranscript.value
        val partnerSource = SessionBus.partnerSourceTranscript.value
        val mineSource = SessionBus.mySourceTranscript.value
        val allBlank = partner.isBlank() && mine.isBlank() &&
            partnerSource.isBlank() && mineSource.isBlank()
        if (startedAt == 0L || allBlank) return
        val conv = SessionBus.conversation.value
        val session = ChatSession(
            id = startedAt.toString(),
            startedAt = startedAt,
            myLang = conv.myLang.code,
            partnerLang = conv.partnerLang.code,
            partnerText = partner,
            myText = mine,
            partnerSourceText = partnerSource,
            mySourceText = mineSource,
        )
        viewModelScope.launch {
            history.upsert(session)
            _sessions.value = history.list()
        }
    }
}
