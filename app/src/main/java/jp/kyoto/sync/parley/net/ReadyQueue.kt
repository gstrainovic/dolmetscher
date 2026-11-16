package jp.kyoto.sync.parley.net

/**
 * Hält Nachrichten zurück, solange die Verbindung nicht bereit ist (Aufbau, Neuverbindung), und schickt sie
 * danach in der richtigen Reihenfolge. Höchstens [max] Nachrichten, die ältesten fallen weg.
 */
class ReadyQueue<T>(private val max: Int = 20, private val send: (T) -> Unit) {
    private val pending = ArrayDeque<T>()
    private var isReady = false

    @Synchronized
    fun offer(item: T) {
        if (isReady) {
            send(item)
            return
        }
        pending.addLast(item)
        while (pending.size > max) pending.removeFirst()
    }

    @Synchronized
    fun ready() {
        isReady = true
        while (pending.isNotEmpty()) send(pending.removeFirst())
    }

    @Synchronized
    fun notReady() {
        isReady = false
    }
}
