package app.backlit.render.faces

import app.backlit.render.Draw
import app.backlit.render.FaceContext
import app.backlit.render.PixelFont
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The rim is a 24-hour dial: midnight at the bottom, 06:00 left, noon top, 18:00 right.
 * Daylight hours are bright, night hours dim, the sun or moon sits on the rim, the time in the middle.
 */
object DayRingFace : Face {
    override val id = "dayring"
    override val label = "DAY RING"

    private const val BAND = 0.6

    fun hourText(hour: Int, use24h: Boolean): String {
        val h = if (use24h) hour else (hour % 12).let { if (it == 0) 12 else it }
        return h.toString().padStart(2, '0')
    }

    override fun render(ctx: FaceContext): PixelGrid {
        val g = PixelGrid(ctx.size)
        val large = ctx.isLarge
        val c = g.center
        val ringR = if (large) 11.5 else 5.6
        val dayB = if (large) 110 else 90
        val nightB = if (large) 22 else 18

        for (y in 0 until ctx.size) for (x in 0 until ctx.size) {
            if (!g.hasLed(x, y)) continue
            if (abs(hypot(x - c, y - c) - ringR) > BAND) continue
            if (!large && x in 2..10 && (y <= 1 || y >= 11)) continue   // room for the stacked digits
            var a = atan2(y - c, x - c) - PI / 2
            a = (a + 4 * PI) % (2 * PI)
            val minute = a / (2 * PI) * 1440.0
            g.plot(x, y, if (ctx.dayLight.isDay(minute)) dayB else nightB)
        }

        val hh = hourText(ctx.hour, ctx.options.use24h)
        val mm = ctx.minute.toString().padStart(2, '0')
        if (large) {
            PixelFont.text(g, "$hh:$mm", 4, 10, 230)
        } else {
            for (y in 1..11) for (x in 3..9) g.put(x, y, 0)
            PixelFont.digit(g, hh[0] - '0', 3, 1, 255)
            PixelFont.digit(g, hh[1] - '0', 7, 1, 255)
            PixelFont.digit(g, mm[0] - '0', 3, 7, 170)
            PixelFont.digit(g, mm[1] - '0', 7, 7, 170)
        }

        val markerR = if (large) ringR - 1 else ringR
        val ma = PI / 2 + ctx.minuteOfDay / 1440.0 * 2 * PI
        val mx = c + cos(ma) * markerR
        val my = c + sin(ma) * markerR
        val isDay = ctx.dayLight.isDay(ctx.minuteOfDay)
        when {
            !large -> g.put(mx.px(), my.px(), 255)
            isDay -> Draw.disc(g, mx, my, 1.5, 255)
            else -> Draw.crescent(g, mx, my, 170)
        }
        return g
    }

    override fun needsSecondTicks(ctx: FaceContext): Boolean = false
}
