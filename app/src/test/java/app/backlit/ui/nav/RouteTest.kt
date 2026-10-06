package app.backlit.ui.nav

import app.backlit.data.Settings
import app.backlit.ui.home.ToyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteTest {
    private val all = listOf(
        Route.Welcome, Route.Home, Route.Toy(ToyId.PET), Route.Studio, Route.Editor("import:abc"), Route.Editor(null),
        Route.Alerts, Route.Settings, Route.Location, Route.Privacy, Route.Setup, Route.About,
    )

    @Test
    fun parentsMatchTheSpec() {
        assertNull(Route.Home.parent())
        assertEquals(Route.Home, Route.Welcome.parent())
        assertEquals(Route.Home, Route.Toy(ToyId.SAND).parent())
        assertEquals(Route.Home, Route.Studio.parent())
        assertEquals(Route.Home, Route.Alerts.parent())
        assertEquals(Route.Home, Route.Settings.parent())
        assertEquals(Route.Studio, Route.Editor(null).parent())
        for (r in listOf(Route.Location, Route.Privacy, Route.Setup, Route.About)) assertEquals(Route.Settings, r.parent())
    }

    @Test
    fun everyRouteRoundTrips() {
        for (r in all) assertEquals(r, Route.restore(r.save()))
        assertEquals(Route.Home, Route.restore("garbage"))
        assertEquals(Route.Home, Route.restore("toy:nope"))
        assertEquals(Route.Editor("a:b:c"), Route.restore(Route.Editor("a:b:c").save()))
    }

    @Test
    fun welcomeOnlyForBrandNewUsers() {
        assertEquals(Route.Welcome, Route.start(Settings(), supported = true))
        assertEquals(Route.Home, Route.start(Settings(toyEverBound = true), supported = true))   // used a toy before the update
        assertEquals(Route.Home, Route.start(Settings(welcomeSeen = true), supported = true))
        assertEquals(Route.Home, Route.start(Settings(), supported = false))
    }
}
