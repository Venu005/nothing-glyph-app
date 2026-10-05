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
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px

object PenguinRig : Rig {
    override val eyes25 = EyeSpec(listOf(10, 14), 9, 1, 2, EyeMode.HOLE)
    override val eyes13 = EyeSpec(listOf(5, 7), 5, 1, 1, EyeMode.HOLE)
    override val mouth25 = MouthSpec(12, 12)
    override val mouth13 = MouthSpec(6, 6)
    override val cheeks25 = listOf(9 to 12, 15 to 12)
    override val cheeks13 = emptyList<Pair<Int, Int>>()
    override val top25 = 4

    override fun mouth(g: PixelGrid, kind: String, o: FaceOpts): Boolean {
        val open = kind == "O" || kind == "chomp" || kind == "wide" || kind == "zig"
        if (g.size >= 25) {
            if (open) { RigArt.pts(g, listOf(11 to 12, 12 to 12, 13 to 12, 11 to 14, 12 to 14, 13 to 14), o.dx, o.dy, 1.0); g.set(12.0 + o.dx, 13.0 + o.dy, 0.0) }
            else RigArt.pts(g, listOf(11 to 12, 12 to 12, 13 to 12, 12 to 13), o.dx, o.dy, 1.0)
        } else {
            g.set(6.0 + o.dx, 6.0 + o.dy, 1.0); if (open) g.set(6.0 + o.dx, 7.0 + o.dy, 1.0)
        }
        return true
    }

    override fun body(g: PixelGrid, o: BodyOpts) {
        val k = if (o.puff) 1.08 else 1.0
        if (g.size >= 25) {
            RigArt.blob(g, 12.0 + o.dx, 13.0 + o.dy, 6.5 * k, 8.5 * k, F, o.b)
            RigArt.blob(g, 10.0 + o.dx, 9.5 + o.dy, 2.6, 2.6, L, null)
            RigArt.blob(g, 14.0 + o.dx, 9.5 + o.dy, 2.6, 2.6, L, null)
            RigArt.blob(g, 12.0 + o.dx, 15.5 + o.dy, 4.3, 5.5, L, null)
            if (o.flap) { RigArt.line(g, 5.0 + o.dx, 12.0 + o.dy, 2.0 + o.dx, 8.0 + o.dy, o.b); RigArt.line(g, 19.0 + o.dx, 12.0 + o.dy, 22.0 + o.dx, 8.0 + o.dy, o.b) }
            else { RigArt.line(g, 5.0 + o.dx, 11.0 + o.dy, 4.0 + o.dx, 16.0 + o.dy, o.b); RigArt.line(g, 19.0 + o.dx, 11.0 + o.dy, 20.0 + o.dx, 16.0 + o.dy, o.b) }
            RigArt.pts(g, listOf(9 to 22, 10 to 22, 11 to 22, 13 to 22, 14 to 22, 15 to 22), o.dx, o.dy + o.footLift, 1.0)
        } else {
            RigArt.bm(g, listOf("..ooooo..", ".oLLoLLo.", ".oLLoLLo.", "ooLLLLLoo", ".oLLLLLo.", ".oLLLLLo.", "..ooooo..", "..X...X.."), 2 + o.dx, 3 + o.dy, mapOf('o' to o.b, 'L' to L, 'X' to 1.0))
            if (o.flap) { g.set(1.0 + o.dx, 5.0 + o.dy, o.b); g.set(11.0 + o.dx, 5.0 + o.dy, o.b) }
        }
    }

    override fun signature(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val sx = (-15 + (t % 2600) / 2600.0 * 30 * (if (big) 1.0 else 0.6)).px()
        if (big) {
            RigArt.blob(g, 12.0 + sx, 16.0, 8.0, 4.2, F, O); RigArt.blob(g, 13.0 + sx, 17.0, 5.0, 2.4, L, null)
            g.set(20.0 + sx, 15.0, 1.0); g.set(21.0 + sx, 16.0, 1.0); g.set(17.0 + sx, 14.0, 0.0)
            g.dot(3.0 + sx, 19.0, 0.35); g.dot(1.0 + sx, 20.0, 0.25)
        } else {
            RigArt.blob(g, 6.0 + sx, 9.0, 4.2, 2.3, F, O); g.set(10.0 + sx, 9.0, 1.0)
        }
    }
}
