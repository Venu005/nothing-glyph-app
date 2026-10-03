package app.backlit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitiesTest {

    private val sample = sequenceOf(
        "Tokyo\tJP\t35.6895\t139.6917",
        "Bengaluru\tIN\t12.9719\t77.5937",
        "Berlin\tDE\t52.5244\t13.4105",
        "Albany\tUS\t42.6526\t-73.7562",
        "broken line",
        "Nowhere\tXX\tnot-a-number\t1.0",
    )

    @Test
    fun parseSkipsMalformedLines() {
        val all = Cities.parse(sample)
        assertEquals(4, all.size)
        assertEquals(City("Bengaluru", "IN", 12.9719, 77.5937), all[1])
        assertEquals("Bengaluru, IN", all[1].display)
    }

    @Test
    fun searchIgnoresCase() {
        val all = Cities.parse(sample)
        assertEquals(listOf("Albany"), Cities.search(all, "ALB").map { it.name })
        assertEquals(setOf("Berlin", "Bengaluru"), Cities.search(all, "be").map { it.name }.toSet())
    }

    @Test
    fun prefixMatchesComeBeforeContainsMatches() {
        val all = Cities.parse(sample)
        val r = Cities.search(all, "al")
        assertEquals("Albany", r.first().name)          // prefix
        assertTrue(r.any { it.name == "Bengaluru" })     // contains "al"
    }

    @Test
    fun blankQueryReturnsNothingAndLimitApplies() {
        val all = Cities.parse(sample)
        assertTrue(Cities.search(all, "  ").isEmpty())
        assertEquals(1, Cities.search(all, "e", limit = 1).size)
    }

    @Test
    fun coordinatesAreRoundedToAboutOneKilometre() {
        assertEquals(12.97, roundCoord(12.97194), 1e-9)
        assertEquals(-73.76, roundCoord(-73.7562), 1e-9)
    }
}
