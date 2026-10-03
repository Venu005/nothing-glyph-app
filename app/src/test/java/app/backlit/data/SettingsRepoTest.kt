package app.backlit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepoTest {

    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun repo() = SettingsRepo(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { tmp.root.resolve("s.preferences_pb") })
    )

    @After fun tearDown() = scope.cancel()

    @Test
    fun defaultsWhenEmpty() = runBlocking {
        assertEquals(Settings(), repo().settings.first())
    }

    @Test
    fun roundTripsEveryField() = runBlocking {
        val r = repo()
        val s = Settings(
            faceId = "dayring", secondHand = false, brightness = 40, use24h = false,
            locationMode = LocationMode.CITY, lat = 12.97, lon = 77.59, placeName = "Bengaluru, IN",
            locationUpdatedAt = 123L, toyEverBound = true,
        )
        r.update { s }
        assertEquals(s, r.settings.first())
    }

    @Test
    fun clearingCoordinatesRemovesThem() = runBlocking {
        val r = repo()
        r.update { it.copy(lat = 1.0, lon = 2.0) }
        r.update { it.copy(lat = null, lon = null) }
        val s = r.settings.first()
        assertNull(s.lat)
        assertNull(s.lon)
    }

    @Test
    fun unknownLocationModeFallsBackToFixed() = runBlocking {
        val r = repo()
        r.update { it.copy(locationMode = LocationMode.APPROXIMATE) }
        assertEquals(LocationMode.APPROXIMATE, r.settings.first().locationMode)
        assertEquals(LocationMode.FIXED, SettingsRepo.parseMode("NOT_A_MODE"))
    }
}
