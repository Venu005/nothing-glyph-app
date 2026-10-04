package app.backlit.render.charge

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/** A small round blob with eyes that fills up like a battery; its antenna glows while charging. */
object BuddyStyle : ChargeStyle {
    override val id = "buddy"
    override val label = "Buddy"

    private val RING13 = listOf("..#####..", ".#.....#.", "#.......#", "#.......#", "#.......#", "#.......#", "#.......#", ".#.....#.", "..#####..")
    private val HEART = listOf("101", "111", "010")

    override fun still(size: Int, level: Int) = PixelGrid(size).also { buddy(it, level / 100.0, null, happy = false) }

    override fun plugIn(size: Int, level: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        buddy(g, level / 100.0 * ChargeKit.easeInOut(tMs / 2800.0), tMs, happy = false)
        return ChargeKit.reveal(g, level, tMs)
    }

    override fun charging(size: Int, level: Int, tMs: Long) =
        ChargeKit.periodicReveal(PixelGrid(size).also { buddy(it, level / 100.0, tMs, happy = false) }, level, tMs)

    override fun done(size: Int, tMs: Long): PixelGrid {
        val big = size >= 25
        val hop = if (tMs < 900) (abs(sin(tMs / 900.0 * PI * 2)) * (if (big) -2 else -1)).px() else 0
        val body = PixelGrid(size).also { buddy(it, 1.0, tMs, happy = true) }
        val g = PixelGrid(size)
        for (y in 0 until size) for (x in 0 until size) { val v = body[x, y]; if (v > 0) g.plot(x, y + hop, v) }
        if (tMs > 800) {
            for (i in 0 until (if (big) 2 else 1)) {
                val ph = ((tMs - 800) / 1400.0 + i * 0.5) % 1.0
                val hx = if (big) (if (i == 1) 18 else 4) else 10
                val hy = ((if (big) 9.0 else 4.0) - ph * (if (big) 6 else 3)).px()
                val v = 1 - ph * 0.8
                if (big) HEART.forEachIndexed { r, row -> row.forEachIndexed { k, ch -> if (ch == '1') g.dot((hx + k).toDouble(), (hy + r).toDouble(), v) } }
                else g.dot(hx.toDouble(), hy.toDouble(), v)
            }
        }
        return g
    }

    /** [tMs] null = still: no blink, antenna dim. */
    private fun buddy(g: PixelGrid, lvl: Double, tMs: Long?, happy: Boolean) {
        val big = g.size >= 25
        val cx = if (big) 12 else 6
        val cy = if (big) 13 else 7
        val r = if (big) 8 else 4
        val ring = if (big) ChargeKit.circlePoints(cx, cy, r).distinct()
        else RING13.flatMapIndexed { row, s -> s.mapIndexedNotNull { k, ch -> if (ch == '#') (2 + k) to (3 + row) else null } }
        val onRing = ring.toSet()
        val top = if (big) cy - r + 1 else 4
        val bot = if (big) cy + r - 1 else 10
        val topFill = bot + 1 - (bot - top + 1) * lvl
        for (y in cy - r - 1..cy + r + 1) for (x in cx - r - 1..cx + r + 1) {
            if ((x to y) in onRing) continue
            val inside = if (big) hypot((x - cx).toDouble(), (y - cy).toDouble()) < r - 0.3
            else x in 3..9 && y in 4..10 && !((y == 4 || y == 10) && (x == 3 || x == 9))
            if (inside && y >= topFill - 0.001) g.dot(x.toDouble(), y.toDouble(), 0.35)
        }
        ring.forEach { (x, y) -> g.dot(x.toDouble(), y.toDouble(), 0.85) }

        // antenna with charging tip
        val ay = if (big) cy - r else 3
        if (big) { g.dot(cx.toDouble(), ay - 1.0, 0.7); g.dot(cx.toDouble(), ay - 2.0, 0.7) } else g.dot(cx.toDouble(), ay - 1.0, 0.7)
        val tip = when {
            happy -> 1.0
            tMs == null -> 0.3
            else -> 0.3 + 0.7 * abs(sin(tMs / 350.0))
        }
        g.dot(cx.toDouble(), ay - (if (big) 3.0 else 2.0), tip)

        // face
        val blink = tMs != null && !happy && tMs % 2600 < 140
        if (big) {
            val ey = cy - 2
            for (ex in listOf(8, 15)) {
                when {
                    happy -> { g.set(ex - 1.0, ey + 1.0, 1.0); g.set(ex.toDouble(), ey.toDouble(), 1.0); g.set(ex + 1.0, ey.toDouble(), 1.0); g.set(ex + 2.0, ey + 1.0, 1.0) }
                    blink -> { g.set(ex.toDouble(), ey + 1.0, 1.0); g.set(ex + 1.0, ey + 1.0, 1.0) }
                    else -> for (dx in 0..1) for (dy in 0..1) g.set(ex + dx.toDouble(), ey + dy.toDouble(), 1.0)
                }
            }
            val my = cy + 2
            val mouth = if (happy) listOf(-2 to 0, -1 to 1, 0 to 1, 1 to 1, 2 to 0) else listOf(-1 to 0, 0 to 1, 1 to 0)
            mouth.forEach { (dx, dy) -> g.set((cx + dx).toDouble(), (my + dy).toDouble(), 1.0) }
        } else {
            val ey = cy - 1
            for (ex in listOf(4, 8)) if (!blink || happy) g.set(ex.toDouble(), ey.toDouble(), 1.0)
            val mouth = if (happy) listOf(4 to 8, 5 to 9, 6 to 9, 7 to 9, 8 to 8) else listOf(5 to 8, 6 to 9, 7 to 8)
            mouth.forEach { (x, y) -> g.set(x.toDouble(), y.toDouble(), 1.0) }
        }
    }
}
