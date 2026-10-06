package app.backlit.ui.home

import app.backlit.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToyCatalogTest {
    @Test
    fun orderAndLabels() {
        assertEquals(listOf("CLOCK", "MUSIC", "CHARGE", "CANVAS", "PET", "SAND", "BADGE"), ToyCatalog.visible(false).map { it.label })
        assertEquals(ToyId.SAND, ToyId.byKey("sand"))
        assertNull(ToyId.byKey("nope"))
    }

    @Test
    fun visibleHidesMusicOnlyWhenAsked() {
        assertEquals(6, ToyCatalog.visible(hideMusic = true).size)
        assertEquals(false, ToyCatalog.visible(hideMusic = true).contains(ToyId.MUSIC))
        assertEquals(7, ToyCatalog.visible(hideMusic = false).size)
    }

    @Test
    fun setUpFollowsEachToysOwnFlag() {
        val s = Settings(clockToyEverBound = true, petToyEverBound = true, badgeToyEverBound = true, toyEverBound = true)
        val on = ToyId.entries.filter { ToyCatalog.isSetUp(s, it) }
        assertEquals(listOf(ToyId.CLOCK, ToyId.PET, ToyId.BADGE), on)
        assertEquals(3, ToyCatalog.setUpCount(s, ToyCatalog.visible(false)))
        // the shared toyEverBound alone no longer marks the clock or music
        assertEquals(0, ToyCatalog.setUpCount(Settings(toyEverBound = true), ToyCatalog.visible(false)))
        val all = Settings(clockToyEverBound = true, musicToyEverBound = true, chargeToyEverBound = true, canvasToyEverBound = true,
            petToyEverBound = true, sandToyEverBound = true, badgeToyEverBound = true)
        assertEquals(7, ToyCatalog.setUpCount(all, ToyCatalog.visible(false)))
    }
}
