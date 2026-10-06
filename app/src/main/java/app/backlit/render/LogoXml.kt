package app.backlit.render

import java.util.Locale

/** The launcher icon layers as Android vector drawables, generated from [LogoGeometry]. */
object LogoXml {
    fun foreground(): String = vector(mono = false)

    /** Themed-icon layer: white only (the system tints it); the ring keeps its light falloff as alpha. */
    fun monochrome(): String = vector(mono = true)

    private fun vector(mono: Boolean): String {
        val groups = LinkedHashMap<String, StringBuilder>()   // "color|alpha" → path data
        for (d in LogoGeometry.dots) {
            val color = if (d.red && !mono) "#FFD71921" else "#FFFFFFFF"
            val alpha = if (d.red) 1.0 else d.alpha
            groups.getOrPut("$color|${f(alpha)}") { StringBuilder() }
                .append("M").append(f(d.x - d.r)).append(",").append(f(d.y))
                .append("a").append(f(d.r)).append(",").append(f(d.r)).append(" 0 1,0 ").append(f(2 * d.r)).append(",0")
                .append("a").append(f(d.r)).append(",").append(f(d.r)).append(" 0 1,0 ").append(f(-2 * d.r)).append(",0")
        }
        return buildString {
            append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
            append("    android:width=\"108dp\"\n    android:height=\"108dp\"\n")
            append("    android:viewportWidth=\"108\"\n    android:viewportHeight=\"108\">\n")
            for ((key, path) in groups) {
                val (color, alpha) = key.split('|')
                append("    <path\n        android:fillColor=\"$color\"\n        android:fillAlpha=\"$alpha\"\n")
                append("        android:pathData=\"").append(path).append("\" />\n")
            }
            append("</vector>\n")
        }
    }

    private fun f(v: Double) = String.format(Locale.US, "%.2f", v)
}
