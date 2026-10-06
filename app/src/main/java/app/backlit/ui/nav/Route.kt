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
    /** Studio, remembering where it was opened from (Home, or the Canvas page). */
    data class Studio(val from: Route = Home) : Route
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
        Welcome, is Toy, Alerts, Settings -> Home
        is Studio -> from
        is Editor -> from
        is Location -> from
        is Setup -> from
        Privacy, About -> Settings
    }

    fun save(): String = when (this) {
        Welcome -> "welcome"
        Home -> "home"
        is Toy -> "toy:${id.key}"
        is Studio -> "studio|${from.save()}"
        // The origin is always last: it may itself contain "|" (e.g. Studio(from = …)).
        is Editor -> "editor|${drawingId ?: ""}|${from.save()}"
        Alerts -> "alerts"
        Settings -> "settings"
        is Location -> "location|${from.save()}"
        Privacy -> "privacy"
        is Setup -> "setup|${from.save()}"
        About -> "about"
    }

    companion object {
        // Built on use: a companion property here could initialise before the data objects (class-init cycle).
        private fun simple(): List<Route> = listOf(Welcome, Home, Alerts, Settings, Privacy, About)

        fun restore(s: String): Route {
            val head = s.substringBefore('|')
            val rest = s.substringAfter('|', missingDelimiterValue = "")
            return when (head) {
                "editor" -> {
                    if (!s.contains('|') || !rest.contains('|')) Home
                    else Editor(rest.substringBefore('|').ifEmpty { null }, restore(rest.substringAfter('|')))
                }
                "location" -> if (rest.isEmpty()) Home else Location(restore(rest))
                "setup" -> if (rest.isEmpty()) Home else Setup(restore(rest))
                "studio" -> Studio(if (rest.isEmpty()) Home else restore(rest))
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
