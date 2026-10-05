package app.backlit.pet

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** The ghost pet, ported from the approved mockup. Brightness 0..1 like the mockup; ChargeKit maps it to 0..255. */
object GhostArt {
    private val HEART = listOf("101", "111", "010")
    private val Z = listOf("111", "010", "111")
    private val BOLT = listOf("01", "11", "10")
    private val LOOKS = listOf(0 to 0, 1 to 0, 0 to 1, -1 to 0)

    private data class Body(val dx: Int = 0, val dy: Int = 0, val b: Double = 0.85, val puff: Boolean = false, val hemSpeed: Long = 200, val droop: Boolean = false)

    fun frame(size: Int, pose: Pose, now: Long): PixelGrid {
        val g = PixelGrid(size)
        val r = pose.reaction
        if (r != null) {
            val t = (now - pose.reactionStart).coerceAtLeast(0)
            when (r) {
                Reaction.PET -> pet(g, t)
                Reaction.YAWN -> yawn(g, t)
                Reaction.DIZZY -> dizzy(g, t)
                Reaction.ANGRY -> angry(g, t)
                Reaction.CALMED -> calmed(g, t)
                Reaction.PEEK -> peek(g, t)
                Reaction.BOO -> boo(g, t)
            }
            return g
        }
        val t = now.coerceAtLeast(0)
        when (pose.base) {
            Base.HAPPY -> happy(g, t, pose.lean)
            Base.CONTENT -> content(g, t, pose)
            Base.BORED -> bored(g, t)
            Base.SAD -> sad(g, t)
            Base.ASLEEP -> sleep(g, t)
            Base.MUNCH -> munch(g, t, pose.lean)
        }
        return g
    }

    fun still(size: Int, pose: Pose, minuteOfHour: Int): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        when (pose.base) {
            Base.ASLEEP -> {
                body(g, 0, Body(b = 0.55, hemSpeed = 0)); eyes(g, "closed"); mouth(g, "small")
                if (big) bm(g, Z, 19, 3, 0.8) else g.p(10, 1, 0.8)
            }
            Base.MUNCH -> {
                body(g, 0, Body(hemSpeed = 0))
                val lv = pose.level / 100.0
                if (big) {
                    val fillTop = (20 - 9 * lv).px()
                    for (y in max(fillTop, 9)..20) for (x in 6..18) g.p(x, y, 0.3)
                } else {
                    val fillTop = (10 - 5 * lv).px()
                    for (y in fillTop..10) for (x in 3..9) g.p(x, y, 0.3)
                }
                eyes(g, "happy"); mouth(g, "smile")
            }
            else -> {
                body(g, 0, Body(hemSpeed = 0))
                val (lx, ly) = LOOKS[((minuteOfHour % 4) + 4) % 4]
                eyes(g, if (pose.base == Base.SAD) "sad" else "open", px = lx, py = if (big) ly else 0)
                mouth(g, when (pose.base) { Base.HAPPY -> "wide"; Base.BORED -> "flat"; Base.SAD -> "frown"; else -> "small" })
            }
        }
        return g
    }

    // ── resting faces ──

    private fun happy(g: PixelGrid, t: Long, lean: Int) {
        val big = g.size >= 25
        val c = t % 2600
        val hop = if (c < 500) (-sin(c / 500.0 * PI) * (if (big) 2 else 1)).px() else float(t, big)
        body(g, t, Body(dx = lean, dy = hop, hemSpeed = 120)); eyes(g, "happy", dx = lean, dy = hop); mouth(g, "wide", dx = lean, dy = hop); blush(g, dx = lean, dy = hop)
    }

    private fun content(g: PixelGrid, t: Long, pose: Pose) {
        val dy = float(t, g.size >= 25)
        body(g, t, Body(dx = pose.lean, dy = dy))
        eyes(g, if (t % 3200 < 160) "blink" else "open", dx = pose.lean, dy = dy, px = pose.lookX, py = pose.lookY)
        mouth(g, "small", dx = pose.lean, dy = dy)
    }

    private fun bored(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val dy = float(t, big, 0.5)
        val yawning = t % 7000 > 5800
        body(g, t, Body(dy = dy, hemSpeed = 450))
        eyes(g, if (yawning) "closed" else "half", dy = dy, px = if (big) 1 else 0)
        mouth(g, if (yawning) "O" else "flat", dy = dy)
    }

    private fun sad(g: PixelGrid, t: Long) {
        val sigh = t % 5200 > 4300
        val dy = if (sigh) 1 else 0
        body(g, t, Body(dy = dy, hemSpeed = 0, b = 0.7, droop = true)); eyes(g, "sad", dy = dy); mouth(g, "frown", dy = dy)
        val tr = (t % 2400) / 2400.0
        if (g.size >= 25) g.dot(15.0, 13 + tr * 4, 0.6 * (1 - tr)) else g.dot(9.0, 7 + tr * 3, 0.6 * (1 - tr))
    }

    private fun sleep(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val br = 0.5 + 0.25 * (0.5 + 0.5 * sin(t / 900.0))
        val dy = float(t, big, 0.5)
        body(g, t, Body(dy = dy, b = br, hemSpeed = 600)); eyes(g, "closed", dy = dy); mouth(g, "small", dy = dy)
        for (i in 0..1) {
            val ph = ((t / 2400.0) + i * 0.5) % 1.0
            if (big) bm(g, Z, 18 + (ph * 3).px(), (5 - ph * 5).px(), 1 - ph) else g.p(10 + ph.px(), (2 - ph * 2).px(), 1 - ph)
        }
    }

    private fun munch(g: PixelGrid, t: Long, lean: Int) {
        val big = g.size >= 25
        val c = t % 2200
        val dy = float(t, big)
        body(g, t, Body(dx = lean, dy = dy, hemSpeed = 120))
        eyes(g, if (c > 1000) "happy" else "open", dx = lean, dy = dy)
        if (c < 1000) {
            val x = (if (big) 23.0 else 12.0) - c / 1000.0 * (if (big) 9 else 5)
            if (big) bm(g, BOLT, x.px(), 14 + dy, 1.0) else g.p(x.px(), 8 + dy, 1.0)
            mouth(g, "O", dx = lean, dy = dy)
        } else {
            mouth(g, if ((c / 180) % 2 == 1L) "chomp" else "flat", dx = lean, dy = dy)
            if (big) { g.dot(16 + (c / 60.0) % 3, 17.0 + dy, 0.4); g.dot(8 - (c / 80.0) % 2, 17.0 + dy, 0.35) }
        }
    }

    // ── reactions ──

    private fun pet(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val dy = float(t, big)
        body(g, t, Body(dy = dy, hemSpeed = 100)); eyes(g, "happy", dy = dy); mouth(g, "wide", dy = dy); blush(g, dy = dy, v = 0.6)
        hearts(g, t, if (big) 3 else 2)
    }

    private fun dizzy(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val dx = (sin(t / 110.0) * (if (big) 1.4 else 1.0)).px()
        val dy = float(t, big)
        body(g, t, Body(dx = dx, dy = dy, hemSpeed = 60)); eyes(g, "swirl", dx = dx, dy = dy, t = t); mouth(g, "zig", dx = dx, dy = dy)
    }

    private fun angry(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val sh = if ((t / 90) % 2 == 1L && t % 1500 < 500) 1 else 0
        body(g, t, Body(dx = sh, puff = true, hemSpeed = 70)); eyes(g, "angry", dx = sh); mouth(g, "zig", dx = sh)
        for (i in 0..1) {
            val ph = ((t / 900.0) + i * 0.5) % 1.0
            val side = if (i == 1) 1 else -1
            if (big) {
                val x = 12 + side * (8 + ph * 3)
                val y = 4 - ph * 4
                g.dot(x, y, 1 - ph); g.dot(x + side, y, 0.7 * (1 - ph)); g.dot(x, y - 1, 0.6 * (1 - ph))
            } else {
                g.dot(6 + side * (5 + ph * 1.5), 1 - ph * 2, 1 - ph)
            }
        }
    }

    private fun calmed(g: PixelGrid, t: Long) {
        if (t % 4000 < 1600) {
            body(g, t, Body(hemSpeed = 300)); eyes(g, "closed"); mouth(g, "small")
            if (g.size >= 25) { g.s(8, 9); g.s(9, 9); g.s(15, 9); g.s(16, 9) }
        } else {
            pet(g, t)
        }
    }

    private fun peek(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val c = t % Reaction.PEEK.ms
        val rise = 1200L                                              // slow enough to see him peek up
        val off = if (c < rise) ((1 - c / rise.toDouble()) * (if (big) 14 else 8)).px() else 0
        body(g, t, Body(dy = off, hemSpeed = 80)); eyes(g, if (c < rise) "open" else "wide", dy = off); mouth(g, if (c < rise) "small" else "O", dy = off)
        if (c > rise && (c / 250) % 2 == 1L) {
            if (big) { for (y in 3..6) g.p(21, y, 1.0); g.p(21, 8, 1.0) } else { g.p(11, 3, 1.0); g.p(11, 5, 1.0) }
        }
    }

    private fun yawn(g: PixelGrid, t: Long) {
        val c = t % 3600
        val dy = float(t, g.size >= 25, 0.5)
        body(g, t, Body(dy = dy, hemSpeed = 500))
        if (c < 1800) { eyes(g, "closed", dy = dy); mouth(g, "O", dy = dy) } else { eyes(g, "half", dy = dy); mouth(g, "small", dy = dy) }
    }

    private fun boo(g: PixelGrid, t: Long) {
        val c = t % 2600
        body(g, t, Body(puff = c < 900, hemSpeed = 60)); eyes(g, "wide"); mouth(g, "O")
    }

    // ── parts ──

    private fun body(g: PixelGrid, t: Long, o: Body) {
        val step = if (o.hemSpeed > 0) (t / o.hemSpeed).toInt() else 0
        if (g.size >= 25) {
            val r = if (o.puff) 8 else 7
            val l = 12 - r
            val rr = 12 + r
            val top = 11
            ChargeKit.circlePoints(12 + o.dx, top + o.dy, r).forEach { (x, y) -> if (y <= top + o.dy) g.p(x, y, o.b) }
            val bot = 19 + if (o.droop) 1 else 0
            ChargeKit.line(g, (l + o.dx).toDouble(), (top + o.dy).toDouble(), (l + o.dx).toDouble(), (bot + o.dy).toDouble(), o.b)
            ChargeKit.line(g, (rr + o.dx).toDouble(), (top + o.dy).toDouble(), (rr + o.dx).toDouble(), (bot + o.dy).toDouble(), o.b)
            for (x in l..rr) { val w = if ((x + step) % 4 < 2) 0 else 1; g.p(x + o.dx, bot + 1 + o.dy + w, o.b) }
        } else {
            val r = if (o.puff) 5 else 4
            val l = 6 - r
            val rr = 6 + r
            val top = 6
            ChargeKit.circlePoints(6 + o.dx, top + o.dy, r).forEach { (x, y) -> if (y <= top + o.dy) g.p(x, y, o.b) }
            ChargeKit.line(g, (l + o.dx).toDouble(), (top + o.dy).toDouble(), (l + o.dx).toDouble(), (10 + o.dy).toDouble(), o.b)
            ChargeKit.line(g, (rr + o.dx).toDouble(), (top + o.dy).toDouble(), (rr + o.dx).toDouble(), (10 + o.dy).toDouble(), o.b)
            for (x in l..rr) { val w = if ((x + step) % 2 != 0) 1 else 0; g.p(x + o.dx, 11 + o.dy - w, o.b) }
        }
    }

    private fun eyes(g: PixelGrid, kind: String, dx: Int = 0, dy: Int = 0, px: Int = 0, py: Int = 0, t: Long = 0) {
        if (g.size >= 25) {
            val ey = 10 + dy
            for (ex in listOf(9 + dx, 14 + dx)) {
                val left = ex < 12 + dx
                when (kind) {
                    "open" -> for (a in 0..1) for (c in 0..2) g.s(ex + a + px, ey + c + py)
                    "blink", "closed" -> { g.s(ex, ey + 2); g.s(ex + 1, ey + 2) }
                    "half" -> { g.s(ex + px, ey + 2); g.s(ex + 1 + px, ey + 2); g.s(ex + px, ey + 1); g.s(ex + 1 + px, ey + 1) }
                    "happy" -> { g.s(ex - 1, ey + 2); g.s(ex, ey + 1); g.s(ex + 1, ey + 1); g.s(ex + 2, ey + 2) }
                    "sad" -> if (left) { g.s(ex + 1, ey + 1); g.s(ex, ey + 2); g.s(ex - 1, ey + 2, 0.5) } else { g.s(ex, ey + 1); g.s(ex + 1, ey + 2); g.s(ex + 2, ey + 2, 0.5) }
                    "swirl" -> {
                        val ph = ((t / 110) % 6).toInt()
                        listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2, 0 to 2, 0 to 1).forEachIndexed { k, (a, c) -> g.s(ex + a, ey + c, if (k == ph) 0.15 else 1.0) }
                    }
                    "angry" -> for (a in 0..1) for (c in 1..2) g.s(ex + a, ey + c)
                    "wide" -> for (a in -1..2) for (c in -1..2) if (!((a == -1 || a == 2) && (c == -1 || c == 2))) g.s(ex + a, ey + c)
                }
            }
            if (kind == "angry") { g.s(8 + dx, 9 + dy); g.s(9 + dx, 9 + dy); g.s(10 + dx, 10 + dy); g.s(16 + dx, 9 + dy); g.s(15 + dx, 9 + dy); g.s(14 + dx, 10 + dy) }
        } else {
            val ey = 6 + dy
            for (ex in listOf(4 + dx, 8 + dx)) {
                val left = ex < 6 + dx
                when (kind) {
                    "open", "angry", "wide" -> g.s(ex + px, ey + py)
                    "blink", "closed", "half" -> g.s(ex, ey, 0.45)
                    "happy" -> { g.s(ex - 1, ey); g.s(ex, ey - 1); g.s(ex + 1, ey) }
                    "sad" -> { g.s(ex, ey); g.s(ex + if (left) -1 else 1, ey + 1, 0.6) }
                    "swirl" -> {
                        val ph = ((t / 120) % 4).toInt()
                        val (ddx, ddy) = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)[ph]
                        g.s(ex, ey, 0.4); g.s(ex + ddx, ey + ddy)
                    }
                }
            }
            if (kind == "angry") { g.s(5 + dx, 5 + dy); g.s(7 + dx, 5 + dy) }
        }
    }

    private val MOUTH25 = mapOf(
        "small" to listOf(12 to 15), "smile" to listOf(11 to 15, 12 to 16, 13 to 15),
        "wide" to listOf(10 to 15, 11 to 16, 12 to 16, 13 to 16, 14 to 15), "flat" to listOf(11 to 15, 12 to 15, 13 to 15),
        "frown" to listOf(11 to 16, 12 to 15, 13 to 16), "O" to listOf(11 to 15, 12 to 14, 13 to 15, 11 to 16, 13 to 16, 12 to 17),
        "zig" to listOf(10 to 16, 11 to 15, 12 to 16, 13 to 15, 14 to 16), "chomp" to listOf(11 to 15, 12 to 15, 13 to 15, 11 to 16, 13 to 16),
    )
    private val MOUTH13 = mapOf(
        "small" to listOf(6 to 8), "smile" to listOf(5 to 8, 6 to 9, 7 to 8), "wide" to listOf(4 to 8, 5 to 9, 6 to 9, 7 to 9, 8 to 8),
        "flat" to listOf(5 to 8, 6 to 8, 7 to 8), "frown" to listOf(5 to 9, 6 to 8, 7 to 9), "O" to listOf(6 to 8, 5 to 9, 7 to 9),
        "zig" to listOf(5 to 9, 6 to 8, 7 to 9), "chomp" to listOf(5 to 8, 6 to 8, 7 to 8),
    )

    private fun mouth(g: PixelGrid, kind: String, dx: Int = 0, dy: Int = 0) =
        (if (g.size >= 25) MOUTH25 else MOUTH13).getValue(kind).forEach { (x, y) -> g.s(x + dx, y + dy) }

    private fun blush(g: PixelGrid, dx: Int = 0, dy: Int = 0, v: Double = 0.35) {
        if (g.size >= 25) { g.p(7 + dx, 14 + dy, v); g.p(17 + dx, 14 + dy, v) } else { g.p(3 + dx, 8 + dy, v); g.p(9 + dx, 8 + dy, v) }
    }

    private fun hearts(g: PixelGrid, t: Long, count: Int) {
        val big = g.size >= 25
        for (i in 0 until count) {
            val ph = ((t / 1300.0) + i.toDouble() / count) % 1.0
            val hx = if (big) (if (i % 2 == 1) 20 else 2) else (if (i % 2 == 1) 11 else 1)
            val hy = (if (big) 11.0 else 6.0) - ph * (if (big) 9 else 5)
            if (big) bm(g, HEART, hx, hy.px(), 1 - ph * 0.7) else g.p(hx, hy.px(), 1 - ph * 0.7)
        }
    }

    private fun float(t: Long, big: Boolean, amp: Double = 1.0): Int = (sin(t / 700.0) * (if (big) 1.0 else 0.6) * amp).px()

    private fun bm(g: PixelGrid, rows: List<String>, x0: Int, y0: Int, v: Double) =
        rows.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> if (ch == '1') g.p(x0 + i, y0 + j, v) } }

    private fun PixelGrid.p(x: Int, y: Int, v: Double) = dot(x.toDouble(), y.toDouble(), v)
    private fun PixelGrid.s(x: Int, y: Int, v: Double = 1.0) = set(x.toDouble(), y.toDouble(), v)
}
