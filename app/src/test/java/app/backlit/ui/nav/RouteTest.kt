package app.backlit.ui.nav

import app.backlit.data.Settings
import app.backlit.ui.home.ToyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteTest {
    private val all = listOf(
        Route.Welcome, Route.Home, Route.Toy(ToyId.PET), Route.Studio,
        Route.Editor("import:abc", Route.Studio), Route.Editor(null, Route.Alerts), Route.Editor("import:x", Route.Toy(ToyId.CANVAS)),
        Route.Alerts, Route.Settings, Route.Location(Route.Settings), Route.Location(Route.Toy(ToyId.CLOCK)),
        Route.Privacy, Route.Setup(Route.Settings), Route.Setup(Route.Toy(ToyId.SAND)), Route.About,
    )

    @Test
    fun parentsMatchTheSpec() {
        assertNull(Route.Home.parent())
        assertEquals(Route.Home, Route.Welcome.parent())
        assertEquals(Route.Home, Route.Toy(ToyId.SAND).parent())
        assertEquals(Route.Home, Route.Studio.parent())
        assertEquals(Route.Home, Route.Alerts.parent())
        assertEquals(Route.Home, Route.Settings.parent())
        for (r in listOf(Route.Privacy, Route.About)) assertEquals(Route.Settings, r.parent())
    }

    @Test
    fun backReturnsToWhereYouCameFrom() {
        assertEquals(Route.Toy(ToyId.CLOCK), Route.Location(Route.Toy(ToyId.CLOCK)).parent())
        assertEquals(Route.Settings, Route.Location(Route.Settings).parent())
        assertEquals(Route.Alerts, Route.Editor("import:a", Route.Alerts).parent())
        assertEquals(Route.Toy(ToyId.CANVAS), Route.Editor(null, Route.Toy(ToyId.CANVAS)).parent())
        assertEquals(Route.Studio, Route.Editor(null, Route.Studio).parent())
        assertEquals(Route.Toy(ToyId.SAND), Route.Setup(Route.Toy(ToyId.SAND)).parent())
    }

    @Test
    fun everyRouteRoundTrips() {
        for (r in all) assertEquals(r, Route.restore(r.save()))
        assertEquals(Route.Home, Route.restore("garbage"))
        assertEquals(Route.Home, Route.restore("toy:nope"))
        assertEquals(Route.Editor("a:b:c", Route.Studio), Route.restore(Route.Editor("a:b:c", Route.Studio).save()))
    }

    @Test
    fun welcomeOnlyForBrandNewUsers() {
        assertEquals(Route.Welcome, Route.start(Settings(), supported = true))
        assertEquals(Route.Home, Route.start(Settings(toyEverBound = true), supported = true))   // used the clock or music before the update
        assertEquals(Route.Home, Route.start(Settings(petToyEverBound = true), supported = true))  // used only the pet
        assertEquals(Route.Home, Route.start(Settings(badgeToyEverBound = true), supported = true))
        assertEquals(Route.Home, Route.start(Settings(welcomeSeen = true), supported = true))
        assertEquals(Route.Home, Route.start(Settings(), supported = false))
    }
}
