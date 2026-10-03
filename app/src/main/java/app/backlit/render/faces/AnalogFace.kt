package app.backlit.render.faces

import app.backlit.render.Draw
import app.backlit.render.FaceContext
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.cos
import kotlin.math.sin

object AnalogFace : Face {
    override val id = "analog"
    override val label = "ANALOG"

    private const val MAJOR_TICK = 200
    private const val MINOR_TICK = 70
    private const val MINUTE_HAND = 170
    private const val HOUR_HAND = 255

    override fun render(ctx: FaceContext): PixelGrid {
        val g = PixelGrid(ctx.size)
        val c = g.center
        val rim = c
        val large = ctx.isLarge

        for (k in 0 until 12) {
            val major = k % 3 == 0
            if (!large && !major) continue
            val a = Math.toRadians(k * 30.0 - 90.0)
            g.plot((c + cos(a) * rim).px(), (c + sin(a) * rim).px(), if (major) MAJOR_TICK else MINOR_TICK)
        }

        // Minute hand moves only on whole minutes so its anti-aliased edges never shimmer.
        val ma = Math.toRadians(ctx.minute * 6.0 - 90.0)
        val ml = if (large) 9.5 else 4.5
        Draw.wuLine(g, c, c, c + cos(ma) * ml, c + sin(ma) * ml, MINUTE_HAND)

        val ha = Math.toRadians(((ctx.hour % 12) + ctx.minute / 60.0) * 30.0 - 90.0)
        val hl = if (large) 6.0 else 3.0
        Draw.wuLine(g, c, c, c + cos(ha) * hl, c + sin(ha) * hl, HOUR_HAND)

        g.plot(c.px(), c.px(), HOUR_HAND)

        if (needsSecondTicks(ctx)) {
            val sa = Math.toRadians(ctx.second * 6.0 - 90.0)
            g.put((c + cos(sa) * rim).px(), (c + sin(sa) * rim).px(), 255)
        }
        return g
    }

    override fun needsSecondTicks(ctx: FaceContext): Boolean =
        ctx.isLarge && ctx.mode == Mode.ACTIVE && ctx.options.secondHand
}
