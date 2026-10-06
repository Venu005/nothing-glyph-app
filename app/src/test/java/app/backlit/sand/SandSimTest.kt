package app.backlit.sand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

class SandSimTest {
    private fun full(n: Int, seed: Long = 1) = SandSim(HourglassShape.forSize(n), Random(seed)).also {
        it.load(SandLayout.layout(it.shape, 1.0, false))
    }

    @Test
    fun grainsAreConservedAndStayInTheGlass() {
        for (n in listOf(25, 13)) {
            val sim = full(n)
            sim.gateRate = 0.01
            val r = Random(7)
            repeat(10_000) {
                val a = r.nextDouble() * 2 * Math.PI
                sim.step(sin(a), cos(a), 50.0)
                if (it % 500 == 0) {
                    assertEquals(sim.shape.total, sim.count())
                    for (i in sim.grid.indices) if (sim.grid[i]) assertTrue(sim.shape.isOpen(i))
                }
            }
            assertEquals(sim.shape.total, sim.count())
        }
    }

    @Test
    fun withTheGateClosedNoGrainChangesBulb() {
        for (n in listOf(25, 13)) {
            val sim = full(n)
            sim.gateRate = 0.0
            val r = Random(3)
            repeat(2_000) {
                val a = r.nextDouble() * 2 * Math.PI
                sim.step(sin(a), cos(a), 50.0)
                assertEquals(sim.shape.total, sim.countOn(1))
            }
        }
    }

    @Test
    fun onItsSideNothingCrossesTheNeck() {
        for (n in listOf(25, 13)) for (gx in listOf(1.0, -1.0)) {
            val sim = full(n)
            sim.gateRate = 1.0
            repeat(500) { sim.step(gx, 0.0, 50.0) }
            assertEquals(sim.shape.total, sim.countOn(1))
        }
    }

    @Test
    fun drainsWithTheClock() {
        for (n in listOf(25, 13)) {
            val sim = full(n)
            val d = 60_000L
            var t = 0L
            var finished = -1L
            while (t < 2 * d) {
                if (t % 1000 == 0L) sim.gateRate = SandSim.gateRateFor(sim.shape.total, d, sim.countOn(1), (d - t).coerceAtLeast(0))
                sim.step(0.0, 1.0, 50.0)
                t += 50
                if (sim.countOn(1) == 0 && !sim.grid[sim.shape.gate]) { finished = t; break }
            }
            assertTrue("n=$n finished at $finished", finished in (d * 95 / 100)..(d * 105 / 100))
        }
    }

    @Test
    fun gateRateControllerFollowsTheClock() {
        val base = 61.0 / 60_000
        assertEquals(base, SandSim.gateRateFor(61, 60_000, 61, 60_000), 1e-12)        // on track
        assertEquals(base + 3 / 1000.0, SandSim.gateRateFor(61, 60_000, 34, 30_000), 1e-12)  // 3 extra grains on top
        assertEquals(0.0, SandSim.gateRateFor(61, 60_000, 20, 30_000), 1e-12)          // too few: wait for the clock
    }
}
