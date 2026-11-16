package jp.kyoto.sync.parley.net

/**
 * Sammelt eine stückweise gelieferte Übersetzung und gibt jeden Satz frei, sobald der nächste beginnt
 * (Satzende wie in [Saetze]); den letzten Satz liefert [finish].
 */
class SatzStrom {
    private val buf = StringBuilder()

    fun feed(chunk: String): List<String> {
        buf.append(chunk)
        val teile = Saetze.split(buf.toString())
        if (teile.size <= 1) return emptyList()
        val fertig = teile.dropLast(1)
        buf.setLength(0)
        buf.append(teile.last())
        return fertig
    }

    fun finish(): List<String> {
        val rest = buf.toString().trim()
        buf.setLength(0)
        return if (rest.isEmpty()) emptyList() else Saetze.split(rest)
    }
}
