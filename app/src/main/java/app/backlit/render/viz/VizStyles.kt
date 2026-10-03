package app.backlit.render.viz

object VizStyles {
    val ids = listOf("mirror", "peaks", "wave")
    private val labels = mapOf("mirror" to "MIRROR", "peaks" to "PEAKS", "wave" to "WAVE")

    fun normalize(id: String): String = if (id in ids) id else ids.first()

    fun label(id: String): String = labels.getValue(normalize(id))

    fun next(id: String): String = ids[(ids.indexOf(normalize(id)) + 1) % ids.size]

    fun create(id: String, size: Int): VizStyle = when (normalize(id)) {
        else -> MirrorBars(size)
    }
}
