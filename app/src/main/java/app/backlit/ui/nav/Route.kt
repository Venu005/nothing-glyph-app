package app.backlit.ui.nav

import app.backlit.ui.home.ToyCatalog
import app.backlit.ui.home.ToyId

/**
 * Where the app is. Saved as a short string so rotation and process death restore the same screen.
 * Screens you can reach from more than one place (the editor, sun-times location, setup) remember where you came
 * from, so Back returns there.
 */
sealed interface Route {
    data object Welcome : Route
    data object Home : Route
    data class Toy(val id: ToyId) : Route
    data object Studio : Route
    data class Editor(val drawingId: String?, val from: Route) : Route
    data object Alerts : Route
    data object Settings : Route
    data class Location(val from: Route) : Route
    data object Privacy : Route
    data class Setup(val from: Route) : Route
    data object About : Route

    /** One level up; null on Home (Back leaves the app). */
    fun parent(): Route? = when (this) {
        Home -> null
        Welcome, is Toy, Studio, Alerts, Settings -> Home
        is Editor -> from
        is Location -> from
        is Setup -> from
        Privacy, About -> Settings
    }

    fun save(): String = when (this) {
        Welcome -> "welcome"
        Home -> "home"
        is Toy -> "toy:${id.key}"
        Studio -> "studio"
        is Editor -> "editor|${from.save()}|${drawingId ?: ""}"
        Alerts -> "alerts"
        Settings -> "settings"
        is Location -> "location|${from.save()}"
        Privacy -> "privacy"
        is Setup -> "setup|${from.save()}"
        About -> "about"
    }

    companion object {
        // Built on use: a companion property here could initialise before the data objects (class-init cycle).
        private fun simple(): List<Route> = listOf(Welcome, Home, Studio, Alerts, Settings, Privacy, About)

        fun restore(s: String): Route {
            val parts = s.split('|', limit = 3)
            return when (parts[0]) {
                "editor" -> if (parts.size == 3) Editor(parts[2].ifEmpty { null }, restore(parts[1])) else Home
                "location" -> if (parts.size >= 2) Location(restore(parts[1])) else Home
                "setup" -> if (parts.size >= 2) Setup(restore(parts[1])) else Home
                else -> when {
                    s.startsWith("toy:") -> ToyId.byKey(s.removePrefix("toy:"))?.let { Toy(it) } ?: Home
                    else -> simple().firstOrNull { it.save() == s } ?: Home
                }
            }
        }

        /** Welcome only for a brand-new user on a Glyph phone: anyone who already used any toy skips it. */
        fun start(settings: app.backlit.data.Settings, supported: Boolean): Route {
            val usedAToy = settings.toyEverBound || ToyCatalog.setUpCount(settings, ToyId.entries) > 0
            return if (supported && !settings.welcomeSeen && !usedAToy) Welcome else Home
        }
    }
}
