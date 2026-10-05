package app.backlit.render

import java.util.Locale

/**
 * Renders a frame as a Nothing-style Glyph Toys picker image: a black disc with every LED as a dot —
 * dim grey when off, white (alpha by brightness) when lit. Output is an Android vector drawable.
 */
object ToyPreviewXml {
    private const val VIEW = 96.0
    private const val MARGIN = 5.0
    private const val OFF = "#FF2E2E2E"

    fun vector(grid: PixelGrid): String {
        val n = grid.size
        val cell = (VIEW - 2 * MARGIN) / n
        val r = cell * 0.36
        val groups = sortedMapOf<Int, StringBuilder>()        // brightness → path data (0 = off)
        for (y in 0 until n) for (x in 0 until n) {
            if (!grid.hasLed(x, y)) continue
            val cx = MARGIN + (x + 0.5) * cell
            val cy = MARGIN + (y + 0.5) * cell
            groups.getOrPut(grid[x, y]) { StringBuilder() }
                .append("M").append(f(cx - r)).append(",").append(f(cy))
                .append("a").append(f(r)).append(",").append(f(r)).append(" 0 1,0 ").append(f(2 * r)).append(",0")
                .append("a").append(f(r)).append(",").append(f(r)).append(" 0 1,0 ").append(f(-2 * r)).append(",0")
        }
        return buildString {
            append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
            append("    android:width=\"96dp\"\n    android:height=\"96dp\"\n")
            append("    android:viewportWidth=\"96\"\n    android:viewportHeight=\"96\">\n")
            append("    <path\n        android:fillColor=\"#FF000000\"\n        android:pathData=\"M0,48a48,48 0 1,0 96,0a48,48 0 1,0 -96,0z\" />\n")
            for ((v, path) in groups) {
                append("    <path\n")
                if (v == 0) append("        android:fillColor=\"$OFF\"\n")
                else append("        android:fillColor=\"#FFFFFFFF\"\n        android:fillAlpha=\"${f(0.35 + 0.65 * v / 255.0)}\"\n")
                append("        android:pathData=\"").append(path).append("\" />\n")
            }
            append("</vector>\n")
        }
    }

    private fun f(v: Double) = String.format(Locale.US, "%.2f", v)
}
