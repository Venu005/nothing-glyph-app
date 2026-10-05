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
            musicStyle = "wave", musicSensitivity = Sensitivity.HIGH,
            chargeStyle = "buddy", chargeTarget = 85, chargePlugInAnim = "import:abc", chargeDoneAnim = "import:def",
            chargeToyEverBound = true,
            canvasDrawingId = "import:q", canvasToyEverBound = true,
            petName = "Casper", petMood = 42.5f, petMoodAt = 123_456L, petSleepStart = 22, petSleepEnd = 6, petToyEverBound = true,
            petKind = "owl", petNames = mapOf("owl" to "Professor", "frog" to "Kermie"),
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

    @Test
    fun unknownSensitivityFallsBackToMed() {
        assertEquals(Sensitivity.MED, SettingsRepo.parseSensitivity("LOUD"))
        assertEquals(Sensitivity.LOW, SettingsRepo.parseSensitivity("LOW"))
        assertEquals("mirror", Settings().musicStyle)
    }

    @Test
    fun chargeDefaults() = runBlocking {
        val s = repo().settings.first()
        assertEquals("moon", s.chargeStyle)
        assertEquals(100, s.chargeTarget)
        assertEquals("", s.chargePlugInAnim)
        assertEquals("", s.chargeDoneAnim)
        assertEquals(false, s.chargeToyEverBound)
    }

    @Test
    fun chargeTargetIsClampedToFivePercentSteps() {
        assertEquals(50, SettingsRepo.clampTarget(10))
        assertEquals(100, SettingsRepo.clampTarget(140))
        assertEquals(85, SettingsRepo.clampTarget(86))
        assertEquals(90, SettingsRepo.clampTarget(88))
    }

    @Test
    fun outOfRangeStoredTargetReadsClamped() = runBlocking {
        val r = repo()
        r.update { it.copy(chargeTarget = 7) }
        assertEquals(50, r.settings.first().chargeTarget)
    }

    @Test
    fun canvasDefaults() = runBlocking {
        val s = repo().settings.first()
        assertEquals("", s.canvasDrawingId)
        assertEquals(false, s.canvasToyEverBound)
    }

    @Test
    fun petDefaults() = runBlocking {
        val s = repo().settings.first()
        assertEquals("Boo", s.petName); assertEquals(70f, s.petMood); assertEquals(0L, s.petMoodAt)
        assertEquals(23, s.petSleepStart); assertEquals(7, s.petSleepEnd); assertEquals(false, s.petToyEverBound)
    }

    @Test
    fun petNameIsCleaned() {
        assertEquals("Boo", SettingsRepo.cleanPetName("   "))
        assertEquals("Spooky Ghost", SettingsRepo.cleanPetName("  Spooky Ghost  "))
        assertEquals("ABCDEFGHIJKL", SettingsRepo.cleanPetName("ABCDEFGHIJKLMNOP"))
    }

    @Test
    fun petNameEditsOnlySaveRealNames() {
        assertNull(SettingsRepo.petNameToSave(""))          // cleared while typing: keep the old name, don't snap back to "Boo"
        assertNull(SettingsRepo.petNameToSave("   "))
        assertEquals("Casper", SettingsRepo.petNameToSave(" Casper "))
        assertEquals("ABCDEFGHIJKL", SettingsRepo.petNameToSave("ABCDEFGHIJKLMNOP"))
    }

    @Test
    fun petKindDefaultsToGhostAndKeepsGhostName() = runBlocking {
        val r = repo()
        r.update { it.copy(petName = "Casper") }                     // an existing user, before multi-pets
        val s = r.settings.first()
        assertEquals("ghost", s.petKind)
        assertEquals("Casper", SettingsRepo.petNameFor(s, app.backlit.pet.PetKind.GHOST))
    }

    @Test
    fun namesArePerPet() {
        val s = Settings()
        val owl = app.backlit.pet.PetKind.OWL
        assertEquals("Hoot", SettingsRepo.petNameFor(s, owl))
        val named = SettingsRepo.withPetName(s, owl, "Professor")
        assertEquals("Professor", SettingsRepo.petNameFor(named, owl))
        assertEquals("Boo", SettingsRepo.petNameFor(named, app.backlit.pet.PetKind.GHOST))
        assertEquals("Ribbit", SettingsRepo.petNameFor(named, app.backlit.pet.PetKind.FROG))
        val ghost = SettingsRepo.withPetName(named, app.backlit.pet.PetKind.GHOST, "Spooky")
        assertEquals("Spooky", ghost.petName); assertEquals("Professor", SettingsRepo.petNameFor(ghost, owl))
        assertEquals("Hoot", SettingsRepo.petNameFor(SettingsRepo.withPetName(s, owl, "   "), owl))
    }
}
