package app.backlit.alerts

/** Matches the caller name a dialer shows against a picked contact name. Exact after normalising. */
object NameMatch {
    private val BIDI = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
    private val SPACES = Regex("\\s+")

    fun normalize(s: String): String = s.replace(BIDI, "").trim().replace(SPACES, " ").lowercase()

    fun matches(a: String, b: String): Boolean {
        val na = normalize(a)
        return na.isNotEmpty() && na == normalize(b)
    }
}
