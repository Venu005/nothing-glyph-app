package app.backlit.render.viz

import org.junit.Assert.assertEquals
import org.junit.Test

class IdleLineTest {

    @Test
    fun onlyTheCentreRowIsLit() {
        val g = IdleLine(25).render()
        assertEquals(25, g.litCount())
        for (x in 0 until 25) assertEquals(100, g[x, 12])
    }

    @Test
    fun breathesOverFourSeconds() {
        val l = IdleLine(25)
        assertEquals(100, l.brightness())
        l.update(1000, fallback = false)
        assertEquals(140, l.brightness())
        l.update(2000, fallback = false)
        assertEquals(60, l.brightness())
    }

    @Test
    fun fallbackIsFasterAndBrighter() {
        val l = IdleLine(25)
        l.update(500, fallback = true)
        assertEquals(180, l.brightness())
    }
}
