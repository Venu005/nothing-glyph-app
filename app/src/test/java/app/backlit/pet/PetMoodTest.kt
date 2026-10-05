package app.backlit.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class PetMoodTest {
    private val utc = ZoneOffset.UTC
    private val night = SleepWindow(23, 7)
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun sleepWindowContainsWrapsMidnight() {
        assertTrue(night.contains(at("2026-10-05T23:30:00Z"), utc))
        assertTrue(night.contains(at("2026-10-06T06:59:00Z"), utc))
        assertFalse(night.contains(at("2026-10-06T07:00:00Z"), utc))
        assertFalse(night.contains(at("2026-10-05T12:00:00Z"), utc))
        val early = SleepWindow(1, 5)
        assertTrue(early.contains(at("2026-10-05T03:00:00Z"), utc)); assertFalse(early.contains(at("2026-10-05T05:00:00Z"), utc))
        assertFalse(SleepWindow(9, 9).enabled); assertFalse(SleepWindow(9, 9).contains(at("2026-10-05T09:30:00Z"), utc))
    }

    @Test
    fun decaysOnePointPerSixAwakeMinutes() {
        val s = MoodState(70.0, at("2026-10-05T12:00:00Z"))
        assertEquals(60.0, PetMood.decay(s, at("2026-10-05T13:00:00Z"), night, utc), 1e-9)
        assertEquals(69.5, PetMood.decay(s, at("2026-10-05T12:03:00Z"), night, utc), 1e-9)
    }

    @Test
    fun sleepHoursDontDecay() {
        val s = MoodState(70.0, at("2026-10-05T22:00:00Z"))
        // 22:00 → 08:00: awake 22–23 and 07–08 = 2 h → −20
        assertEquals(50.0, PetMood.decay(s, at("2026-10-06T08:00:00Z"), night, utc), 1e-9)
        assertEquals(60.0, PetMood.decay(MoodState(70.0, at("2026-10-05T12:00:00Z")), at("2026-10-05T13:00:00Z"), SleepWindow(9, 9), utc), 1e-9)
    }

    @Test
    fun multiDaySpanSkipsSleepAndFloorsAtZero() {
        val s = MoodState(70.0, at("2026-10-05T12:00:00Z"))
        assertEquals(0.0, PetMood.decay(s, at("2026-10-08T12:00:00Z"), night, utc), 1e-9)
        assertEquals(48L * 3_600_000, PetMood.awakeMs(s.at, at("2026-10-08T12:00:00Z"), night, utc))
    }

    @Test
    fun futureOrUnsetTimestampMeansNoDecay() {
        assertEquals(70.0, PetMood.decay(MoodState(70.0, at("2026-10-05T14:00:00Z")), at("2026-10-05T12:00:00Z"), night, utc), 1e-9)
        assertEquals(70.0, PetMood.decay(MoodState(70.0, 0L), at("2026-10-05T12:00:00Z"), night, utc), 1e-9)
        assertEquals(100.0, PetMood.clamp(130.0), 1e-9); assertEquals(0.0, PetMood.clamp(-4.0), 1e-9)
    }

    @Test
    fun repeatedShortSpansAddUp() {
        var s = MoodState(70.0, at("2026-10-05T12:00:00Z"))
        repeat(12) {                                                   // saved every 5 minutes for an hour
            val now = s.at + 300_000
            s = MoodState(PetMood.decay(s, now, night, utc), now)
        }
        assertEquals(60.0, s.mood, 1e-9)
    }
}
