package app.backlit.sand

/**
 * Tells the toy's own settings writes (echoing back through DataStore) from real changes made elsewhere.
 * An echo consumes that write and every older pending one, so the same value written later by the app counts as new.
 */
class EchoFilter(private val max: Int = 8) {
    private val pending = ArrayDeque<String>()

    fun record(json: String) {
        pending.addLast(json)
        while (pending.size > max) pending.removeFirst()
    }

    fun isEcho(json: String): Boolean {
        val i = pending.lastIndexOf(json)
        if (i < 0) return false
        repeat(i + 1) { pending.removeFirst() }
        return true
    }
}
