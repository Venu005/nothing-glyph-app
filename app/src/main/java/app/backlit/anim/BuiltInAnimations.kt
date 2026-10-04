package app.backlit.anim

object BuiltInAnimations {
    const val DEFAULT_CONTACT = "builtin:heart"
    const val DEFAULT_DEVICE = "builtin:link"

    val all: List<GlyphAnimation> = listOf(Heartbeat, SmileyWink, Ringing, Burst, Link, Bounce)

    fun byId(id: String): GlyphAnimation? = all.firstOrNull { it.id == id }
}
