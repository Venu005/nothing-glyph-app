package app.backlit.pet.rigs

import app.backlit.pet.BodyOpts
import app.backlit.pet.EyeMode
import app.backlit.pet.EyeSpec
import app.backlit.pet.FaceOpts
import app.backlit.pet.MouthSpec
import app.backlit.pet.Rig
import app.backlit.pet.RigArt
import app.backlit.pet.RigArt.F
import app.backlit.pet.RigArt.O
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import kotlin.math.min
import kotlin.math.sign

object RobotRig : Rig {
    override val eyes25 = EyeSpec(listOf(9, 14), 10, 2, 2, EyeMode.BRIGHT)
    override val eyes13 = EyeSpec(listOf(5, 7), 5, 1, 1, EyeMode.BRIGHT)
    override val mouth25 = MouthSpec(12, 14, 1)
    override val mouth13 = MouthSpec(6, 7)
    override val cheeks25 = listOf(8 to 13, 16 to 13)
    override val cheeks13 = emptyList<Pair<Int, Int>>()
    override val top25 = 3

    override fun body(g: PixelGrid, o: BodyOpts) {
        val dim = o.b / O
        if (g.size >= 25) {
            val p = if (o.puff) 1 else 0
            for (y in 6 - p..17 + p) for (x in 5 - p..19 + p) {
                val edge = y == 6 - p || y == 17 + p || x == 5 - p || x == 19 + p
                val corner = (y == 6 - p || y == 17 + p) && (x == 5 - p || x == 19 + p)
                if (!corner) g.set((x + o.dx).toDouble(), (y + o.dy).toDouble(), if (edge) o.b else F)
            }
            for (y in 8..15) for (x in 7..17) g.set((x + o.dx).toDouble(), (y + o.dy).toDouble(), if (y == 8 || y == 15 || x == 7 || x == 17) 0.45 * dim else 0.0)
            RigArt.pts(g, listOf(12 to 5, 12 to 4, 4 to 11, 4 to 12, 20 to 11, 20 to 12), o.dx, o.dy, 0.7 * dim)
            g.set(12.0 + o.dx, 3.0 + o.dy, o.tip); g.set(12.0 + o.dx, 18.0 + o.dy, 0.6 * dim)
            for (y in 19..22) for (x in 8..16) g.set((x + o.dx).toDouble(), (y + o.dy).toDouble(), if (y == 19 || y == 22 || x == 8 || x == 16) o.b else F)
        } else {
            RigArt.bm(g, listOf("...o...", "...X...", "ooooooo", "oFFFFFo", "oFFFFFo", "oFFFFFo", "oFFFFFo", "ooooooo", "..ooo.."), 3 + o.dx, 1 + o.dy, mapOf('o' to o.b, 'F' to F, 'X' to o.tip))
        }
    }

    override fun signature(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val h = PixelGrid(g.size)
        body(h, BodyOpts(tip = 1.0)); RigArt.eyes(h, this, "open", FaceOpts()); RigArt.mouth(h, this, "flat", FaceOpts())
        val n = g.size
        val seed = t / 110
        for (y in 0 until n) {
            val r = ((y * 73 + seed * 31) % 11).toInt()
            var off = if (r < 2) (if (r == 1) 2 else -2) else if (r == 2) 1 else 0
            if (!big) off = off.sign
            for (x in 0 until n) { val sx = x - off; if (sx in 0 until n && h[sx, y] > 0) g.plot(x, y, h[sx, y]) }
        }
    }

    override fun munch(g: PixelGrid, t: Long, o: FaceOpts): Boolean {
        val c = t % 2400
        body(g, BodyOpts(dy = o.dy, tip = if ((t / 200) % 2 == 1L) 1.0 else 0.4))
        RigArt.eyes(g, this, if (c > 1200) "happy" else "open", o)
        RigArt.mouth(g, this, if (c > 1200) "wide" else "flat", o)
        if (g.size >= 25) {
            val reach = min(1.0, c / 900.0)
            RigArt.line(g, 24.0, 15.0, 24 - reach * 3, 12.0, 0.8)
            if (c > 900) g.dot(20.0, 12.0, if ((t / 120) % 2 == 1L) 1.0 else 0.5)
        } else if (c > 600) g.set(11.0, 6.0, if ((t / 150) % 2 == 1L) 1.0 else 0.4)
        return true
    }
}
