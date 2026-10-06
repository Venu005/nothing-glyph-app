package app.backlit.ui

/** Turns the markdown privacy policy into plain paragraphs for the in-app screen (no wrapping breaks, no marks). */
object PolicyText {
    fun paragraphs(markdown: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        fun flush() { if (current.isNotBlank()) out += clean(current.toString().trim()); current.clear() }
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flush()
                line.startsWith("# ") -> flush()                       // the page title is the screen's header
                line.startsWith("- ") -> { flush(); current.append("• ").append(line.removePrefix("- ")) }
                else -> { if (current.isNotEmpty()) current.append(' '); current.append(line.trim()) }
            }
        }
        flush()
        return out
    }

    private fun clean(s: String): String = s.replace("**", "").replace(Regex("^_(.*)_$"), "$1")
}
