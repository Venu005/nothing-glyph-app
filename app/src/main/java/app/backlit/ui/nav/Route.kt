package app.backlit.ui.nav

import app.backlit.ui.home.ToyId

/** Where the app is. Saved as a short string so rotation and process death restore the same screen. */
sealed interface Route {
    data object Welcome : Route
    data object Home : Route
    data class Toy(val id: ToyId) : Route
    data object Studio : Route
    data class Editor(val drawingId: String?) : Route
    data object Alerts : Route
    data object Settings : Route
    data object Location : Route
    data object Privacy : Route
    data object Setup : Route
    data object About : Route

    /** One level up; null on Home (Back leaves the app). */
    fun parent(): Route? = when (this) {
        Home -> null
        Welcome, is Toy, Studio, Alerts, Settings -> Home
        is Editor -> Studio
        Location, Privacy, Setup, About -> Settings
    }

    fun save(): String = when (this) {
        is Toy -> "toy:${id.key}"
        is Editor -> "editor:${drawingId ?: ""}"
        else -> this::class.simpleName!!.lowercase()
    }

    companion object {
        fun restore(s: String): Route = when {
            s.startsWith("toy:") -> ToyId.byKey(s.removePrefix("toy:"))?.let { Toy(it) } ?: Home
            s.startsWith("editor:") -> Editor(s.removePrefix("editor:").ifEmpty { null })
            else -> listOf(Welcome, Home, Studio, Alerts, Settings, Location, Privacy, Setup, About)
                .firstOrNull { it.save() == s } ?: Home
        }

        /** Welcome only for a brand-new user on a Glyph phone (anyone who already used a toy skips it). */
        fun start(settings: app.backlit.data.Settings, supported: Boolean): Route =
            if (supported && !settings.welcomeSeen && !settings.toyEverBound) Welcome else Home
    }
}
