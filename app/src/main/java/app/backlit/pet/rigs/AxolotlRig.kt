package app.backlit.pet.rigs

import app.backlit.pet.BodyOpts
import app.backlit.pet.EyeMode
import app.backlit.pet.EyeSpec
import app.backlit.pet.FaceOpts
import app.backlit.pet.Gill
import app.backlit.pet.MouthSpec
import app.backlit.pet.Rig
import app.backlit.pet.RigArt
import app.backlit.pet.RigArt.F
import app.backlit.pet.RigArt.O
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.sin

object AxolotlRig : Rig {
    override val eyes25 = EyeSpec(listOf(9, 15), 12, 1, 2, EyeMode.BRIGHT)
    override val eyes13 = EyeSpec(listOf(4, 8), 6, 1, 1, EyeMode.BRIGHT)
    override val mouth25 = MouthSpec(12, 16)
    override val mouth13 = MouthSpec(6, 7)
    override val cheeks25 = listOf(7 to 16, 17 to 16)
    override val cheeks13 = listOf(3 to 8, 9 to 8)
    override val top25 = 8

    override fun body(g: PixelGrid, o: BodyOpts) {
        val k = if (o.puff) 1.08 else 1.0
        val sw = when (o.gill) { Gill.FAST -> sin(o.t / 100.0); Gill.SLOW -> sin(o.t / 1400.0); Gill.STILL -> 0.0; else -> sin(o.t / 550.0) }
        val droop = o.gill == Gill.DROOP
        val tipV = if (o.b >= O) 1.0 else o.b
        if (g.size >= 25) {
            RigArt.blob(g, 12.0 + o.dx, 14.0 + o.dy, 7.5 * k, 5.5 * k, F, o.b)
            for (sd in listOf(-1, 1)) listOf(10 to -2, 13 to 0, 16 to 2).forEachIndexed { i, (gy, gdy) ->
                val x0 = 12 + sd * 7 + o.dx; val y0 = gy + o.dy
                val w = if (droop) 2 else (sw * (if (i == 1) 0.6 else 1.0) * (if (i == 0) -1 else 1)).px()
                val x1 = x0 + sd * 3; val y1 = y0 + gdy + w
                RigArt.line(g, x0.toDouble(), y0.toDouble(), x1.toDouble(), y1.toDouble(), 0.75 * (o.b / O))
                g.set(x1.toDouble(), y1.toDouble(), tipV); g.set((x1 + sd).toDouble(), (y1 - 1).toDouble(), 0.8 * (o.b / O)); g.set((x1 + sd).toDouble(), (y1 + 1).toDouble(), 0.8 * (o.b / O))
            }
        } else {
            RigArt.bm(g, listOf("..ooooo..", ".oFFFFFo.", "oFFFFFFFo", "oFFFFFFFo", ".oFFFFFo.", "..ooooo.."), 2 + o.dx, 4 + o.dy, mapOf('o' to o.b, 'F' to F))
            val w = if (droop) 1 else sw.px()
            listOf(1 to 4, 0 to 7, 1 to 10).forEachIndexed { i, (px, py) ->
                val yo = if (droop) 1 else if (i == 1) 0 else w * (if (i == 0) -1 else 1)
                g.set((px + o.dx).toDouble(), (py + o.dy + yo).toDouble(), tipV); g.set((12 - px + o.dx).toDouble(), (py + o.dy + yo).toDouble(), tipV)
            }
        }
    }

    override fun signature(g: PixelGrid, t: Long) {
        body(g, BodyOpts(t = t)); RigArt.eyes(g, this, "open", FaceOpts()); RigArt.mouth(g, this, "O", FaceOpts())
        if (g.size >= 25) {
            for (i in 0..2) {
                val q = ((t / 1000.0) + i / 3.0) % 1.0
                val bx = 12 + sin(q * 7 + i) * 1.6; val by = 15 - q * 15
                if (q > 0.35) { g.dot(bx - 1, by, 1 - q); g.dot(bx + 1, by, 1 - q); g.dot(bx, by - 1, 1 - q); g.dot(bx, by + 1, 1 - q) } else g.dot(bx, by, 1 - q)
            }
        } else { val q = (t / 900.0) % 1.0; g.dot(6.0, 7 - q * 7, 1 - q) }
    }
}
