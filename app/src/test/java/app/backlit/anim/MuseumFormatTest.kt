package app.backlit.anim

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuseumFormatTest {

    private fun leds(size: Int) = PixelGrid.ledCount(size)
    private fun file(v: Int, frames: List<Pair<Int?, List<Int>>>): String =
        """{"v":$v,"frames":[""" + frames.joinToString(",") { (d, p) ->
            (if (d != null) """{"d":$d,"p":[""" else """{"p":[""") + p.joinToString(",") + "]}"
        } + "]}"

    private fun ok(r: MuseumFormat.Result) = (r as MuseumFormat.Result.Ok).animation

    @Test
    fun parsesPhone3FileWithDurations() {
        val first = List(leds(25)) { if (it == 0) 255 else 0 }
        val a = ok(MuseumFormat.parse(file(1, listOf(200 to first, null to List(leds(25)) { 0 })), "import:a", "A"))
        assertEquals(25, a.sourceSize)
        assertEquals(255, a.frames25[0][9, 0])          // first LED of 25×25 is (9, 0)
        assertEquals(listOf(200L, 100L), a.durations)
        assertEquals(300L, a.loopMs)
        assertEquals(0, a.frame(25, 250)[9, 0])         // t = 250 → second frame
    }

    @Test
    fun parsesPhone4aProFile() {
        val first = List(leds(13)) { if (it == 0) 200 else 0 }
        val a = ok(MuseumFormat.parse(file(4, listOf(null to first)), "import:b", "B"))
        assertEquals(13, a.sourceSize)
        assertEquals(200, a.frames13[0][4, 0])           // first LED of 13×13 is (4, 0)
    }

    @Test
    fun clampsValuesAndDurations() {
        val p = List(leds(25)) { when (it) { 0 -> 300; 1 -> -5; else -> 0 } }
        val a = ok(MuseumFormat.parse(file(1, listOf(1 to p, 99999 to p)), "import:c", "C"))
        assertEquals(255, a.frames25[0][9, 0])
        assertEquals(0, a.frames25[0][10, 0])
        assertEquals(listOf(20L, 5000L), a.durations)
    }

    @Test
    fun rejectsBadFiles() {
        val good = List(leds(25)) { 0 }
        for (bad in listOf(
            "hello",
            file(2, listOf(null to good)),
            file(1, listOf(null to List(leds(25) - 1) { 0 })),
            """{"v":1,"frames":[]}""",
            file(1, List(601) { null to good }),
        )) assertEquals(bad.take(30), MuseumFormat.Result.Invalid, MuseumFormat.parse(bad, "import:x", "X"))
    }

    @Test
    fun roundTripsThroughJson() {
        val p = List(leds(13)) { it % 256 }
        val a = ok(MuseumFormat.parse(file(4, listOf(150 to p)), "import:d", "D"))
        val b = ok(MuseumFormat.parse(MuseumFormat.toJson(a), "import:d", "D"))
        assertEquals(a.frames13, b.frames13)
        assertEquals(a.durations, b.durations)
        assertEquals(13, b.sourceSize)
    }

    @Test
    fun resamplingKeepsCentreDotCentred() {
        val big = PixelGrid(25).apply { plot(12, 12, 255) }
        val small = MuseumFormat.resample(big, 13)
        assertEquals(255, small[6, 6]); assertEquals(0, small[5, 6])
        val up = MuseumFormat.resample(PixelGrid(13).apply { plot(6, 6, 255) }, 25)
        assertEquals(255, up[12, 12]); assertEquals(0, up[11, 12])
    }

    @Test
    fun resamplingAFullFrameStaysBright() {
        val full = PixelGrid(13).apply { for (y in 0 until 13) for (x in 0 until 13) plot(x, y, 255) }
        val up = MuseumFormat.resample(full, 25)
        assertTrue(up[12, 12] == 255 && up[12, 1] > 0)
    }
}
