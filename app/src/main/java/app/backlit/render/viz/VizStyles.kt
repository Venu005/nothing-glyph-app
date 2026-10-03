package app.backlit.render.viz

object VizStyles {
    val ids = listOf("mirror", "peaks")
    private val labels = mapOf("mirror" to "MIRROR", "peaks" to "PEAKS")

    /** Unknown ids (including the removed "wave" style) fall back to the first style. */
    fun normalize(id: String): String = if (id in ids) id else ids.first()

    fun label(id: String): String = labels.getValue(normalize(id))

    fun next(id: String): String = ids[(ids.indexOf(normalize(id)) + 1) % ids.size]

    fun create(id: String, size: Int): VizStyle = when (normalize(id)) {
        "peaks" -> MirrorPeaks(size)
        else -> MirrorBars(size)
    }
}
