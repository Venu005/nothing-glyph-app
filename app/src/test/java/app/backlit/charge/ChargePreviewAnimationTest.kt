package app.backlit.charge

import app.backlit.render.charge.MoonStyle
import app.backlit.render.charge.NumberStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargePreviewAnimationTest {

    @Test
    fun idsRoundTrip() {
        val id = ChargePreviewAnimation.idFor("moon", Moment.PLUG_IN)
        assertEquals("charge:moon:plug_in", id)
        val a = ChargePreviewAnimation.parse(id)!!
        assertEquals(id, a.id)
        assertEquals(5000L, a.loopMs)
        assertEquals(MoonStyle.plugIn(25, 62, 1200), a.frame(25, 1200))
    }

    @Test
    fun badIdsDoNotParse() {
        assertNull(ChargePreviewAnimation.parse("charge:moon"))
        assertNull(ChargePreviewAnimation.parse("charge:moon:sideways"))
        assertNull(ChargePreviewAnimation.parse("builtin:heart"))
        assertEquals("moon", ChargePreviewAnimation.parse("charge:nope:still")!!.id.split(':')[1])   // unknown style → default
    }

    @Test
    fun durationsAndFrames() {
        assertEquals(3500L, ChargePreviewAnimation.durationMs(Moment.DONE))
        assertEquals(3000L, ChargePreviewAnimation.durationMs(Moment.STILL))
        assertEquals(NumberStyle.still(13, 62), ChargePreviewAnimation(NumberStyle, Moment.STILL).frame(13, 999))
    }
}
