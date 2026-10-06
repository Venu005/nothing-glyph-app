package app.backlit.badge

import app.backlit.badge.BadgeMessage.Kind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BadgeTextTest {
    private val zone = ZoneOffset.UTC
    private val ten = ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, zone).toInstant().toEpochMilli()   // 10:00
    private val h = 3_600_000L
    private val back = BadgeMessage.STARTERS[1]
    private val meeting = BadgeMessage("IN A MEETING", "laptop", Kind.UNTIL, untilMinuteOfDay = 15 * 60)
    private fun scroll(m: BadgeMessage, now: Long, since: Long = ten, h24: Boolean = false) = BadgeText.scroll(m, since, now, h24, zone)
    private fun short(m: BadgeMessage, now: Long, since: Long = ten, h24: Boolean = false) = BadgeText.short(m, since, now, h24, zone)

    @Test
    fun countdownCountsThenSaysSoon() {
        assertEquals("BACK IN 5:00", scroll(back, ten))
        assertEquals("BACK IN 4:59", scroll(back, ten + 1000))
        assertEquals("BACK IN 0:01", scroll(back, ten + 299_001))
        assertEquals("BACK SOON", scroll(back, ten + 300_000))
        assertEquals("LUNCH SOON", scroll(BadgeMessage("LUNCH", "food", Kind.COUNTDOWN, minutes = 1), ten + 60_000))
        assertEquals("SOON", scroll(BadgeMessage("", "food", Kind.COUNTDOWN, minutes = 1), ten + 60_000))
    }

    @Test
    fun longCountdownsUseHours() {
        assertEquals("BACK IN 1:30:00", scroll(back.copy(minutes = 90), ten))
        assertEquals("99M", short(back.copy(minutes = 99), ten))
        assertEquals("3H", short(back.copy(minutes = 150), ten))
        assertEquals("5M", short(back, ten))
        assertEquals("1M", short(back, ten + 299_001))
        assertEquals("SOON", short(back, ten + 300_000))
    }

    @Test
    fun untilShowsTheTimeThenDropsIt() {
        assertEquals("IN A MEETING UNTIL 3PM", scroll(meeting, ten))
        assertEquals("IN A MEETING UNTIL 15:00", scroll(meeting, ten, h24 = true))
        assertEquals("IN A MEETING UNTIL 3PM", scroll(meeting, ten + 5 * h - 1))
        assertEquals("IN A MEETING", scroll(meeting, ten + 5 * h))
        assertEquals("3PM", short(meeting, ten))
        assertEquals("15", short(meeting, ten, h24 = true))
        assertEquals("IN", short(meeting, ten + 5 * h))
    }

    @Test
    fun untilPickedAfterItsTimeMeansTomorrow() {
        val four = ten + 6 * h                                    // picked at 16:00, until 15:00
        assertEquals("IN A MEETING UNTIL 3PM", scroll(meeting, four, since = four))
        assertEquals("IN A MEETING UNTIL 3PM", scroll(meeting, four + 22 * h, since = four))   // 14:00 tomorrow
        assertEquals("IN A MEETING", scroll(meeting, four + 23 * h, since = four))            // 15:00 tomorrow
        val midnight = meeting.copy(untilMinuteOfDay = 30)        // 00:30, picked at 10:00 → after midnight
        assertEquals("IN A MEETING UNTIL 12:30AM", scroll(midnight, ten + 14 * h))
        assertEquals("IN A MEETING", scroll(midnight, ten + 14 * h + 30 * 60_000))
    }

    @Test
    fun timeLabels() {
        assertEquals("3:30PM", BadgeText.timeLabel(15 * 60 + 30, false))
        assertEquals("12AM", BadgeText.timeLabel(0, false))
        assertEquals("12PM", BadgeText.timeLabel(12 * 60, false))
        assertEquals("9:05", BadgeText.timeLabel(9 * 60 + 5, true))
    }

    @Test
    fun plainShowsTextAndFirstWord() {
        val m = BadgeMessage("DO NOT DISTURB", "moon")
        assertEquals("DO NOT DISTURB", scroll(m, ten))
        assertEquals("DO", short(m, ten))
        assertEquals("", short(BadgeMessage("", "moon"), ten))
    }
    @Test
    fun theScrollSpanStaysTheSameForTheWholeCountdown() {
        val span = BadgeText.spanText(back, use24h = false)
        assertEquals("BACK IN 5:00", span)
        assertEquals("IN A MEETING UNTIL 12:30AM", BadgeText.spanText(meeting.copy(untilMinuteOfDay = 30), use24h = false))
        assertEquals("DO NOT DISTURB", BadgeText.spanText(BadgeMessage("DO NOT DISTURB", "moon"), false))
        // the frame's loop length uses the span, so "BACK IN 9:59" and "BACK SOON" scroll on the same loop
        val a = BadgeArt.frame(25, back, "BACK IN 9:59", 4_000, BadgeArt.FLASH_MS, span)
        val b = BadgeArt.frame(25, back, "BACK IN 9:59", 4_000 + (BadgeFont.width(25, span) + 29) * 55L, BadgeArt.FLASH_MS, span)
        assertEquals(a.raw().toList(), b.raw().toList())
    }
}
