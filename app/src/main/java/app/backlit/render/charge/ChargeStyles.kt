package app.backlit.render.charge

object ChargeStyles {
    const val DEFAULT = "moon"
    val all: List<ChargeStyle> = listOf(SproutStyle, BuddyStyle, NumberStyle, MoonStyle)

    fun byId(id: String): ChargeStyle = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT }

    fun next(id: String): String {
        val i = all.indexOfFirst { it.id == id }
        return if (i < 0) all.first().id else all[(i + 1) % all.size].id
    }
}
