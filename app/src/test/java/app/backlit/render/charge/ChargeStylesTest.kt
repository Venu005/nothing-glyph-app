package app.backlit.render.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeStylesTest {

    @Test
    fun registryOrderDefaultAndCycling() {
        assertEquals(listOf("sprout", "buddy", "number", "moon"), ChargeStyles.all.map { it.id })
        assertEquals("moon", ChargeStyles.byId("nope").id)
        assertEquals("sprout", ChargeStyles.next("moon"))
        assertEquals("buddy", ChargeStyles.next("sprout"))
        assertEquals("sprout", ChargeStyles.next("unknown"))
    }

    @Test
    fun allStylesAllLevelsStayInMask() {
        for (style in ChargeStyles.all) for (size in listOf(25, 13)) {
            for (level in listOf(0, 5, 50, 62, 99, 100)) {
                val frames = listOf(style.still(size, level)) +
                    (0L..5000L step 250).map { style.plugIn(size, level, it) } +
                    (0L..6000L step 300).map { style.charging(size, level, it) }
                frames.forEach { g ->
                    assertEquals(size, g.size)
                    for (y in 0 until size) for (x in 0 until size) if (g[x, y] > 0) assertTrue("${style.id} $size $level", g.hasLed(x, y))
                }
            }
            (0L..3500L step 100).forEach { assertEquals(size, style.done(size, it).size) }
        }
    }
}
