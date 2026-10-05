package app.backlit.studio

import app.backlit.anim.ImportedAnimation
import app.backlit.anim.MuseumFormat
import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingCodecTest {

    private fun sample(): Drawing {
        var s = EditorState.of(Drawing.blank(25, "Wink")).circle(12, 12, 8, 3, false).fill(12, 12, 1, false)
        s = s.addFrame().line(8, 10, 10, 10, 2, true).setHold(3).setFps(10)
        return s.doc
    }

    @Test
    fun shadeThresholds() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3), listOf(34, 35, 109, 110, 202, 203).map { DrawingCodec.shadeOf(it) })
        assertEquals(100L, DrawingCodec.frameMs(10)); assertEquals(125L, DrawingCodec.frameMs(8)); assertEquals(333L, DrawingCodec.frameMs(3))
    }

    @Test
    fun encodeDecodeRoundTripIsExact() {
        val d = sample()
        val anim = DrawingCodec.encode(d, "import:x")
        assertEquals(listOf(100L, 300L), anim.durations)
        assertEquals(25, anim.sourceSize); assertEquals(2, anim.frames13.size)
        assertEquals(255, anim.frames25[0][12, 4]); assertEquals(70, anim.frames25[0][12, 12])
        val back = DrawingCodec.decode(anim, "Wink", fps = 10, size = 25)
        assertEquals(d, back.drawing); assertFalse(back.truncated); assertFalse(back.simplified)
    }

    @Test
    fun roundTripsThroughMuseumJson() {
        val d = sample()
        val parsed = MuseumFormat.parse(MuseumFormat.toJson(DrawingCodec.encode(d, "import:x")), "import:x", "Wink") as MuseumFormat.Result.Ok
        assertEquals(d, DrawingCodec.decode(parsed.animation, "Wink", fps = 10, size = 25).drawing)
    }

    @Test
    fun truncatesAndDerivesTiming() {
        val frames = (0 until 30).map { i -> PixelGrid(25).also { it.put(12, 12, if (i % 2 == 0) 160 else 255) } }
        val durations = (0 until 30).map { if (it == 1) 300L else 100L }
        val anim = ImportedAnimation("import:y", "Big", 25, frames, frames.map { MuseumFormat.resample(it, 13) }, durations)
        val r = DrawingCodec.decode(anim, "Big", fps = 0, size = 25)
        assertEquals(24, r.drawing.frames.size); assertTrue(r.truncated); assertTrue(r.simplified)   // 160 isn't a shade value
        assertEquals(10, r.drawing.fps)
        assertEquals(listOf(1, 3, 1), r.drawing.frames.take(3).map { it.hold })
        assertEquals(2, r.drawing.frames[0][25, 12, 12])                                              // 160 → MED
        val odd = ImportedAnimation("import:z", "Odd", 25, frames.take(2), frames.take(2).map { MuseumFormat.resample(it, 13) }, listOf(20L, 5000L))
        val o = DrawingCodec.decode(odd, "Odd", fps = 0, size = 25)
        assertEquals(20, o.drawing.fps); assertEquals(listOf(1, 4), o.drawing.frames.map { it.hold })
    }

    @Test
    fun decodesAtTheOtherSize() {
        val anim = DrawingCodec.encode(sample(), "import:x")
        val small = DrawingCodec.decode(anim, "Wink", fps = 10, size = 13).drawing
        assertEquals(13, small.size); assertEquals(169, small.frames[0].shades.size)
    }
}
