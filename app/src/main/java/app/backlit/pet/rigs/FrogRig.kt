package app.backlit.pet.rigs

import app.backlit.pet.BodyOpts
import app.backlit.pet.EyeMode
import app.backlit.pet.EyeSpec
import app.backlit.pet.FaceOpts
import app.backlit.pet.MouthSpec
import app.backlit.pet.Rig
import app.backlit.pet.RigArt
import app.backlit.pet.RigArt.F
import app.backlit.pet.RigArt.L
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object FrogRig : Rig {
    override val eyes25 = EyeSpec(listOf(6, 16), 9, 2, 2, EyeMode.HOLE)
    override val eyes13 = EyeSpec(listOf(3, 9), 5, 1, 1, EyeMode.HOLE)
    override val mouth25 = MouthSpec(12, 16, 2)
    override val mouth13 = MouthSpec(6, 8, 1)
    override val cheeks25 = listOf(5 to 16, 19 to 16)
    override val cheeks13 = listOf(2 to 8, 10 to 8)
    override val top25 = 5
    private val BOLT = listOf("01", "11", "10")

    override fun body(g: PixelGrid, o: BodyOpts) {
        val k = if (o.puff) 1.08 else 1.0
        if (g.size >= 25) {
            RigArt.blob(g, 12.0 + o.dx, 15.0 + o.dy, 9.5 * k, 6 * k, F, o.b)
            for (ex in listOf(7, 17)) RigArt.blob(g, (ex + o.dx).toDouble(), 9.0 + o.dy, 3.4, 3.4, L, o.b)
            RigArt.pts(g, listOf(7 to 21, 8 to 21, 9 to 21, 15 to 21, 16 to 21, 17 to 21), o.dx, o.dy, 0.75)
        } else {
            RigArt.bm(g, listOf("oo.....oo", "oLo...oLo", "ooooooooo", "oFFFFFFFo", "oFFFFFFFo", ".oFFFFFo."), 2 + o.dx, 4 + o.dy, mapOf('o' to o.b, 'L' to L, 'F' to F))
        }
    }

    override fun signature(g: PixelGrid, t: Long) {
        body(g, BodyOpts()); RigArt.eyes(g, this, "open", FaceOpts()); RigArt.mouth(g, this, "smile", FaceOpts())
        val c = t % 1500
        if (g.size >= 25) {
            val fa = t / 400.0
            val fx = (19 + cos(fa) * 1.5).px(); val fy = (5 + sin(fa * 1.3)).px()
            if (c < 1150) { g.set(fx.toDouble(), fy.toDouble(), 1.0); g.dot(fx - 1.0, fy - 1.0, 0.45); g.dot(fx + 1.0, fy - 1.0, 0.45) }
            if (c in 801..1199) { var f = (c - 800) / 200.0; if (f > 1) f = 2 - f; RigArt.line(g, 12.0, 17.0, 12 + (fx - 12) * f, 17 + (fy - 17) * f, 1.0) }
        } else {
            if (c in 801..1199) { g.set(8.0, 7.0, 1.0); g.set(9.0, 6.0, 1.0); g.set(10.0, 5.0, 1.0) } else g.set(10.0, 3.0, 1.0)
        }
    }

    override fun munch(g: PixelGrid, t: Long, o: FaceOpts): Boolean {
        val c = t % 2200
        body(g, BodyOpts(dy = o.dy, t = o.t))
        RigArt.eyes(g, this, if (c > 1300) "happy" else "open", o)
        if (g.size >= 25) {
            val bx = 23 - min(1.0, c / 900.0) * 4
            if (c < 1000) BOLT.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> if (ch == '1') g.set((bx.px() + i).toDouble(), (8 + j + o.dy).toDouble(), 1.0) } }
            if (c in 801..1099) { var f = (c - 800) / 150.0; if (f > 1) f = 2 - f; RigArt.line(g, 12.0, 17.0 + o.dy, 12 + (bx - 12) * f, 17 + (8 - 17) * f + o.dy, 1.0) }
        } else if (c < 900) g.set((11 - (c / 300.0).px()).toDouble(), 5.0, 1.0)
        RigArt.mouth(g, this, if (c > 1100) "chomp" else "smile", o)
        return true
    }
}
