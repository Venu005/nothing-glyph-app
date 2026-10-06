package app.backlit.badge

import app.backlit.badge.BadgeMessage.Kind
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** What a message says right now: live countdowns, "until" times, and the short word for always-on. */
object BadgeText {
    private const val MIN = 60_000L
    private const val HOUR = 3_600_000L

    fun scroll(m: BadgeMessage, since: Long, now: Long, use24h: Boolean, zone: ZoneId): String = when (m.kind) {
        Kind.PLAIN -> m.text
        Kind.COUNTDOWN -> {
            val left = left(m, since, now)
            if (left > 0) join(m.text, clock(left)) else soon(m.text)
        }
        Kind.UNTIL -> if (now < untilEnd(m, since, zone)) join(m.text, "UNTIL " + timeLabel(m.untilMinuteOfDay, use24h)) else m.text
    }

    fun short(m: BadgeMessage, since: Long, now: Long, use24h: Boolean, zone: ZoneId): String = when (m.kind) {
        Kind.PLAIN -> firstWord(m.text)
        Kind.COUNTDOWN -> {
            val left = left(m, since, now)
            val mins = ceilDiv(left, MIN)
            when {
                left <= 0 -> "SOON"
                mins <= 99 -> "${mins}M"
                else -> "${ceilDiv(left, HOUR)}H"
            }
        }
        Kind.UNTIL -> if (now < untilEnd(m, since, zone)) shortTime(m.untilMinuteOfDay, use24h) else firstWord(m.text)
    }

    /** The next time [BadgeMessage.untilMinuteOfDay] comes round after [since] (the same minute or earlier → tomorrow). */
    fun untilEnd(m: BadgeMessage, since: Long, zone: ZoneId): Long {
        val start = Instant.ofEpochMilli(since).atZone(zone)
        var end = start.toLocalDate().atTime(LocalTime.of(m.untilMinuteOfDay / 60, m.untilMinuteOfDay % 60)).atZone(zone)
        if (!end.isAfter(start)) end = end.plusDays(1)
        return end.toInstant().toEpochMilli()
    }

    fun timeLabel(minuteOfDay: Int, use24h: Boolean): String {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        if (use24h) return String.format(Locale.US, "%d:%02d", h, m)
        val h12 = if (h % 12 == 0) 12 else h % 12
        val ap = if (h < 12) "AM" else "PM"
        return if (m == 0) "$h12$ap" else String.format(Locale.US, "%d:%02d%s", h12, m, ap)
    }

    private fun shortTime(minuteOfDay: Int, use24h: Boolean): String {
        val h = minuteOfDay / 60
        if (use24h) return "$h"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12" + if (h < 12) "AM" else "PM"
    }

    private fun left(m: BadgeMessage, since: Long, now: Long): Long = m.minutes * MIN - (now - since)

    private fun clock(ms: Long): String {
        val s = ceilDiv(ms, 1000)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /** "BACK IN" → "BACK SOON"; anything else gets " SOON". */
    private fun soon(text: String): String {
        val t = text.trim()
        val base = if (t == "IN") "" else t.removeSuffix(" IN")
        return join(base, "SOON")
    }

    private fun join(a: String, b: String): String = listOf(a.trim(), b).filter { it.isNotBlank() }.joinToString(" ")

    private fun firstWord(text: String): String = text.trim().substringBefore(' ')

    private fun ceilDiv(a: Long, b: Long): Long = if (a <= 0) 0 else (a + b - 1) / b
}
