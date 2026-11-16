package jp.kyoto.sync.parley.net

/**
 * Wann eine getrennte Verbindung neu aufgebaut wird: Wartezeit verdoppelt sich ab 0.5 s, nach
 * [maxInRow] Fehlschlägen in Folge wird aufgegeben. Eine funktionierende Verbindung setzt zurück.
 */
class ReconnectPolicy(private val maxInRow: Int = 5) {
    private var failures = 0

    @Volatile
    var active = true
        private set

    /** Wartezeit bis zum nächsten Versuch, oder null: aufgeben. */
    fun nextDelayMs(): Long? {
        if (!active || failures >= maxInRow) return null
        return 500L shl failures++
    }

    fun connected() {
        failures = 0
    }

    fun stop() {
        active = false
    }
}
