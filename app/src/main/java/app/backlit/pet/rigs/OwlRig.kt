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
import app.backlit.pet.RigArt.O
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.sin

object OwlRig : Rig {
    override val eyes25 = EyeSpec(listOf(9, 15), 11, 1, 2, EyeMode.HOLE)
    override val eyes13 = EyeSpec(listOf(4, 8), 5, 1, 1, EyeMode.HOLE)
    override val mouth25 = MouthSpec(12, 13)
    override val mouth13 = MouthSpec(6, 6)
    override val cheeks25 = emptyList<Pair<Int, Int>>()
    override val cheeks13 = emptyList<Pair<Int, Int>>()
    override val top25 = 5

    override fun mouth(g: PixelGrid, kind: String, o: FaceOpts): Boolean {
        val open = kind == "O" || kind == "chomp" || kind == "wide" || kind == "zig"
        if (g.size >= 25) {
            g.set(12.0 + o.dx, 13.0 + o.dy, 1.0); g.set(12.0 + o.dx, 14.0 + o.dy, 1.0)
            if (open) { g.set(11.0 + o.dx, 15.0 + o.dy, 1.0); g.set(13.0 + o.dx, 15.0 + o.dy, 1.0) }
        } else { g.set(6.0 + o.dx, 6.0 + o.dy, 1.0); if (open) g.set(6.0 + o.dx, 7.0 + o.dy, 0.7) }
        return true
    }

    override fun body(g: PixelGrid, o: BodyOpts) {
        val k = if (o.puff) 1.08 else 1.0
        if (g.size >= 25) {
            RigArt.blob(g, 12.0 + o.dx, 14.0 + o.dy, 7.5 * k, 8.5 * k, F, o.b)
            RigArt.pts(g, listOf(6 to 5, 7 to 6, 18 to 5, 17 to 6), o.dx, o.dy, o.b)
            for (ex in listOf(9, 15)) RigArt.blob(g, (ex + o.dx).toDouble(), 11.5 + o.dy, 3.0, 3.0, L, if (o.b >= O) 1.0 else o.b)
            RigArt.pts(g, listOf(10 to 17, 11 to 18, 13 to 18, 14 to 17, 11 to 20, 12 to 21, 13 to 20), o.dx, o.dy, 0.6 * (o.b / O))
            if (o.flap) { RigArt.line(g, 4.0 + o.dx, 13.0 + o.dy, 1.0 + o.dx, 10.0 + o.dy, o.b); RigArt.line(g, 20.0 + o.dx, 13.0 + o.dy, 23.0 + o.dx, 10.0 + o.dy, o.b) }
            RigArt.pts(g, listOf(10 to 23, 11 to 23, 13 to 23, 14 to 23), o.dx, o.dy, 1.0)
        } else {
            RigArt.bm(g, listOf("o.......o", ".ooooooo.", "oLLoFoLLo", "oLLLFLLLo", "oLLoFoLLo", "oFFFFFFFo", ".oFdFdFo.", "..ooooo.."), 2 + o.dx, 2 + o.dy, mapOf('o' to o.b, 'L' to L, 'F' to F, 'd' to 0.6))
            if (o.flap) { g.set(1.0 + o.dx, 6.0 + o.dy, o.b); g.set(11.0 + o.dx, 6.0 + o.dy, o.b) }
        }
    }

    override fun signature(g: PixelGrid, t: Long) {
        val look = (sin(t / 420.0) * 1.4).px().coerceIn(-1, 1)
        body(g, BodyOpts()); RigArt.eyes(g, this, "open", FaceOpts(lx = look)); RigArt.mouth(g, this, "small", FaceOpts())
    }
}
