package jp.kyoto.sync.parley.net

/**
 * Erkennt Sätze, die nur das Echo der eigenen Übersetzung sind: Stimmen mindestens [share] der Wörter
 * (ab 3 Buchstaben) mit dem überein, was die App in den letzten [windowMs] gesprochen hat, wird der Satz
 * verworfen. So muss das Mikrofon nicht stumm geschaltet werden, während das Gegenüber weiterredet.
 */
class EchoFilter(
    private val windowMs: Long = 20_000,
    private val share: Double = 0.6,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val recent = ArrayDeque<Pair<Long, Set<String>>>()

    @Synchronized
    fun spoken(text: String) {
        recent.addLast(clock() to words(text))
        prune()
    }

    @Synchronized
    fun isEcho(recognized: String): Boolean {
        prune()
        val w = words(recognized)
        if (w.size < 3) return false
        val heard = recent.flatMap { it.second }.toSet()
        return w.count { it in heard }.toDouble() / w.size >= share
    }

    private fun prune() {
        val limit = clock() - windowMs
        while (recent.isNotEmpty() && recent.first().first < limit) recent.removeFirst()
    }

    private fun words(t: String): Set<String> =
        t.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length >= 3 }.toSet()
}
