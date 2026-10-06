package app.backlit.ui.home

import app.backlit.data.Settings

enum class ToyId(val key: String, val label: String) {
    CLOCK("clock", "CLOCK"),
    MUSIC("music", "MUSIC"),
    CHARGE("charge", "CHARGE"),
    CANVAS("canvas", "CANVAS"),
    PET("pet", "PET"),
    SAND("sand", "SAND"),
    BADGE("badge", "BADGE");

    companion object {
        fun byKey(key: String): ToyId? = entries.firstOrNull { it.key == key }
    }
}

/** The Glyph toys the app shows, in grid / pager order, and whether each has been on the Glyph at least once. */
object ToyCatalog {
    /** Music needs a live (non-AOD) matrix, so the Phone (4a) Pro hides it. */
    fun visible(hideMusic: Boolean): List<ToyId> = ToyId.entries.filter { !(hideMusic && it == ToyId.MUSIC) }

    fun isSetUp(s: Settings, id: ToyId): Boolean = when (id) {
        ToyId.CLOCK -> s.clockToyEverBound
        ToyId.MUSIC -> s.musicToyEverBound
        ToyId.CHARGE -> s.chargeToyEverBound
        ToyId.CANVAS -> s.canvasToyEverBound
        ToyId.PET -> s.petToyEverBound
        ToyId.SAND -> s.sandToyEverBound
        ToyId.BADGE -> s.badgeToyEverBound
    }

    fun setUpCount(s: Settings, ids: List<ToyId>): Int = ids.count { isSetUp(s, it) }
}
