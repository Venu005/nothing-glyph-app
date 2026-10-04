package app.backlit.alerts

/** Matches the caller name a dialer shows against a picked contact name. Exact after normalising. */
object NameMatch {
    private val BIDI = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
    private val SPACES = Regex("\\s+")

    fun normalize(s: String): String = s.replace(BIDI, "").trim().replace(SPACES, " ").lowercase()

    /** True when [name] appears in [text] as a whole word or phrase (e.g. "Mom (2)" contains "Mom", "Momo" doesn't). */
    fun containsName(text: String, name: String): Boolean {
        val n = normalize(name)
        if (n.isEmpty()) return false
        return Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(n) + "($|[^\\p{L}\\p{N}])").containsMatchIn(normalize(text))
    }

    fun matches(a: String, b: String): Boolean {
        val na = normalize(a)
        return na.isNotEmpty() && na == normalize(b)
    }
}
