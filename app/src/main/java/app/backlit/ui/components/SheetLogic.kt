package app.backlit.ui.components

/**
 * Keeps a sheet's selected key while its item is on the way, and closes it only once an item that was there is gone.
 * A just-added contact or device isn't in the list yet, and right after rotation the list starts empty (not loaded).
 */
class LiveSelection(seen: Set<String> = emptySet()) {
    private val seen = seen.toMutableSet()

    /** The key to keep (null = close the sheet). */
    fun resolve(key: String?, present: Boolean, loaded: Boolean): String? {
        if (key == null) return null
        if (present) { seen += key; return key }
        if (!loaded) return key
        return if (key in seen) null else key
    }
}

/** "1 DRAWING", "3 DRAWINGS". */
fun plural(n: Int, word: String): String = "$n $word" + if (n == 1) "" else "S"
