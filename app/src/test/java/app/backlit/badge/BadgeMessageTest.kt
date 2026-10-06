package app.backlit.badge

import app.backlit.badge.BadgeMessage.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class BadgeMessageTest {
    @Test
    fun textIsUppercasedAndLimitedToTheFont() {
        assertEquals("BACK IN 5", BadgeMessage("back in 5 😀 #").clean().text)
        assertEquals("IN ", BadgeMessage.cleanText("in "))           // a trailing space survives while typing
        assertEquals("A B", BadgeMessage.cleanText("a    b"))
        assertEquals("IT'S OK? YES! 3:30 - .", BadgeMessage("it's ok? yes! 3:30 - .").clean().text)
        assertEquals(32, BadgeMessage.cleanText("A".repeat(40)).length)
    }

    @Test
    fun cleanClampsNumbersAndUnknownIcons() {
        val m = BadgeMessage("x", "nope", Kind.COUNTDOWN, minutes = 500, untilMinuteOfDay = 5000).clean()
        assertEquals("heart", m.icon)
        assertEquals(180, m.minutes)
        assertEquals(1439, m.untilMinuteOfDay)
        assertEquals(1, BadgeMessage(minutes = 0).clean().minutes)
        assertEquals(0, BadgeMessage(untilMinuteOfDay = -5).clean().untilMinuteOfDay)
    }

    @Test
    fun startersMatchTheSpec() {
        val s = BadgeMessage.STARTERS
        assertEquals(listOf("IN A MEETING", "BACK IN", "ON A CALL", "DO NOT DISTURB", "THANK YOU"), s.map { it.text })
        assertEquals(listOf("laptop", "coffee", "phone", "moon", "heart"), s.map { it.icon })
        assertEquals(Kind.COUNTDOWN, s[1].kind)
        assertEquals(5, s[1].minutes)
        assertEquals(8, BadgeIcons.ids.size)
    }

    @Test
    fun listRoundTripsThroughJson() {
        val list = BadgeMessage.STARTERS + BadgeMessage("LUNCH", "food", Kind.UNTIL, untilMinuteOfDay = 13 * 60 + 30)
        assertEquals(list, BadgeMessage.decodeList(BadgeMessage.encodeList(list)))
    }

    @Test
    fun badJsonFallsBackToStarters() {
        assertEquals(BadgeMessage.STARTERS, BadgeMessage.decodeList(""))
        assertEquals(BadgeMessage.STARTERS, BadgeMessage.decodeList("{not json"))
        assertEquals(BadgeMessage.STARTERS, BadgeMessage.decodeList("[]"))
        val ten = List(10) { BadgeMessage("M$it", "heart") }
        assertEquals(8, BadgeMessage.decodeList(BadgeMessage.encodeList(ten)).size)
    }

    @Test
    fun activeIndexClamps() {
        assertEquals(4, BadgeMessage.activeIndex(9, 5))
        assertEquals(0, BadgeMessage.activeIndex(-1, 5))
        assertEquals(2, BadgeMessage.activeIndex(2, 5))
    }

    @Test
    fun movingKeepsTheSameMessageCurrent() {
        assertEquals(1, BadgeMessage.moveActive(active = 2, from = 2, to = 1))
        assertEquals(2, BadgeMessage.moveActive(active = 1, from = 2, to = 1))
        assertEquals(0, BadgeMessage.moveActive(active = 0, from = 2, to = 1))
    }

    @Test
    fun afterDeletePicksTheNext() {
        assertEquals(1, BadgeMessage.afterDelete(active = 2, deleted = 1, sizeBefore = 5))
        assertEquals(2, BadgeMessage.afterDelete(active = 2, deleted = 2, sizeBefore = 5))   // the next one slides into 2
        assertEquals(0, BadgeMessage.afterDelete(active = 4, deleted = 4, sizeBefore = 5))   // wraps
        assertEquals(1, BadgeMessage.afterDelete(active = 1, deleted = 3, sizeBefore = 5))
    }
}
