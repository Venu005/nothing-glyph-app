package app.backlit.render

import app.backlit.audio.AudioFrame
import app.backlit.pet.Base
import app.backlit.pet.GhostArt
import app.backlit.pet.Pose
import app.backlit.render.charge.MoonStyle
import app.backlit.render.faces.Faces
import app.backlit.render.viz.MirrorBars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Glyph Toys picker images are generated from the toys' real frames, Nothing-style (dot-matrix disc).
 * This test fails if a checked-in image is out of date; regenerate with:
 *   UPDATE_PREVIEWS=1 ./gradlew --no-daemon :app:testDebugUnitTest --tests 'app.backlit.render.ToyPreviewsTest'
 */
class ToyPreviewsTest {

    private val previews: Map<String, PixelGrid> = mapOf(
        "ic_toy_preview" to Faces.byId("analog").render(FaceContext(10, 10, 30, 25, Mode.ACTIVE, FaceOptions(secondHand = false))),
        "ic_music_preview" to MirrorBars(25).apply {
            update(AudioFrame(floatArrayOf(0.85f, 0.15f, 0.7f, 0.1f, 0.6f, 0.08f, 0.45f, 0.05f), 0.4f, 0.5f), 50)
        }.render(),
        "ic_charge_preview" to MoonStyle.still(25, 62),
        "ic_canvas_preview" to heartDrawing(),
        "ic_pet_preview" to GhostArt.still(25, Pose(Base.CONTENT, null, 0, 0, 0, 0, 62), 0),
    )

    @Test
    fun vectorHasEveryLedAndADisc() {
        val xml = ToyPreviewXml.vector(PixelGrid(25).also { it.put(12, 12, 255); it.put(12, 13, 100) })
        assertTrue(xml.startsWith("<vector"))
        assertTrue(xml.contains("android:fillColor=\"#FF000000\""))
        assertEquals(PixelGrid.ledCount(25), Regex("a1\\.24,1\\.24 0 1,0 2\\.48,0").findAll(xml).count())   // one circle (two arcs) per LED
        assertEquals(3, Regex("<path").findAll(xml).count() - 1)                                   // off + 2 lit shades
    }

    @Test
    fun checkedInPreviewsAreUpToDate() {
        val update = System.getenv("UPDATE_PREVIEWS") == "1"
        for ((name, grid) in previews) {
            val file = File("src/main/res/drawable/$name.xml")
            val xml = ToyPreviewXml.vector(grid)
            if (update) file.writeText(xml) else assertEquals("$name is stale — regenerate (see class doc)", xml, file.readText())
        }
    }

    /** A small pixel-art heart, as if drawn in the Studio: bright outline, dim fill. */
    private fun heartDrawing(): PixelGrid {
        val rows = listOf("..XXX.XXX..", ".XXXXXXXXX.", "XXXXXXXXXXX", "XXXXXXXXXXX", ".XXXXXXXXX.", "..XXXXXXX..", "...XXXXX...", "....XXX....", ".....X.....")
        val g = PixelGrid(25)
        fun lit(c: Int, r: Int) = r in rows.indices && c in 0 until rows[r].length && rows[r][c] == 'X'
        for (r in rows.indices) for (c in rows[r].indices) if (lit(c, r)) {
            val edge = !(lit(c - 1, r) && lit(c + 1, r) && lit(c, r - 1) && lit(c, r + 1))
            g.put(7 + c, 8 + r, if (edge) 255 else 90)
        }
        return g
    }
}
