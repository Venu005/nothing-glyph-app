package app.backlit.sand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class HourglassShapeTest {
    @Test
    fun loadsMatchTheMockup() {
        assertEquals(61, HourglassShape.forSize(25).total)
        assertEquals(16, HourglassShape.forSize(13).total)
    }

    @Test
    fun bulbsAreMirrorImagesAndTheGateIsTheCentre() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            assertEquals(Cell.GATE, s.kind[s.gate])
            assertEquals(s.center * n + s.center, s.gate)
            assertEquals(s.cellsOn(1).size, s.cellsOn(-1).size)
            for (y in 0 until n) for (x in 0 until n) {
                assertEquals(s.kind[y * n + x], s.kind[(n - 1 - y) * n + x])
                assertEquals(s.kind[y * n + x], s.kind[y * n + (n - 1 - x)])
            }
        }
    }

    @Test
    fun everythingIsInsideTheMaskAndWallsTouchTheGlass() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            val c = (n - 1) / 2.0
            for (i in 0 until n * n) {
                val x = i % n; val y = i / n
                if (s.kind[i] != Cell.OUT) assertTrue(hypot(x - c, y - c) <= n / 2.0)
                if (s.kind[i] == Cell.WALL) {
                    val near = (-1..1).any { dy -> (-1..1).any { dx ->
                        val X = x + dx; val Y = y + dy
                        X in 0 until n && Y in 0 until n && s.isOpen(Y * n + X)
                    } }
                    assertTrue("wall $x,$y", near)
                }
            }
        }
    }
}
