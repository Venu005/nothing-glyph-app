package app.backlit.sand

import app.backlit.render.PixelFont
import app.backlit.render.PixelFont5x7
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Draws the hourglass (mockups 2026-10-06-sand-styles style A and sand-moments). */
object SandArt {
    private const val WALL = 0.16
    private const val GRAIN = 0.7
    private const val STREAM_STEP_MS = 90L

    private fun b(v: Double): Int = (v * 255).px()

    /** Live frame while the toy is ACTIVE. [grains]/[moved] come from the SandSim. */
    fun frame(shape: HourglassShape, grains: BooleanArray, moved: BooleanArray?, st: TimerState, now: Long): PixelGrid {
        if (now < st.numberUntil) return number(shape, grains, st.numberValue)
        if (now < st.refillUntil) {
            val f = (now - st.numberUntil).toDouble() / TimerState.REFILL_MS
            return picture(shape, SandLayout.layout(shape, 0.0, false, st.upSide, f.coerceIn(0.0, 1.0)), null, WALL)
        }
        if (st.phase == Phase.DONE && now - st.doneAt in 0 until TimerState.FLIP_ME_MS) return flipMe(shape, st.upSide, now - st.doneAt)
        val wall = when (st.phase) {
            Phase.READY -> 0.16 + 0.12 * max(0.0, sin(now / 500.0))
            Phase.DONE -> 0.16 + 0.08 * max(0.0, sin(now / 1500.0))
            else -> WALL
        }
        val g = picture(shape, grains, moved, wall)
        if (st.phase == Phase.RUNNING) stream(shape, grains, st.upSide, now, g)
        return g
    }

    /**
     * A real hourglass always shows a thin falling stream, even when the neck only lets a grain through every few
     * seconds: the neck is lit and dots fall from it to the top of the pile while sand is left in the up bulb.
     */
    private fun stream(shape: HourglassShape, grains: BooleanArray, upSide: Int, now: Long, g: PixelGrid) {
        val n = shape.size
        val c = shape.center
        if (shape.cellsOn(upSide).none { grains[it] }) return
        g.put(c, c, b(1.0))
        val dir = if (upSide >= 0) 1 else -1
        val phase = (now / STREAM_STEP_MS).toInt()
        var k = 1
        while (true) {
            val y = c + dir * k
            if (y !in 0 until n) break
            val i = y * n + c
            if (!shape.isOpen(i) || grains[i]) break
            if (Math.floorMod(k - phase, 3) == 0) g.put(c, y, b(1.0))
            k++
        }
    }

    /** Always-on still (one frame a minute). */
    fun still(shape: HourglassShape, st: TimerState, now: Long): PixelGrid {
        val running = st.phase == Phase.RUNNING
        val grains = SandLayout.layout(shape, st.fractionUp(now), running, st.upSide)
        if (now < st.numberUntil) return number(shape, grains, st.numberValue)
        val bright = if (running) SandLayout.streamCells(shape, st.upSide).toSet() else emptySet()
        return picture(shape, grains, null, if (st.phase == Phase.DONE) 0.35 else WALL, bright)
    }

    fun picture(shape: HourglassShape, grains: BooleanArray, moved: BooleanArray?, wall: Double, bright: Set<Int> = emptySet()): PixelGrid {
        val n = shape.size
        val g = PixelGrid(n)
        for (i in 0 until n * n) {
            val x = i % n
            val y = i / n
            if (grains[i]) g.put(x, y, b(if (moved?.get(i) == true || i in bright) 1.0 else GRAIN))
            else if (shape.kind[i] == Cell.WALL) g.put(x, y, b(wall))
        }
        return g
    }

    /** The long-press number on a cleared window over the dimmed hourglass. 5×7 digits at 25×25, 3×5 at 13×13. */
    private fun number(shape: HourglassShape, grains: BooleanArray, value: Int): PixelGrid {
        val n = shape.size
        val base = picture(shape, grains, null, WALL)
        val g = PixelGrid(n)
        for (y in 0 until n) for (x in 0 until n) g.put(x, y, (base[x, y] * 0.25).px())
        val s = value.coerceIn(0, 99).toString()
        val big = n >= 25
        val w = if (big) PixelFont5x7.WIDTH else PixelFont.WIDTH
        val h = if (big) PixelFont5x7.HEIGHT else PixelFont.HEIGHT
        val width = s.length * (w + 1) - 1
        val x0 = (shape.center - (width - 1) / 2.0).px()
        val y0 = (shape.center - (h - 1) / 2.0).px()
        for (y in y0 - 1..y0 + h) for (x in x0 - 1..x0 + width) g.put(x, y, 0)
        s.forEachIndexed { k, ch ->
            val x = x0 + k * (w + 1)
            if (big) PixelFont5x7.digit(g, ch - '0', x, y0, 255) else PixelFont.digit(g, ch - '0', x, y0, 255)
        }
        return g
    }

    /** "Flip me": the done picture spins half a turn (eased, 1200 ms), then the glass blinks twice. */
    fun flipMe(shape: HourglassShape, upSide: Int, t: Long): PixelGrid {
        val n = shape.size
        val c = shape.center.toDouble()
        val src = picture(shape, SandLayout.layout(shape, 0.0, false, upSide), null, 0.3)
        val a = if (t < 1200) (1 - cos(t / 1200.0 * PI)) / 2 * PI else PI
        val ca = cos(-a)
        val sa = sin(-a)
        val blink = t in 1300 until 2300 && ((t - 1300) / 250) % 2 == 0L
        val g = PixelGrid(n)
        for (y in 0 until n) for (x in 0 until n) {
            if (!g.hasLed(x, y)) continue
            val dx = x - c
            val dy = y - c
            var v = src[(c + dx * ca - dy * sa).px(), (c + dx * sa + dy * ca).px()]
            if (blink && v in 1 until 128) v = b(0.85)
            g.put(x, y, v)
        }
        return g
    }
}
