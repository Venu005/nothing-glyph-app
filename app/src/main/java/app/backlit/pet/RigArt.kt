package app.backlit.pet

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

enum class EyeMode { HOLE, BRIGHT }
data class EyeSpec(val cx: List<Int>, val cy: Int, val w: Int, val h: Int, val mode: EyeMode)
data class MouthSpec(val x: Int, val y: Int, val scale: Int = 1)
enum class Gill { FAST, NORMAL, SLOW, STILL, DROOP }

data class BodyOpts(
    val dx: Int = 0, val dy: Int = 0, val puff: Boolean = false, val b: Double = RigArt.O, val t: Long = 0,
    val gill: Gill = Gill.NORMAL, val flap: Boolean = false, val tip: Double = 0.6, val footLift: Int = 0,
)

data class FaceOpts(val dx: Int = 0, val dy: Int = 0, val lx: Int = 0, val ly: Int = 0, val t: Long = 0)

/** A pet's body and face anchors. Moods and reactions are drawn by [RigArt] using these. */
interface Rig {
    val eyes25: EyeSpec
    val eyes13: EyeSpec
    val mouth25: MouthSpec
    val mouth13: MouthSpec
    val cheeks25: List<Pair<Int, Int>>
    val cheeks13: List<Pair<Int, Int>>
    val top25: Int
    fun body(g: PixelGrid, o: BodyOpts)
    /** Return true if this rig drew the mouth itself (beaks). */
    fun mouth(g: PixelGrid, kind: String, o: FaceOpts): Boolean = false
    fun signature(g: PixelGrid, t: Long)
    /** Return true if this rig drew its own munch. */
    fun munch(g: PixelGrid, t: Long, o: FaceOpts): Boolean = false
}

/** Shared expression system for rig pets, ported from docs/superpowers/mockups/2026-10-06-pets-full.html. */
object RigArt {
    const val O = 0.9
    const val F = 0.22
    const val L = 0.5
    private val HEART = listOf("101", "111", "010")
    private val Z = listOf("111", "010", "111")
    private val BOLT = listOf("01", "11", "10")
    private val LOOKS = listOf(0 to 0, 1 to 0, 0 to 1, -1 to 0)
    private val MOUTH = mapOf(
        "smile" to listOf(-1 to 0, 0 to 1, 1 to 0), "wide" to listOf(-2 to 0, -1 to 1, 0 to 1, 1 to 1, 2 to 0),
        "flat" to listOf(-1 to 0, 0 to 0, 1 to 0), "frown" to listOf(-1 to 1, 0 to 0, 1 to 1),
        "O" to listOf(0 to -1, -1 to 0, 1 to 0, 0 to 1), "zig" to listOf(-2 to 1, -1 to 0, 0 to 1, 1 to 0, 2 to 1),
        "chomp" to listOf(-1 to 0, 0 to 0, 1 to 0, -1 to 1, 1 to 1), "small" to listOf(0 to 0),
    )

    // ── drawing helpers ──

    fun blob(g: PixelGrid, cx: Double, cy: Double, a: Double, b: Double, fill: Double?, edge: Double?) {
        fun inE(x: Int, y: Int) = ((x - cx) / a).pow(2) + ((y - cy) / b).pow(2) <= 1
        for (y in 0 until g.size) for (x in 0 until g.size) {
            if (!inE(x, y)) continue
            val e = !inE(x + 1, y) || !inE(x - 1, y) || !inE(x, y + 1) || !inE(x, y - 1)
            if (e) { if (edge != null) g.set(x.toDouble(), y.toDouble(), edge) } else if (fill != null) g.set(x.toDouble(), y.toDouble(), fill)
        }
    }

    fun line(g: PixelGrid, x0: Double, y0: Double, x1: Double, y1: Double, v: Double) {
        val st = max(1, ceil(hypot(x1 - x0, y1 - y0) * 2).toInt())
        for (k in 0..st) { val f = k.toDouble() / st; g.set(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, v) }
    }

    fun bm(g: PixelGrid, rows: List<String>, x0: Int, y0: Int, map: Map<Char, Double>) =
        rows.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> map[ch]?.let { g.set((x0 + i).toDouble(), (y0 + j).toDouble(), it) } } }

    fun pts(g: PixelGrid, list: List<Pair<Int, Int>>, dx: Int, dy: Int, v: Double) =
        list.forEach { (x, y) -> g.set((x + dx).toDouble(), (y + dy).toDouble(), v) }

    private fun PixelGrid.s(x: Int, y: Int, v: Double = 1.0) = set(x.toDouble(), y.toDouble(), v)
    private fun float(t: Long, big: Boolean, amp: Double = 1.0): Int = (sin(t / 700.0) * (if (big) 1.0 else 0.6) * amp).px()

    // ── face parts ──

    fun eyes(g: PixelGrid, r: Rig, kind: String, o: FaceOpts) {
        val big = g.size >= 25
        val a = if (big) r.eyes25 else r.eyes13
        val ly = if (big) o.ly else 0
        val on = if (a.mode == EyeMode.HOLE) 0.0 else 1.0
        fun blk(xx: Int, yy: Int, ww: Int, hh: Int, v: Double) { for (i in 0 until ww) for (j in 0 until hh) g.s(xx + i, yy + j, v) }
        a.cx.forEachIndexed { idx, cx ->
            val x = cx + o.dx; val y = a.cy + o.dy; val w = a.w; val h = a.h; val left = idx == 0
            when (kind) {
                "open" -> blk(x + o.lx, y + ly, w, h, on)
                "blink", "closed" -> { val from = if (w > 1) 0 else -1; val to = if (w > 1) w else w + 1; for (i in from until to) g.s(x + i, y + h - 1, if (big) 1.0 else 0.5) }
                "half" -> blk(x + (if (big) 1 else 0), y + h - 1, w, 1, on)
                "happy" -> if (big) { g.s(x - 1, y + h - 1); for (i in 0 until w) g.s(x + i, y + h - 2); g.s(x + w, y + h - 1) } else { g.s(x - 1, y); g.s(x, y - 1); g.s(x + 1, y) }
                "sad" -> { blk(x, y + (if (h > 1) 1 else 0), w, if (h > 1) h - 1 else 1, on); g.s(if (left) x - 1 else x + w, y + h, 0.6) }
                "swirl" -> {
                    var cells = (0 until w).flatMap { i -> (0 until h).map { j -> i to j } }
                    if (cells.size == 1) cells = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
                    val ph = ((o.t / 110) % cells.size).toInt()
                    cells.forEachIndexed { k, (i, j) -> g.s(x + i, y + j, if (k == ph) (if (a.mode == EyeMode.HOLE) L else 0.15) else on) }
                }
                "angry" -> {
                    blk(x, y + h - 1, w, 1, on)
                    if (big) { val bx0 = if (left) x - 1 else x + w; val bx1 = if (left) x + w else x - 1; line(g, bx0.toDouble(), (y - 2).toDouble(), bx1.toDouble(), (y - 1).toDouble(), 1.0) }
                    else g.s(if (left) x + 1 else x - 1, y - 1)
                }
                "wide" -> blk(x - (if (big) 1 else 0), y - (if (big) 1 else 0), w + (if (big) 2 else 0), h + (if (big) 1 else 0), on)
            }
        }
    }

    fun mouth(g: PixelGrid, r: Rig, kind: String, o: FaceOpts) {
        if (r.mouth(g, kind, o)) return
        val m = if (g.size >= 25) r.mouth25 else r.mouth13
        MOUTH.getValue(kind).forEach { (px, py) ->
            val xs = px * m.scale
            g.s(m.x + xs + o.dx, m.y + py + o.dy)
            if (m.scale > 1 && px != 0) g.s(m.x + xs - xs.sign + o.dx, m.y + py + o.dy)
        }
    }

    fun cheeks(g: PixelGrid, r: Rig, o: FaceOpts, v: Double) =
        (if (g.size >= 25) r.cheeks25 else r.cheeks13).forEach { (x, y) -> g.dot((x + o.dx).toDouble(), (y + o.dy).toDouble(), v) }

    private fun hearts(g: PixelGrid, t: Long, count: Int) {
        val big = g.size >= 25
        for (i in 0 until count) {
            val ph = ((t / 1300.0) + i.toDouble() / count) % 1.0
            val hx = if (big) (if (i % 2 == 1) 20 else 2) else (if (i % 2 == 1) 11 else 1)
            val hy = ((if (big) 11.0 else 6.0) - ph * (if (big) 9 else 5)).px()
            if (big) HEART.forEachIndexed { j, row -> row.forEachIndexed { k, ch -> if (ch == '1') g.dot((hx + k).toDouble(), (hy + j).toDouble(), 1 - ph * 0.7) } }
            else g.dot(hx.toDouble(), hy.toDouble(), 1 - ph * 0.7)
        }
    }

    private fun zz(g: PixelGrid, t: Long, r: Rig) {
        val big = g.size >= 25
        for (i in 0..1) {
            val ph = ((t / 2400.0) + i * 0.5) % 1.0
            if (big) Z.forEachIndexed { j, row -> row.forEachIndexed { k, ch -> if (ch == '1') g.dot((18 + (ph * 3).px() + k).toDouble(), ((r.top25 + 1 - ph * 5).px() + j).toDouble(), 1 - ph) } }
            else g.dot((10 + ph.px()).toDouble(), (2 - ph * 2).px().toDouble(), 1 - ph)
        }
    }

    private fun steam(g: PixelGrid, t: Long, r: Rig) {
        val big = g.size >= 25
        for (i in 0..1) {
            val ph = ((t / 900.0) + i * 0.5) % 1.0
            val side = if (i == 1) 1 else -1
            if (big) { val x = 12 + side * (8 + ph * 3); val y = r.top25 - ph * 4; g.dot(x, y, 1 - ph); g.dot(x + side, y, 0.7 * (1 - ph)); g.dot(x, y - 1, 0.6 * (1 - ph)) }
            else g.dot(6 + side * (5 + ph * 1.5), 1 - ph * 2, 1 - ph)
        }
    }

    // ── moods, reactions, AOD ──

    fun frame(r: Rig, size: Int, pose: Pose, now: Long): PixelGrid {
        val g = PixelGrid(size)
        val reaction = pose.reaction
        if (reaction != null) {
            val t = (now - pose.reactionStart).coerceAtLeast(0)
            when (reaction) {
                Reaction.PET -> pet(g, r, t)
                Reaction.YAWN -> yawn(g, r, t)
                Reaction.DIZZY -> dizzy(g, r, t)
                Reaction.ANGRY -> angry(g, r, t)
                Reaction.CALMED -> calmed(g, r, t)
                Reaction.PEEK -> peek(g, r, t)
                Reaction.BOO -> r.signature(g, t)
            }
            return g
        }
        val t = now.coerceAtLeast(0)
        when (pose.base) {
            Base.HAPPY -> happy(g, r, t)
            Base.CONTENT -> content(g, r, t, pose)
            Base.BORED -> bored(g, r, t)
            Base.SAD -> sad(g, r, t)
            Base.ASLEEP -> asleep(g, r, t)
            Base.MUNCH -> munch(g, r, t)
        }
        return g
    }

    fun still(r: Rig, size: Int, pose: Pose, minuteOfHour: Int): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        when (pose.base) {
            Base.ASLEEP -> {
                val o = FaceOpts()
                r.body(g, BodyOpts(b = 0.55, gill = Gill.STILL, tip = 0.15)); eyes(g, r, "closed", o); mouth(g, r, "small", o)
                if (big) Z.forEachIndexed { j, row -> row.forEachIndexed { k, ch -> if (ch == '1') g.dot((19 + k).toDouble(), (r.top25 - 2 + j).toDouble(), 0.8) } }
                else g.dot(10.0, 1.0, 0.8)
            }
            Base.MUNCH -> aodFill(g, r, pose.level / 100.0)
            Base.BORED -> { r.body(g, BodyOpts(gill = Gill.STILL, tip = 0.3)); eyes(g, r, "half", FaceOpts()); mouth(g, r, "flat", FaceOpts()) }
            Base.SAD -> { r.body(g, BodyOpts(gill = Gill.DROOP, tip = 0.2, b = 0.7)); eyes(g, r, "sad", FaceOpts()); mouth(g, r, "frown", FaceOpts()) }
            else -> {
                val (lx, ly) = LOOKS[((minuteOfHour % 4) + 4) % 4]
                r.body(g, BodyOpts(gill = Gill.STILL, tip = 0.5))
                eyes(g, r, "open", FaceOpts(lx = lx, ly = ly))
                mouth(g, r, if (pose.base == Base.HAPPY) "wide" else "smile", FaceOpts())
            }
        }
        return g
    }

    private fun aodFill(g: PixelGrid, r: Rig, lv: Double) {
        val h = PixelGrid(g.size)
        r.body(h, BodyOpts(gill = Gill.STILL, tip = 1.0))
        val fv = (F * 255).toInt()                                         // ChargeKit maps 0.22 → 56
        val ys = (0 until g.size * g.size).filter { abs(h[it % g.size, it / g.size] - fv) <= 1 }.map { it / g.size }
        if (ys.isNotEmpty()) {
            val top = ys.min(); val bot = ys.max()
            val fillTop = (bot - (bot - top) * lv).px()
            for (y in 0 until g.size) for (x in 0 until g.size) if (abs(h[x, y] - fv) <= 1) h.put(x, y, if (y >= fillTop) (0.42 * 255).toInt() else 0)
        }
        for (y in 0 until g.size) for (x in 0 until g.size) if (h[x, y] > 0) g.plot(x, y, h[x, y])
        eyes(g, r, "happy", FaceOpts()); mouth(g, r, "smile", FaceOpts())
    }

    private fun happy(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val c = t % 2600
        val dy = if (c < 500) (-sin(c / 500.0 * PI) * (if (big) 2 else 1)).px() else float(t, big)
        r.body(g, BodyOpts(dy = dy, t = t, flap = c < 500 && (c / 120) % 2 == 0L, gill = Gill.FAST, tip = 1.0))
        val o = FaceOpts(dy = dy); eyes(g, r, "happy", o); mouth(g, r, "wide", o); cheeks(g, r, o, 0.5)
    }

    private fun content(g: PixelGrid, r: Rig, t: Long, pose: Pose) {
        val dy = float(t, g.size >= 25)
        r.body(g, BodyOpts(dy = dy, t = t, tip = if ((t / 600) % 2 == 1L) 1.0 else 0.3))
        val o = FaceOpts(dy = dy, lx = pose.lookX, ly = pose.lookY)
        eyes(g, r, if (t % 3200 < 160) "blink" else "open", o); mouth(g, r, "smile", o); cheeks(g, r, o, 0.3)
    }

    private fun bored(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val dy = float(t, big, 0.5)
        val yawning = t % 7000 > 5800
        r.body(g, BodyOpts(dy = dy, t = t, gill = Gill.SLOW, tip = 0.3))
        val o = FaceOpts(dy = dy); eyes(g, r, if (yawning) "closed" else "half", o); mouth(g, r, if (yawning) "O" else "flat", o)
        if (big && !yawning && (t / 350) % 2 == 1L) g.dot(6.0, 22.0, 0.5)
    }

    private fun sad(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val dy = if (t % 5200 > 4300) 1 else 0
        r.body(g, BodyOpts(dy = dy, t = t, b = 0.7, gill = Gill.DROOP, tip = 0.2))
        val o = FaceOpts(dy = dy); eyes(g, r, "sad", o); mouth(g, r, "frown", o)
        val a = if (big) r.eyes25 else r.eyes13
        val tr = (t % 2400) / 2400.0
        g.dot((a.cx[1] + a.w).toDouble(), a.cy + a.h + 1 + tr * (if (big) 4 else 2), 0.6 * (1 - tr))
    }

    private fun asleep(g: PixelGrid, r: Rig, t: Long) {
        val br = 0.5 + 0.25 * (0.5 + 0.5 * sin(t / 900.0))
        val dy = float(t, g.size >= 25, 0.5)
        r.body(g, BodyOpts(dy = dy, t = t, b = br, gill = Gill.STILL, tip = 0.15))
        val o = FaceOpts(dy = dy); eyes(g, r, "closed", o); mouth(g, r, "small", o); zz(g, t, r)
    }

    private fun pet(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val dy = float(t, big)
        r.body(g, BodyOpts(dy = dy, t = t, gill = Gill.FAST, tip = 1.0, flap = (t / 150) % 2 == 0L))
        val o = FaceOpts(dy = dy); eyes(g, r, "happy", o); mouth(g, r, "wide", o); cheeks(g, r, o, 0.7); hearts(g, t, if (big) 3 else 2)
    }

    private fun dizzy(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val dx = (sin(t / 110.0) * (if (big) 1.4 else 1.0)).px()
        val dy = float(t, big)
        r.body(g, BodyOpts(dx = dx, dy = dy, t = t * 3, gill = Gill.FAST))
        eyes(g, r, "swirl", FaceOpts(dx = dx, dy = dy, t = t)); mouth(g, r, "zig", FaceOpts(dx = dx, dy = dy))
    }

    private fun angry(g: PixelGrid, r: Rig, t: Long) {
        val sh = if ((t / 90) % 2 == 1L && t % 1500 < 500) 1 else 0
        r.body(g, BodyOpts(dx = sh, puff = true, t = t, gill = Gill.FAST, tip = 1.0))
        val o = FaceOpts(dx = sh); eyes(g, r, "angry", o); mouth(g, r, "zig", o); steam(g, t, r)
    }

    private fun calmed(g: PixelGrid, r: Rig, t: Long) {
        if (t % 4000 < 1600) { r.body(g, BodyOpts(t = t, gill = Gill.SLOW)); eyes(g, r, "closed", FaceOpts()); mouth(g, r, "small", FaceOpts()) }
        else pet(g, r, t)
    }

    private fun munch(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val o = FaceOpts(dy = float(t, big), t = t)
        if (r.munch(g, t, o)) return
        val c = t % 2200
        r.body(g, BodyOpts(dy = o.dy, t = t))
        eyes(g, r, if (c > 1000) "happy" else "open", o)
        if (c < 1000) {
            val m = if (big) r.mouth25 else r.mouth13
            val x = (if (big) 23.0 else 12.0) - c / 1000.0 * (if (big) (23 - m.x - 2) else (12 - m.x - 1))
            if (big) BOLT.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> if (ch == '1') g.s(x.px() + i, m.y - 1 + j + o.dy) } }
            else g.s(x.px(), m.y + o.dy)
            mouth(g, r, "O", o)
        } else mouth(g, r, if ((c / 180) % 2 == 1L) "chomp" else "flat", o)
    }

    private fun peek(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val c = t % 3900
        val rise = 1200L
        val off = if (c < rise) ((1 - c / rise.toDouble()) * (if (big) 14 else 8)).px() else 0
        r.body(g, BodyOpts(dy = off, t = t, tip = 1.0))
        val o = FaceOpts(dy = off); eyes(g, r, if (c < rise) "open" else "wide", o); mouth(g, r, if (c < rise) "small" else "O", o)
        if (c > rise && (c / 250) % 2 == 1L) { if (big) { for (y in 3..6) g.dot(22.0, y.toDouble(), 1.0); g.dot(22.0, 8.0, 1.0) } else { g.dot(11.0, 3.0, 1.0); g.dot(11.0, 5.0, 1.0) } }
    }

    private fun yawn(g: PixelGrid, r: Rig, t: Long) {
        val c = t % 3600
        val dy = float(t, g.size >= 25, 0.5)
        r.body(g, BodyOpts(dy = dy, t = t, gill = Gill.SLOW, tip = 0.3))
        val o = FaceOpts(dy = dy)
        if (c < 1800) { eyes(g, r, "closed", o); mouth(g, r, "O", o) } else { eyes(g, r, "half", o); mouth(g, r, "small", o) }
    }

}
