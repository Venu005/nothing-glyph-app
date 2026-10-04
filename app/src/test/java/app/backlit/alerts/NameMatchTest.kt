package app.backlit.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatchTest {
    @Test
    fun matchesIgnoringCaseSpacingAndBidiMarks() {
        assertTrue(NameMatch.matches("Mom", "mom"))
        assertTrue(NameMatch.matches("  Ravi   Kumar ", "Ravi Kumar"))
        assertTrue(NameMatch.matches("‪Mom‬", "Mom"))
        assertTrue(NameMatch.matches("⁨Dad⁩", "dad"))
    }

    @Test
    fun noPartialOrEmptyMatches() {
        assertFalse(NameMatch.matches("Mom", "Mom Work"))
        assertFalse(NameMatch.matches("", ""))
        assertFalse(NameMatch.matches("   ", "Mom"))
        assertFalse(NameMatch.matches("+91 98765 43210", "Mom"))
    }
}
