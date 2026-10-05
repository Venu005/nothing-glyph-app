package app.backlit.pet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class PetInsightTest {
    private val utc = ZoneOffset.UTC
    private val night = SleepWindow(23, 7)
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun hintsForEachState() {
        val noon = at("2026-10-05T12:00:00Z")
        assertEquals("Sleeping until 07:00 · mood won't drop", PetInsight.hint(60.0, at("2026-10-05T23:30:00Z"), night, utc, charging = false, asleep = true))
        assertEquals("Snacking · +1 mood per minute", PetInsight.hint(60.0, noon, night, utc, charging = true, asleep = false))
        assertEquals("Gets bored in ~2 h", PetInsight.hint(60.0, noon, night, utc, charging = false, asleep = false))     // 20 pts × 6 min
        assertEquals("Gets bored in ~30 min", PetInsight.hint(45.0, noon, night, utc, charging = false, asleep = false))
        assertEquals("Gets sad in ~1 h", PetInsight.hint(25.0, noon, night, utc, charging = false, asleep = false))       // 10 pts
        assertEquals("Pet him to cheer him up", PetInsight.hint(10.0, noon, night, utc, charging = false, asleep = false))
    }

    @Test
    fun countdownSkipsSleepHours() {
        // 22:00, 20 pts to bored = 2 awake hours: 1 h until 23:00, then sleep until 07:00, then 1 h → 10 h of wall time
        assertEquals("Gets bored in ~10 h", PetInsight.hint(60.0, at("2026-10-05T22:00:00Z"), night, utc, charging = false, asleep = false))
    }
}
